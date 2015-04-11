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

import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.util.Schedulable;



/**
 * @author kenl
 *
 */
public class Split extends Composite {

  public static Split fork(String name, Activity a) {
    return new Split(name).include(a);
  }

  public static Split fork(Activity a) {
    return new Split().include(a);
  }

  public static Split applyAnd(String name, Activity andBody)  {
    return new Split(name, new And(andBody));
  }

  public static Split applyAnd(Activity andBody)  {
    return new Split(new And(andBody));
  }

  public static Split applyOr(String name, Activity orBody)  {
    return new Split(name, new Or(orBody));
  }

  public static Split applyOr(Activity orBody)  {
    return new Split(new Or(orBody));
  }

  public static Split apply(String name, Merge j) {
    return new Split(name, j);
  }

  public static Split apply(Merge j) {
    return new Split(j);
  }

  public static Split apply(String name) {
    return new Split(name);
  }

  public static Split apply() {
    return new Split();
  }

  protected Merge _theMerge;

  public Split(String name, Merge j) {
    super(name);
    _theMerge = j;
  }

  public Split(Merge j) {
    this("", j);
  }

  public Split (String name) {
    this(name,null);
  }

  public Split () {
    this("",null);
  }

  public Split includeMany(Activity... acts) {
    for (Activity a : acts) {
      add(a);
    }
    return this;
  }

  public Split include(Activity a) {
    add(a);
    return this;
  }

  public FlowNode reifyNode(FlowNode cur) {
    return new SplitNode(cur, this);
  }

  public  void realize(FlowNode n) {
    SplitNode p= (SplitNode) n;
    Merge m= _theMerge;

    if ( m != null) {
      m.withBranches( size() );
    } else {
      m = new NullJoin();
    }

    FlowNode s = m.reify(p.next() );
    // note: get all *children* to come back to the join
    p.withBranches( new Iter(s, listChildren() ) );

    if (m instanceof NullJoin) {
        p.fallThrough();
    }
  }


}

/**
 * @author kenl
 *
 */
class NullJoin extends Merge {

  public FlowNode reifyNode(FlowNode cur) {
    return new NullJoinNode(cur, this);
  }

  public void realize(FlowNode cur) {}

  public NullJoin() {
    super(null);
  }

}

/**
 * @author kenl
 *
 */
class NullJoinNode extends MergeNode {

  public NullJoinNode(FlowNode c, Merge a) {
    super(c,a);
  }

  public FlowNode eval(Job j) {
    return null;
  }

}


/**
 * 
 * @author kenl
 *
 */
class SplitNode extends CompositeNode {

  public SplitNode(FlowNode c, Split a) {
    super(c,a);
  }

  private boolean _fallThru=false;

  public FlowNode eval(Job j) {
    ServerLike x = pipe().container();
    Schedulable core = x.core();
    FlowNode rc= null;

    while ( !_inner.isEmpty() ) {
      rc = _inner.next();
      core.run(rc);
    }

    realize();

    return _fallThru ? next() : null;
  }

  public SplitNode withBranches(Iter w) {
    _inner=w;
    return this;
  }

  public SplitNode fallThrough() {
    _fallThru=true;
    return this;
  }

}


