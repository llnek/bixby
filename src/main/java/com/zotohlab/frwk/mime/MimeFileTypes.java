/*
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


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


