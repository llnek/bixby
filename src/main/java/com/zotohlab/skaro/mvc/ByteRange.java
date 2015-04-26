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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 */
class ByteRange {

  private static Logger _log= getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private RandomAccessFile _file;
  private byte[] _header;
  private long _start;
  private long _end;
  private String _cType;
  private int _servedHeader = 0;
  private int _servedRange = 0;

  public ByteRange (RandomAccessFile file, boolean wantHeader, long start, long end,
    String cType) {

    _header= new byte[0];
    _file= file;
    _start= start;
    _end= end;
    _cType= cType;

    if (wantHeader) {
      try {
        _header= fmtHeader(file.length());
      } catch (IOException e) {
        tlog().error("",e);
      }
    }
  }

  public long start() { return _start; }
  public long end() { return _end; }

  public long size() { return _end - _start + 1; }

  public long remaining() { return size() - _servedRange; }

  public long calcTotalSize() { return size() + _header.length; }

  public int pack(byte[] out, int offset) throws IOException {
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

  private byte[] fmtHeader(long flen) throws UnsupportedEncodingException {
    String s= "--" + HTTPRangeInput.DEF_BD + "\r\n" + "content-type: " + _cType + "\r\n" +
      "content-range: bytes " + _start + "-" + _end + "/" + flen + "\r\n\r\n";
    return s.getBytes("utf-8");
  }

}




