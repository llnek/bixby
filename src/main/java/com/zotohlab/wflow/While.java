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

/**
 * @author kenl
 *
 */
public class While extends Conditional {

  public static While apply(BoolExpr b, Activity body) {
    return new While(b,body);
  }

  private Activity _body;

  public While(BoolExpr expr, Activity b) {
    super(expr);
    _body=b;
  }

//  public While(Activity body) {
//    this(body, new BoolExpr () {
//      public boolean evaluate(Job j) { return false; }
//    });
//  }
//
  public FlowNode reifyPoint(FlowNode cur) { return new WhileNode(cur, this); }

  public void realize(FlowNode fp) {
    WhileNode p= (WhileNode) fp;
    if (_body != null) {
      p.withBody(_body.reify(p));
    }
    p.withTest( expr() );
  }

}


