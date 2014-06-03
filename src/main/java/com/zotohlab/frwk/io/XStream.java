/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlab.frwk.io;

import java.io.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

/**
 * Wrapper on top of a File input stream such that it can
 * delete itself from the file system when necessary.
 *
 * @author kenl
 *
 */
public class XStream extends InputStream {

  private static final Logger _log= LoggerFactory.getLogger(XStream.class);
  public static Logger tlog() { return _log; }
  private transient InputStream _inp = null;
  protected boolean _deleteFile;
  protected boolean _closed = true;
  protected File _fn;
  private long pos = 0L;

  public XStream(File f, boolean delFile) {
    _deleteFile = delFile;
    _fn= f;
  }

  public XStream(File f) {
    this(f,false);
  }

  public int available() throws IOException {
    pre();
    return _inp.available();
  }

  public int read() throws IOException {
    pre();
    int r = _inp.read();
    pos += 1;
    return r;
  }

  public int read(byte[] b, int offset, int len) throws IOException {
    if (b == null) { return -1; } else {
      pre();
      int r = _inp.read(b, offset, len);
      pos = (r== -1 ) ? -1 :  pos + r;
      return r;
    }
  }

  public int read(byte[] b) throws IOException {
    return (b==null) ? -1 : read(b, 0, b.length);
  }

  public long skip(long n) throws IOException {
    if (n < 0L) { return -1L; } else {
      pre();
      long  r= _inp.skip(n);
      if (r > 0L) { pos +=  r; }
      return r;
    }
  }

  public void close() {
    IOUtils.closeQuietly(_inp);
    _inp= null;
    _closed= true;
  }

  public void mark(int readLimit) {
    if (_inp != null) {
      _inp.mark(readLimit);
    }
  }

  public void reset() {
    close();
    try {
      _inp= new FileInputStream(_fn);
    } catch (FileNotFoundException e) {
      tlog().error("",e);
    }
    _closed=false;
    pos=0L;
  }

  public boolean markSupported() { return true; }

  public XStream setDelete(boolean dfile) { _deleteFile = dfile ; return this; }

  public void delete() {
    close();
    if (_deleteFile && _fn != null) {
      FileUtils.deleteQuietly(_fn);
    }
  }

  public String filename() {
    try {
      return (_fn != null) ? _fn.getCanonicalPath() : "" ;
    } catch (IOException e) {
      tlog().error("",e);
      return "";
    }
  }

  public String toString() { return filename(); }

  public long getPosition() { return pos; }

  public void finalize() { delete(); }

  private void pre() {
    if (_closed) { ready(); }
  }

  private void ready() {
    reset();
  }

}

