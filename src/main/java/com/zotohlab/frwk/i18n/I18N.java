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


package com.zotohlab.frwk.i18n;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

/**
 * @author kenl
 */
public enum I18N {
;

  private static Map<Object,ResourceBundle> _bundles= new HashMap<>();
  private static ResourceBundle _base;

  private static Logger _log= getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  public static ResourceBundle getBase() {
    return _base;
  }

  public static void setBase(ResourceBundle b) {
    _base=b;
  }

  public static ResourceBundle getBundle(Object bkey) {
    return _bundles.get(bkey);
  }

  public static void setBundle(Object bkey, ResourceBundle b) {
    tlog().info("setting a resource bundle, bkey = {}", bkey);
    _bundles.put(bkey,b);
  }

  public static void clsBundle(String bkey) {
    _bundles.remove(bkey);
  }

}

