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


package com.zotohlab.frwk.net;

import static java.lang.invoke.MethodHandles.lookup;
import static com.zotohlab.frwk.util.CU.nsb;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;

import org.apache.commons.fileupload.FileItemHeaders;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import com.zotohlab.frwk.io.XData;


/**
 * @author kenl
 *
 */
public class ULFileItem implements FileItem , Serializable {

  private static final long serialVersionUID= 2214937997601489203L;
  public static final Logger TLOG= getLogger(lookup().lookupClass());

  private transient OutputStream _os;
  private byte[] _fieldBits;
  private String _filename = "";
  private String _field="";
  private String _ctype="";
  private XData _ds;
  private boolean _ff = false;

  /**
   * @param field
   * @param contentType
   * @param isFormField
   * @param fileName
   */
  public ULFileItem(String field, String contentType, boolean isFormField, String fileName) {
    _ctype= nsb(contentType);
    _field= field;
    _ff= isFormField;
    _filename= fileName;
  }

  /**
   *
   * @param field
   * @param contentType
   * @param fileName
   * @param file
   */
  public ULFileItem(String field, String contentType, String fileName, XData file) {
    _ctype= nsb(contentType);
    _field= field;
    _ff= false;
    _filename= fileName;
    _ds=file;
  }

  /**
   *
   * @param field
   * @param value
   */
  public ULFileItem(String field, byte[] value) {
    _field= field;
    _filename= "";
    _ctype= "";
    _ff= true;
    _os=iniz();
    try {
      _os.write(value);
    } catch (IOException e) {
      TLOG.error("",e);
    }
  }

  public void delete()  {
    IOUtils.closeQuietly(_os);
    if (_ds!=null) {
      //_ds.setDeleteFile(true);
      _ds.destroy();
    }
    _ds=null;
    _os=null;
  }

  public  FileItemHeaders getHeaders() { return null; }
  public void setHeaders(FileItemHeaders h) {
  }

  public String getContentType() { return  _ctype; }

  public byte[] get() { return null; }


  public String getFieldNameLC() { return  nsb(_field).toLowerCase(); }
  public String getFieldName() { return  nsb(_field); }

  public InputStream getInputStream() { return null; }

  public String getName() { return _filename; }

  public OutputStream getOutputStream() {
    return (_os==null) ? iniz() : _os;
  }

  public long getSize() { return 0L; }

  public XData fileData() { return _ds; }

  public String getString() { return getString("UTF-8"); }

  public String getString(String charset) {
    try {
      return (maybeGetBits() == null) ?  null : new String(_fieldBits, charset);
    } catch (UnsupportedEncodingException e) {
      TLOG.error("",e);
      return null;
    }
  }

  public boolean isInMemory() { return false; }
  public boolean isFormField() {return _ff;}

  public void setFieldName(String s) {
    _field=s;
  }

  public void setFormField(boolean b)  {
    _ff= b;
  }

  public void write(File fp) { }

  public void cleanup()  {
    if (_fieldBits == null) {  maybeGetBits(); }
    IOUtils.closeQuietly(_os);
    _os=null;
  }

  public void finalize() throws Throwable {
    IOUtils.closeQuietly(_os);
    super.finalize();
  }

  public String toString() {
    String s2, s= "field name= " + getFieldName() + "\n" +
    "formfield= " + isFormField() + "\n" +
    "filename= " + getName() + "\n"  ;

    if (_ds != null) {
      try {
        s2 ="filepath = " + _ds.filePath();
      } catch (IOException e) {
        TLOG.error("",e);
        s2="ERROR";
      }
    } else {
      s2= "field-value = " + getString();
    }

    return s+s2 + "\n";
  }

  private byte[] maybeGetBits() {
    if (_os instanceof ByteArrayOutputStream) {
      _fieldBits= ((ByteArrayOutputStream) _os).toByteArray();
    }
    return _fieldBits;
  }

  private OutputStream iniz() {
    if (_ff) {
      _os= new ByteArrayOutputStream(1024);
    } else {
      _ds= new XData();
      try {
        File fp = File.createTempFile("tmp-", ".dat");
        _ds.resetContent(fp,true);
        _os = new FileOutputStream(fp);
      }
      catch (Throwable e) {
        TLOG.error("", e);
      }
    }

    return _os;
  }

}


