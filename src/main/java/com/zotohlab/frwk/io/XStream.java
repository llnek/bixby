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


package com.zotohlab.frwk.io;

import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

/**
 * Wrapper on top of a File input stream such that it can
 * delete itself from the file system when necessary.
 *
 * @author kenl
 *
 */
public class XStream extends InputStream {

  public static final Logger TLOG= getLogger(lookup().lookupClass());

  private transient InputStream _inp = null;
  protected boolean _closed = true;
  protected boolean _deleteFile;
  protected File _fn;
  private long pos = 0L;

  //

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
      TLOG.error("",e);
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



