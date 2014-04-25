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

package com.zotohlabs.wflow;

import com.zotohlabs.wflow.core.Job;

/**
 * @author kenl
 *
 */
public class While extends Conditional {

  private Activity _body;

  public While(Activity b,BoolExpr expr) {
    super(expr);
    _body=b;
  }

  public While(Activity body) {
    this(body, new BoolExpr () {
      public boolean evaluate(Job j) { return false; }
    });
  }

  public FlowPoint reifyPoint(FlowPoint cur) { return new WhilePoint(cur, this); }

  public void realize(FlowPoint fp) {
    WhilePoint p= (WhilePoint) fp;
    if (_body != null) {
      p.withBody(_body.reify(p));
    }
    p.withTest( expr() );
  }

}


