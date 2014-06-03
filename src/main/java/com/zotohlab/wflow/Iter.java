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

package com.zotohlab.wflow;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author kenl
 *
 */
public class Iter {

  private List<Activity> _acts= new ArrayList<Activity>();
  private FlowPoint _outer;

  public Iter(FlowPoint outer) {
    _outer= outer;
  }

  public Iter(FlowPoint c, ListIterator<Activity> a) {
    this(c);
    while (a.hasNext()) {
      _acts.add(a.next());
    }
  }

  public boolean isEmpty() { return  _acts.size() == 0; }

  public FlowPoint next() {
    return (_acts.size() > 0) ? _acts.remove(0).reify(_outer) : null;
  }

  public int size() { return _acts.size(); }

}

