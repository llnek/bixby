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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

/**
 * Wrapper structure to abstract a piece of data which can be a file
 * or a memory byte[], String or some object.
 *
 * @author kenl
 *
 */
public class XData implements Serializable {

  private static final long serialVersionUID = -8637175588593032279L;

  public static final Logger TLOG= getLogger(lookup().lookupClass());

  private String _encoding ="utf-8";
  private Object _data = null;
  private boolean _cls=true;

  //

  public XData(Object p) {
    resetContent(p);
  }

  public XData() {
    this(null);
  }

  public String getEncoding() { return _encoding; }
  public XData setEncoding(String enc) { _encoding=enc;  return this; }

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
    else
    if (obj instanceof ByteArrayOutputStream) {
      _data = ((ByteArrayOutputStream) obj).toByteArray();
    }
    else
    if (obj instanceof File[]) {
      File[] ff= (File[]) obj;
      if (ff.length > 0) { _data = ff[0]; }
    }
    else {
      _data=obj;
    }
    setDeleteFile(delIfFile);
    return this;
  }

  public XData resetContent(Object obj) {
    return resetContent(obj, true);
  }

  public boolean hasContent() { return _data != null; }
  public Object content() { return _data; }

  public byte[] javaBytes() throws IOException {
    byte[] bits;

    if (_data instanceof File) {
      bits = IOUtils.toByteArray(((File) _data).toURI().toURL());
    }
    else
    if (_data instanceof String) {
      bits = ((String) _data).getBytes(_encoding);
    }
    else
    if (_data instanceof byte[]) {
      bits = (byte[]) _data;
    }
    else {
      bits=new byte[0];
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
    if (_data instanceof byte[]) {
      len = ((byte[]) _data).length;
    }
    else {
      if (_data instanceof String) try {
        len = ((String) _data).getBytes(_encoding).length;
      } catch (Exception e) {
        TLOG.error("", e);
      }
    }
    return len;
  }

  public void finalize() {
    destroy();
  }

  public String stringify() throws IOException {
    return !hasContent()
      ? ""
      : (_data instanceof String)
        ? _data.toString()
        : new String(javaBytes(), _encoding);
  }

  public InputStream stream() throws IOException {
    InputStream inp= null;
    if (_data instanceof File) {
      inp = new XStream( (File) _data);
    }
    else if (hasContent()) {
      inp= new ByteArrayInputStream(javaBytes());
    }
    return inp;
  }

  private void reset() {
    _encoding= "utf-8";
    _cls=true;
    _data=null;
  }

}


