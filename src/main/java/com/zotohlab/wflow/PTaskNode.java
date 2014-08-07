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

import com.zotohlab.wflow.core.Job;

/**
 * @author kenl
 *
 */
public class PTaskNode extends FlowNode {

  public PTaskNode(FlowNode s, PTask a) {
    super(s,a);
  }

  private Work _work= null;

  public PTaskNode withWork(Work w) {
    _work=w;
    return this;
  }

  public FlowNode eval(Job j) {
    //tlog.debug("PTaskNode: {} about to perform work.", this.id )
    Object a= _work.perform(this, j, popClosureArg());
    FlowNode rc= nextPoint();

    if (a instanceof Nihil) {
      rc = new NihilNode(flow() );
    }
    else
    if (a instanceof Activity) {
      rc = ((Activity) a).reify(rc);
    } 
    else {
      if (rc != null) {
        rc.attachClosureArg(a);
      }
    }

    return rc;
  }


}

