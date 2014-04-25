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
public class IfPoint extends ConditionalPoint {

  public IfPoint(FlowPoint s, If a) {
    super(s,a);
  }

  private FlowPoint _then= null;
  private FlowPoint _else= null;

  public IfPoint withElse(FlowPoint s ) {
    _else=s;
    return this;
  }

  public IfPoint withThen(FlowPoint s ) {
    _then=s;
    return this;
  }

  public FlowPoint eval(Job j) {
    Object c= getClosureArg() ;  // data pass back from previous async call?
    boolean b = test(j);
    tlog().debug("If: test {}", (b) ? "OK" : "FALSE");
    FlowPoint rc = b ? _then : _else;
    if (rc != null) {
      rc.attachClosureArg(c);
    }
    realize();
    return rc;
  }

}


