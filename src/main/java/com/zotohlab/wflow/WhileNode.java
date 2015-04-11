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
public class WhileNode extends ConditionalNode {

  public WhileNode(FlowNode c, While a) {
    super(c,a);
  }

  public FlowNode eval(Job j) {
    FlowNode n, rc = this;

    if ( ! test(j)) {
      //tlog().debug("WhileNode: test-condition == false")
      rc= next();
      realize();
    } else {
      //tlog().debug("WhileNode: looping - eval body")
      //normally n is null, but if it is not
      //switch the body to it.
      n= _body.eval(j);
      if (n != null) {

        if (n instanceof DelayNode) {
          ((DelayNode) n).setNext(rc);
          rc=n;
        }
        else
        if (n != this){
          tlog().error("WhileNode##{}.body should not return anything.",
              getDef().getName());
          // let's not do this now
          //_body = n;
        }
      }
    }

    return rc;
  }

  public WhileNode withBody(FlowNode b) {
    _body=b;
    return this;
  }

  private FlowNode _body = null;
}


