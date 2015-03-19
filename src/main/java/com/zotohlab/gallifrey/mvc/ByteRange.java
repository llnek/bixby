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

package com.zotohlab.gallifrey.mvc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kenl
 */
public class ByteRange {

  private static Logger _log= LoggerFactory.getLogger(ByteRange.class);
  public Logger tlog() { return _log; }

  private RandomAccessFile _file;
  private boolean _incHeader;
  private byte[] _header;
  private long _start;
  private long _end;
  private String _cType;
  private int _servedHeader = 0;
  private int _servedRange = 0;

  public ByteRange (RandomAccessFile file, long start, long end,
      String cType, boolean incHeader) {

    _incHeader= incHeader;
    _file= file;
    _start= start;
    _end= end;
    _cType= cType;
    if ( _incHeader) {
      try {
        _header= fmtRangeHeader( start, end, file.length() , _cType, "DEFAULT_SEPARATOR");
      } catch (IOException e) {
        tlog().error("",e);
      }
    } else {
      _header= new byte[0];
    }
  }

  public long start() { return _start; }
  public long end() { return _end; }

  public long size() { return _end - _start + 1; }

  public long remaining() { return _end - _start + 1 - _servedRange; }

  public long computeTotalLength() { return size() + _header.length; }

  public int fill(byte[] out, int offset) throws IOException {
    int count = 0;
    int pos=offset;
    while ( pos < out.length && _servedHeader < _header.length ) {
        out[pos] = _header[ _servedHeader];
        pos += 1;
        _servedHeader += 1;
        count += 1;
    }
    if ( pos < out.length) {
      _file.seek( _start + _servedRange);
      long maxToRead = (remaining() > out.length - pos) ? out.length - pos : remaining();
      if (maxToRead > Integer.MAX_VALUE) {
          maxToRead = Integer.MAX_VALUE;
      }
      int c = _file.read( out, pos, (int)maxToRead);
      if (c < 0) {
        throw new IOException("error while reading file : no more to read ! length=" +
            _file.length() +
            ", seek=" + ( _start + _servedRange));
      }
      _servedRange += c;
      count += c;
    }
    return count;
  }

  private byte[] fmtRangeHeader(long start, long end, long flen, String cType, String boundary) {
    String s= "--" + boundary + "\r\n" + "content-type: " + cType + "\r\n" +
    "content-range: bytes " + start + "-" + end + "/" + flen + "\r\n" +
    "\r\n";
      try {
          return s.getBytes("utf-8");
      } catch (UnsupportedEncodingException e) {
          tlog().error("",e);
          return null;
      }
  }

}




