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

package com.zotohlab.frwk.mime;

import static org.apache.commons.lang3.StringUtils.trim;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map.Entry;
import java.util.Properties;

import javax.activation.MimetypesFileTypeMap;

/**
 * @author kenl
 */
public class MimeFileTypes {

  public static  MimetypesFileTypeMap makeMimeFileTypes(Properties props) throws IOException{
    StringBuilder sum = new StringBuilder();
    for (Entry<Object, Object> en : props.entrySet()) {
      sum.append(  trim( en.getValue().toString() )  + "  " + trim(en.getKey().toString() )  + "\n");
    }
    try {
      return new MimetypesFileTypeMap( new ByteArrayInputStream( sum.toString().getBytes("utf-8")) );
    } catch (UnsupportedEncodingException e) {
      throw new IOException("Failed to parse mime.properties.");
    }
  }

}


