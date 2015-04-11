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

/**
 * A nothing, nada task.
 *
 * @author kenl
 *
 */
public class Nihil  extends Activity {

  public static Nihil apply() {
    return new Nihil();
  }

  public Nihil() {}

  public FlowNode reifyNode(FlowNode cur) {
    return new NihilNode(cur.pipe() );
  }

  public void realize(FlowNode p) {}

}


class NihilNode extends FlowNode {

  public FlowNode eval(Job j) { return this; }
  public FlowNode next() { return this; }

  public NihilNode(Pipeline f) {
    super(f);
  }

}


