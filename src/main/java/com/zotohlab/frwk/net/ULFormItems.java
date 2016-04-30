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


package com.zotohlab.frwk.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


import com.zotohlab.frwk.io.XData;

/**
 * @author kenl
 */
public class ULFormItems {

  private static final Logger _log= getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private List<ULFileItem> _items= new ArrayList<>();

  public ULFormItems() {
  }

  public ListIterator<ULFileItem> getAll() { return _items.listIterator(); }
  public Iterable<ULFileItem> intern() { return _items; }

  public Map<String,ULFileItem> asMap() {
    Map<String,ULFileItem> m = new HashMap<>();
    for (ULFileItem n : _items) {
      m.put(n.getFieldName(), n);
    }
    return m;
  }

  public void add(ULFileItem x) {
    tlog().debug("Adding a new ul-file-item {}", x.getFieldName());
    _items.add(x);
  }

  public int size() {
    return _items.size();
  }

  public void reset() { _items.clear(); }

  public void destroy() {
    for (ULFileItem fi : _items) {
      XData xs = fi.fileData();
      if (xs != null) { xs.destroy(); }
    }
    _items.clear();
  }

  public String toString() {
    StringBuilder b= new StringBuilder(1024);
    for (ULFileItem n : _items) {
      if (b.length() > 0) { b.append("\n"); }
      b.append("name=" + n.getFieldName());
      b.append(" ,data=" + (n.isFormField() ? n.toString() : n.getName()));
    }
    return b.toString();
  }

}


