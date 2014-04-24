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

package com.zotohlabs.frwk.net;

import com.zotohlabs.frwk.io.XData;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemHeaders;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import static com.zotohlabs.frwk.io.IOUtils.newTempFile;
import static com.zotohlabs.frwk.util.CoreUtils.nsb;


/**
 * @author kenl
 *
 */
public class ULFileItem implements FileItem , Serializable {

  private static Logger _log= LoggerFactory.getLogger(ULFileItem.class);
  private static final long serialVersionUID= 2214937997601489203L;
  public Logger tlog() { return ULFileItem._log; }

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

  public ULFileItem(String field, String contentType, String fileName, XData file) {
    _ctype= nsb(contentType);
    _field= field;
    _ff= false;
    _filename= fileName;
    _ds=file;
  }

  public ULFileItem(String field, byte[] value) {
    _ctype= "";
    _field= field;
    _ff= true;
    _filename= "";
    _os=iniz();
      try {
          _os.write(value);
      } catch (IOException e) {
          tlog().error("",e);
      }
  }

  public void delete()  {
    IOUtils.closeQuietly(_os);
    if (_ds!=null) {
      //_ds.setDeleteFile(true);
      _ds.destroy();
    }
    _ds=null;
  }

  public  FileItemHeaders getHeaders() { return null; }
  public void setHeaders(FileItemHeaders h) {
  }

  public String getContentType() { return  _ctype; }

  public byte[] get() { return null; }

  public String getFieldName() { return  _field; }

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
          tlog().error("",e);
          return null;
      }
  }

  public boolean isFormField() {return _ff;}

  public boolean isInMemory() { return false; }

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
            tlog().error("",e);
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
        Object[] fos= newTempFile(true);
        _ds.resetContent( fos[0]);
        _ds.setDeleteFile(true);
        _os = (OutputStream) fos[1];
      }
      catch (Throwable e) {
        tlog().error("", e);
      }
    }

    return _os;
  }

}

