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
public class WhilePoint extends ConditionalPoint {

  public WhilePoint(FlowPoint s, While a) {
    super(s,a);
  }

  private FlowPoint _body = null;

  public FlowPoint eval(Job j) {
    Object c= getClosureArg();
    FlowPoint f,rc = this;

    if ( ! test(j)) {
      //tlog().debug("WhilePoint: test-condition == false")
      rc= nextPoint();
      if (rc != null) { rc.attachClosureArg(c); }
      realize();
    } else {
      //tlog().debug("WhilePoint: looping - eval body")
      _body.attachClosureArg(c);
      f= _body.eval(j);
      if (f instanceof AsyncWaitPoint) {
        ((AsyncWaitPoint) f).forceNext(rc);
        rc=f;
      }
      else
      if (f instanceof DelayPoint) {
        ((DelayPoint) f).forceNext(rc);
        rc=f;
      }
      else
      if (f != null) {
        if (f == this) {} else { _body = f; }
      }
    }

    return rc;
  }

  public WhilePoint withBody(FlowPoint b) {
    _body=b;
    return this;
  }

}


