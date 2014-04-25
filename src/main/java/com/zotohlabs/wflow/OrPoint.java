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
public class OrPoint  extends JoinPoint {

  public OrPoint(FlowPoint s, Or a) {
    super(s,a);
  }

  public FlowPoint eval(Job j) {
    int nv= _cntr.incrementAndGet();
    FlowPoint rc= this;
    Object c= getClosureArg();
    FlowPoint np=nextPoint();

    if (size() == 0) {
      rc= np;
      realize();
    }
    else if (nv==1) {
      rc= (_body== null) ? np : _body;
    }
    else if ( nv==size() ) {
      rc=null;
      realize();
    }

    if (rc != null) { rc.attachClosureArg(c); }
    return rc;
  }

}


