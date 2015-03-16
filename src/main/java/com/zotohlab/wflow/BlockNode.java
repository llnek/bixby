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
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.wflow;

import com.zotohlab.wflow.core.Job;

/**
 * @author kenl
 *
 */
public class BlockNode extends CompositeNode {

  public BlockNode(FlowNode s, Block a) {
    super(s,a);
  }

  public FlowNode eval(Job j) {
    Object c= getClosureArg();   // data pass back from previous async call?
    FlowNode rc= null;

    if ( ! _inner.isEmpty()) {
      //tlog.debug("BlockNode: {} element(s.)",  _inner.size )
      FlowNode n=_inner.next();
      n.attachClosureArg(c);
      rc = n.eval(j);
    } else {
      //tlog.debug("BlockNode: no more elements.")
      rc= nextNode();
      if (rc != null) {  rc.attachClosureArg(c); }
      realize();
    }

    return rc;
  }

}

