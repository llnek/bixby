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

package com.zotohlab.wflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * @author kenl
 *
 */
public abstract class Composite extends Activity {

  private List<Activity> _innards= new ArrayList<>();

  public int size() { return _innards.size(); }

  protected Composite(String name) {
    super(name);
  }

  protected Composite() {}

  protected void onAdd(Activity a) {}

  protected void add(Activity a) {
    _innards.add(a);
    onAdd(a);
  }

  public Iterator<Activity> listChildren() {
    return _innards.listIterator();
  }

  public void realize(FlowNode fp) {
    CompositeNode p= (CompositeNode) fp;
    p.reifyInner( listChildren() );
  }

}

abstract class CompositeNode extends FlowNode {

  protected CompositeNode(FlowNode c, Activity a) {
    super(c,a);
  }

  public void reifyInner( Iterator<Activity> c) {
    _inner=new Iter(this,c);
  }

  public Iter inner() { return _inner; }

  protected Iter _inner = null;
}


