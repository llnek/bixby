// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package com.zotohlab.skaro.mvc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedInput;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 */
public class HTTPRangeInput implements ChunkedInput<ByteBuf> {

  private static Logger _log= getLogger(lookup().lookupClass());
  public Logger tlog()  { return _log; }

  private boolean _unsatisfiable = false;
  private int _chunkSize = 8096;
  private ByteRange[] _ranges;
  private int _currentByteRange = 0;
  private long _clen= 0L;

  private RandomAccessFile _file;
  private String _contentType;

  public HTTPRangeInput(RandomAccessFile file, String cType, String range) {
    initRanges(range);
  }

  public static boolean accepts(String range) {
    return range != null && range.length() > 0;
  }

  public long prepareNettyResponse(HttpResponse rsp) {
    HttpHeaders.addHeader(rsp,"accept-ranges", "bytes");
    if (_unsatisfiable) {
      rsp.setStatus(HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
      HttpHeaders.setHeader(rsp, "content-range", "bytes " + "0-" + (_clen-1) + "/" + _clen);
      HttpHeaders.setHeader(rsp, "content-length", "0");
      return 0L;
    }

    rsp.setStatus(HttpResponseStatus.PARTIAL_CONTENT);
    if(_ranges.length == 1) {
      ByteRange r= _ranges[0];
      HttpHeaders.setHeader(rsp, "content-range", "bytes " + r.start() + "-" + r.end() + "/" + _clen);
    } else {
      HttpHeaders.setHeader(rsp, "content-type", "multipart/byteranges; boundary="+ "DEFAULT_SEPARATOR");
    }
    long len=0L;
    for (int n=0; n < _ranges.length; ++n) {
      len += _ranges[n].computeTotalLength();
    }
    HttpHeaders.setHeader(rsp, "content-length", Long.toString(len));
    return len;
  }

  public ByteBuf readChunk(ChannelHandlerContext ctx) throws IOException {

    byte[] buff= new byte[ _chunkSize];
    int count = 0;

    while ( count < _chunkSize && _currentByteRange < _ranges.length &&
        _ranges[_currentByteRange] != null) {
      if ( _ranges[_currentByteRange].remaining() > 0) {
          count += _ranges[_currentByteRange].fill(buff, count);
      } else {
          _currentByteRange += 1;
      }
    }
    return (count == 0) ? null : Unpooled.copiedBuffer(buff);
  }

  private boolean hasNextChunk() {
    return _currentByteRange < _ranges.length && _ranges[_currentByteRange].remaining() > 0;
  }

  public boolean isEndOfInput() { return !hasNextChunk(); }

  public void close() {
      try {
          _file.close();
      } catch (IOException e) {
          tlog().warn("",e);
      }
  }

  private void initRanges(String s /* range */) {
    try {
      _clen= _file.length();
      //val ranges = mutable.ArrayBuffer[ (Long,Long) ]()
      // strip off "bytes="
      int pos= s.indexOf("bytes=");
      String[] rvs= (pos < 0) ?  null : s.substring(pos+6).trim().split(",");
      List<Long[]> ranges= new ArrayList<Long[]>();

      if (rvs != null) for (int n=0; n < rvs.length; ++n) {
          String rs= rvs[n].trim();
          long start=0L;
          long end=0L;
          if (rs.startsWith("-")) {
            start = _clen - 1 -  Long.valueOf(rs.substring(1).trim() );
            end = _clen - 1;
          } else {
            String[] range = rs.split("-");
            start = Long.valueOf(range[0].trim() );
            end = (range.length > 1) ? Long.valueOf(range[1].trim()) : _clen - 1;
          }
          if (end > (_clen - 1)) { end = _clen - 1; }
          if (start <= end) { ranges.add( new Long[]{ start, end } ); }
      }

      List<ByteRange> bytes = new ArrayList<ByteRange>();
      List<Long[]> nrs = normalize(ranges);
      for (Long[] rr : nrs) {
        bytes.add( new ByteRange( _file, rr[0], rr[1], _contentType, nrs.size() > 1) );
      }
      _ranges = bytes.toArray( new ByteRange[0]) ;
      _unsatisfiable = (_ranges.length == 0);
    }
    catch (Throwable e) {
      _unsatisfiable = true;
      tlog().error("", e);
    }
  }

  private boolean maybeIntersect(Long[] r1, Long[] r2) {
    return  ( r1[0] >= r2[0] && r1[0] <= r2[1] ) ||
               ( r1[1] >= r2[0] && r1[0] <= r2[1] );
  }

  private Long[] mergeRanges(Long[] r1, Long[] r2) {
    return new Long[] { (r1[0] < r2[0]) ?  r1[0] : r2[0],
                        (r1[1] > r2[1]) ? r1[1] : r2[1] } ;
  }

  private List<Long[]> normalize( List<Long[]> chunks ) {

    if (chunks.size() == 0) { return new ArrayList<Long[]>(); }

    Long[][] sortedChunks = chunks.toArray(new Long[][] {} );

    Arrays.sort(sortedChunks, new Comparator< Long[]> () {
        public int compare( Long[] t1, Long[] t2) {
          return t1[0].compareTo(t2[0]);
        }
    });

    List<Long[]> rc= new ArrayList<>();
    rc.add(sortedChunks[0] );
    for (int n = 1; n < sortedChunks.length; ++n) {
        Long[] r1 = rc.get(rc.size() - 1);
        Long[] c1 = sortedChunks[n];
        if ( maybeIntersect(c1, r1)) {
            rc.set(rc.size() - 1, mergeRanges(c1, r1));
        } else {
            rc.add(c1);
        }
    }
    return rc;
  }

}

