/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


package czlab.wabbit.net;

import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.RandomAccessFile;

import static org.slf4j.LoggerFactory.*;
import org.slf4j.Logger;


/**
 * @author Kenneth Leung
 */
class ByteRange {

  public static final Logger TLOG= getLogger(ByteRange.class);

  private RandomAccessFile _file;
  private byte[] _header;
  private long _start;
  private long _end;
  private String _cType;
  private int _servedHeader = 0;
  private int _servedRange = 0;

  /**
   */
  public ByteRange(RandomAccessFile file,
      boolean wantHeader, long start, long end, String cType) {

    _header= new byte[0];
    _file= file;
    _start= start;
    _end= end;
    _cType= cType;

    if (wantHeader)
    try {
      _header= fmtHeader(file.length());
    } catch (IOException e) {
      TLOG.error("",e);
    }

  }

  /**
   */
  public long size() { return _end - _start + 1; }

  /**
   */
  public long start() { return _start; }

  /**
   */
  public long end() { return _end; }

  /**
   */
  public long calcTotalSize() { return size() + _header.length; }

  /**
   */
  public long remaining() { return size() - _servedRange; }

  /**
   */
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
        throw new IOException("error while reading file : " +
            "no more to read ! length=" +
            _file.length() +
            ", seek=" + ( _start + _servedRange));
      }
      _servedRange += c;
      count += c;
    }
    return count;
  }

  /**
   */
  private byte[] fmtHeader(long flen) throws UnsupportedEncodingException {

    String s= "--" + RangeInput.DEF_BD + "\r\n" +
      "content-type: " + _cType + "\r\n" +
      "content-range: bytes " +
      _start + "-" + _end +
      "/" + flen +
      "\r\n\r\n";

    return s.getBytes("utf-8");
  }

}




