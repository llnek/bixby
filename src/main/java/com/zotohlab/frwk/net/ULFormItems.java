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
  public List<ULFileItem> intern() { return _items; }

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


