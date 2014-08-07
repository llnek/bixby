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

import com.zotohlab.wflow.core.Job;


/**
 * @author kenl
 *
 */
public class Split extends Composite {

  public static Split wrap(Join j) {
    return new Split(j);
  }

  public static Split wrap() {
    return new Split();
  }

  protected Join _theJoin;

  public Split(Join j) {
    _theJoin = j;
  }

  public Split () {
    this(null);
  }

  public Split split(Activity a) {
    add(a);
    return this;
  }

  public FlowPoint reifyPoint(FlowPoint cur) { return new SplitPoint(cur, this); }

  public  void realize(FlowPoint fp) {
    SplitPoint  p= (SplitPoint) fp;

    if ( _theJoin != null) {
      _theJoin.withBranches( size() );
    } else {
      _theJoin= new NullJoin();
    }

    FlowPoint s = _theJoin.reify(p.nextPoint() );
    // note: get all *children* to come back to the join
    p.withBranches( new Iter(s, listChildren() ) );

    if ( _theJoin instanceof NullJoin) {
        p.fallThrough();
    }
  }


}

/**
 * @author kenl
 *
 */
class NullJoin extends Join {

  public NullJoin() {
    super(null);
  }

  public FlowPoint reifyPoint(FlowPoint cur) { return new NullJoinPoint(cur, this); }

  public void realize(FlowPoint cur) {}

}

/**
 * @author kenl
 *
 */
class NullJoinPoint extends JoinPoint {

  public NullJoinPoint(FlowPoint s, Join a) {
    super(s,a);
  }

  public FlowPoint eval(Job j) { return null; }

}

