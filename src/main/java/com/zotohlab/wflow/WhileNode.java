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
// Copyright (c) 2013, Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.wflow;

import com.zotohlab.wflow.core.Job;

/**
 * @author kenl
 *
 */
public class WhileNode extends ConditionalNode {

  public WhileNode(FlowNode s, While a) {
    super(s,a);
  }

  private FlowNode _body = null;

  public FlowNode eval(Job j) {
    Object c= getClosureArg();
    FlowNode f,rc = this;

    if ( ! test(j)) {
      //tlog().debug("WhileNode: test-condition == false")
      rc= nextNode();
      if (rc != null) { rc.attachClosureArg(c); }
      realize();
    } else {
      //tlog().debug("WhileNode: looping - eval body")
      _body.attachClosureArg(c);
      f= _body.eval(j);
      if (f instanceof AsyncWaitNode) {
        ((AsyncWaitNode) f).forceNext(rc);
        rc=f;
      }
      else
      if (f instanceof DelayNode) {
        ((DelayNode) f).forceNext(rc);
        rc=f;
      }
      else
      if (f != null) {
        if (f == this) {} else { _body = f; }
      }
    }

    return rc;
  }

  public WhileNode withBody(FlowNode b) {
    _body=b;
    return this;
  }

}


