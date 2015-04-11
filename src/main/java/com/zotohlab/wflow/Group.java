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

/**
 * A logical group - sequence of connected activities.
 *
 * @author kenl
 *
 */
class Group extends Composite {

  public static Group apply(Activity a) {
    return new Group(a);
  }

  public Group(String name, Activity a) {
    this(name);
    add(a);
  }

  public Group(Activity a) {
    this("",a);
  }

  public Group(String name) {
    super(name);
  }

  public Group() {
    this("");
  }

  public Activity chainMany(Activity... acts) {
    for (Activity a: acts) {
      add(a);
    }
    return this;
  }

  public Activity chain(Activity a) {
    add(a);
    return this;
  }

  public FlowNode reifyNode(FlowNode cur) {
    return new GroupNode(cur,this);
  }

}


/**
 * 
 * @author kenl
 *
 */
class GroupNode extends CompositeNode {

  public GroupNode(FlowNode c, Group a) {
    super(c,a);
  }

  public FlowNode eval(Job j) {
    FlowNode rc= null;

    if ( ! _inner.isEmpty()) {
      //tlog().debug("Group: {} element(s.)",  _inner.size() );
      FlowNode n=_inner.next();
      Activity d=n.getDef();
      if (d.hasName()) {
        tlog().debug("FlowNode##{} :eval().", d.getName());
      }
      rc = n.eval(j);
    } else {
      //tlog().debug("Group: no more elements.");
      rc= next();
      realize();
    }

    return rc;
  }

}

