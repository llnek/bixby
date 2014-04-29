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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author kenl
 */
public class ULFormItems {

  private List<ULFileItem> _items= new ArrayList<ULFileItem>();

  public ULFormItems() {
  }

  public ListIterator<ULFileItem> getAll() { return _items.listIterator(); }

  public void add(ULFileItem x) { _items.add(x); }

  public void reset() { _items.clear(); }

  public void destroy() {
    for (ULFileItem fi : _items) {
      XData xs = fi.fileData();
      if (xs != null) { xs.destroy(); }
    }
    _items.clear();
  }

}


