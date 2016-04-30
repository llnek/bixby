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


package com.zotohlab.tpcl.axis;

import javax.xml.namespace.QName;

import org.apache.axis.encoding.ser.BaseDeserializerFactory;

/**
 * @author kenl
 */
public class CalDeserializerFactory extends BaseDeserializerFactory {

  private static final long serialVersionUID = 1L;


  public CalDeserializerFactory(Class<?> cz) {
    super(cz);
  }

  public CalDeserializerFactory(Class<?> java_type, QName xml_type) {
    this(CalDeserializer.class);
    javaType= java_type;
    xmlType= xml_type;
  }

}



