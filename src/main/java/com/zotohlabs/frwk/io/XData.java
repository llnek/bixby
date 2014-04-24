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

package com.zotohlabs.frwk.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;

import  org.apache.commons.lang3.StringUtils;
import  org.apache.commons.io.FileUtils;
import  org.apache.commons.io.IOUtils;

/**
 * Wrapper structure to abstract a piece of data which can be a file
 * or a memory byte[].  If the data is byte[], it will also be
 * compressed if a certain threshold is exceeded.
 *
 * @author kenl
 *
 */
public class XData implements Serializable {

  private static final Logger _log= LoggerFactory.getLogger(XData.class);
  private static final long serialVersionUID = -8637175588593032279L;
  private String _encoding ="utf-8";
  private Object _data = null;
  private boolean _cls=true;

  public static Logger tlog() { return _log; }

  public XData(Object p) {
    resetContent(p);
  }

  public XData() {
      this(null);
  }

  public void setEncoding(String enc) { _encoding=enc; }
  public String getEncoding() { return _encoding; }

  /**
   * Control the internal file.
   *
   * @param del true to delete, false ignore.
   */
  public XData setDeleteFile(boolean del) { _cls= del; return this; }
  public boolean isDeleteFile() { return _cls; }

  public void destroy() {
    if (_data instanceof File && _cls) {
      FileUtils.deleteQuietly( (File) _data);
    }
    reset();
  }

  public boolean isDiskFile() {
    return _data instanceof File;
  }

  public XData resetContent(Object obj, boolean delIfFile) {
    destroy();
    if (obj instanceof CharArrayWriter) {
      _data = new String( ((CharArrayWriter) obj).toCharArray() );
    }
    if (obj instanceof ByteArrayOutputStream) {
      _data = ((ByteArrayOutputStream) obj).toByteArray();
    }
    if (obj instanceof File[]) {
      File[] ff= (File[]) obj;
      if (ff.length > 0) { _data = ff[0]; }
    }
    setDeleteFile(delIfFile);
    return this;
  }

  public XData resetContent(Object obj) {
    return resetContent(obj, true);
  }

  public Object content() { return _data; }

  public boolean hasContent() { return _data != null; }

  public byte[] javaBytes() throws IOException {
    byte[] bits= new byte[0];

    if (_data instanceof File) {
      bits = IOUtils.toByteArray(((File) _data).toURI().toURL());
    }
    if (_data instanceof String) {
      bits = ((String) _data).getBytes(_encoding);
    }
    if (_data instanceof byte[]) {
      bits = (byte[]) _data;
    }

    return bits;
  }

  public File fileRef() {
    return _data instanceof File ? ((File) _data) : null;
  }

  public String filePath() throws IOException {
      return _data instanceof File ? ((File) _data).getCanonicalPath() : "";
  }

  public long size() {
    long len=0L;
    if (_data instanceof File) {
      len= ((File) _data).length();
    }
    else
    if (_data instanceof String) {
      len = ((String) _data).length();
    }
    else
    if (_data instanceof byte[]) {
      len = ((byte[]) _data).length;
    }
    else {
    }
    return len;
  }

  public void finalize() {
    destroy();
  }

  public String stringify() throws IOException {
    return _data instanceof String ? ((String) _data) : new String ( javaBytes(), _encoding );
  }

  public InputStream stream() throws IOException {
    InputStream inp= null;
    if (_data instanceof File) {
      inp = new XStream( (File) _data);
    }
    else if (_data != null) {
      inp= new ByteArrayInputStream( javaBytes());
    }
    return inp;
  }

  private void reset() {
    _encoding= "utf-8";
    _cls=true;
    _data=null;
  }

}

