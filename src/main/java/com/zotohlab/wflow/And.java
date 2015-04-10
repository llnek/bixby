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
 * A "AND" enforces that all bound activities must return before it continues.
 *
 * @author kenl
 *
 */
public class And extends Merge {

  public And(String name, Activity body) {
    super(name, body);
  }
  
  public And(Activity body) {
    this("",body);
  }

  public FlowNode reifyNode(FlowNode cur) {
    return new AndNode(cur, this);
  }

  public void realize(FlowNode fp) {
    AndNode s = (AndNode)fp;
    FlowNode x=s.next();
    s.withBranches(_branches);
    if (_body != null) {
      s.withBody( _body.reify( x));
    }
  }

}


