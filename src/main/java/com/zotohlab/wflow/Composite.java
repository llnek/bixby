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
abstract class Composite extends Activity {

  private List<Activity> _innards= new ArrayList<>();

  public int size() { return _innards.size(); }

  protected Composite(String name) {
    super(name);
  }

  protected Composite() {}

  protected void add(Activity a) {
    _innards.add(a);
  }

  public Iterator<Activity> listChildren() {
    return _innards.listIterator();
  }

  public void realize(FlowNode fp) {
    CompositeNode p= (CompositeNode) fp;
    p.reifyInner( listChildren() );
  }

}


/**
 *
 * @author kenl
 *
 */
abstract class CompositeNode extends FlowNode {

  protected CompositeNode(FlowNode c, Activity a) {
    super(c,a);
  }

  public void reifyInner( Iterator<Activity> c) {
    _inner=new Innards(this,c);
  }

  public void reifyInner() {
    _inner=new Innards(this);
  }

  public Innards inner() { return _inner; }
  protected void setInner(Innards n) {
    _inner=n;
  }

  private Innards _inner = null;
}

/**
 *
 * @author kenl
 *
 */
class Innards {

  public boolean isEmpty() { return  _acts.size() == 0; }

  private List<Activity> _acts= new ArrayList<>();
  private FlowNode _outer;

  public Innards(FlowNode c, Iterator<Activity> a) {
    this(c);
    while (a.hasNext()) {
      _acts.add(a.next());
    }
  }

  public Innards(FlowNode outer) {
    _outer= outer;
  }

  public FlowNode next() {
    return _acts.size() > 0
      ? _acts.remove(0).reify(_outer)
    :
      null;
  }

  public int size() { return _acts.size(); }

}


