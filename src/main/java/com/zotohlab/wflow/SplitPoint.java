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

import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.wflow.core.Job;
import com.zotohlab.frwk.server.ServerLike;

/**
 * @author kenl
 *
 */
public class SplitPoint extends CompositePoint {

  public SplitPoint(FlowPoint s, Split a) {
    super(s,a);
  }

  private boolean _fallThru=false;

  public FlowPoint eval(Job j) {
    ServerLike x = flow().container();
    Schedulable core = x.core();
    Object c= getClosureArg();
    FlowPoint rc= null;

    while ( !_inner.isEmpty() ) {
      rc = _inner.next();
      rc.attachClosureArg(c);
      core.run(rc);
    }

    realize();

    // should we also pass the closure to the next step ? not for now
    return _fallThru ? nextPoint() : null;
  }

  public SplitPoint withBranches(Iter w) {
    _inner=w;
    return this;
  }

  public SplitPoint fallThrough() {
    _fallThru=true;
    return this;
  }

}

