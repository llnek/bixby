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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author kenl
 *
 */
public abstract class JoinPoint extends FlowPoint {

  protected JoinPoint (FlowPoint s, Join a) {
    super(s,a);
  }

  protected AtomicInteger _cntr=new AtomicInteger(0);
  protected FlowPoint _body = null;
  private int _branches= 0;

  public JoinPoint withBody(FlowPoint body) {
    _body=body;
    return this;
  }

  public JoinPoint withBranches(int n) {
    _branches=n;
    return this;
  }

  public int size() { return  _branches; }

  public void postRealize() {
    _cntr.set(0);
  }

}


