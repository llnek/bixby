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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author kenl
 *
 */
public abstract class Merge  extends Activity {

  protected Merge(String name, Activity b) {
    super(name);
    _body=b;
  }

  protected Merge(Activity b) {
    this("",b);
  }

  protected Merge withBranches(int n) {
    _branches=n;
    return this;
  }

  protected int _branches=0;
  protected Activity _body;

}


/**
 * 
 * @author kenl
 *
 */
abstract class MergeNode extends FlowNode {

  protected AtomicInteger _cntr=new AtomicInteger(0);
  protected FlowNode _body = null;
  private int _branches= 0;

  protected MergeNode(FlowNode c, Merge a) {
    super(c,a);
  }

  public MergeNode withBody(FlowNode body) {
    _body=body;
    return this;
  }

  public MergeNode withBranches(int n) {
    _branches=n;
    return this;
  }

  public int size() { return  _branches; }

  public void postRealize() {
    _cntr.set(0);
  }

}

/**
 * 
 * @author kenl
 *
 */
class NullJoin extends Merge {

  public FlowNode reifyNode(FlowNode cur) {
    return new MergeNode(cur, this){
      public FlowNode eval(Job j) {
        return null;
      }      
    };
  }

  public void realize(FlowNode cur) {}

  public NullJoin() {
    super(null);
  }

}
