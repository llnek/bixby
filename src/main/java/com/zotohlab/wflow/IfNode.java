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
 * @author kenl
 *
 */
public class IfNode extends ConditionalNode {

  public IfNode(FlowNode c, If a) {
    super(c,a);
  }

  private FlowNode _then= null;
  private FlowNode _else= null;

  public IfNode withElse(FlowNode n ) {
    _else=n;
    return this;
  }

  public IfNode withThen(FlowNode n ) {
    _then=n;
    return this;
  }

  public FlowNode eval(Job j) {
    boolean b = test(j);
    tlog().debug("If: test {}", (b) ? "OK" : "FALSE");
    FlowNode rc = b ? _then : _else;
    realize();
    return rc;
  }

}


