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

import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.util.Schedulable;

/**
 * @author kenl
 *
 */
public class SplitNode extends CompositeNode {

  public SplitNode(FlowNode c, Split a) {
    super(c,a);
  }

  private boolean _fallThru=false;

  public FlowNode eval(Job j) {
    ServerLike x = pipe().container();
    Schedulable core = x.core();
    FlowNode rc= null;

    while ( !_inner.isEmpty() ) {
      rc = _inner.next();
      core.run(rc);
    }

    realize();

    return _fallThru ? next() : null;
  }

  public SplitNode withBranches(Iter w) {
    _inner=w;
    return this;
  }

  public SplitNode fallThrough() {
    _fallThru=true;
    return this;
  }

}

