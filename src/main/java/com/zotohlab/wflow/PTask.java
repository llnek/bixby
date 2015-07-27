// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.wflow;

/**
 * @author kenl
 *
 */
public class PTask extends Activity {

  public static PTask PTaskWrapper(String name, Work w) {
    return new PTask(name, w);
  }

  public static PTask PTaskWrapper(Work w) {
    return new PTask("",w);
  }

  public static PTask apply(String name, Work w) {
    return new PTask(name, w);
  }

  public static PTask apply(Work w) {
    return apply("",w);
  }

  private Work _work;

  public PTask(String name, Work w) {
    super(name);
    _work=w;
  }

  public PTask(Work w) {
    this("",w);
  }

  public FlowDot reifyDot(FlowDot cur) {
    return new PTaskDot(cur, this);
  }

  public void realize(FlowDot n) {
    PTaskDot s= (PTaskDot) n;
    s.withWork(_work);
  }

  public Work work() { return _work; }

}


/**
 *
 * @author kenl
 *
 */
class PTaskDot extends FlowDot {

  public PTaskDot(FlowDot c, PTask a) {
    super(c,a);
  }

  public PTaskDot withWork(Work w) {
    _work=w;
    return this;
  }

  public FlowDot eval(Job j) {
    //tlog.debug("PTaskDot: {} about to exec work.", this.id )
    Object a= _work.on(this,j);
    FlowDot rc= next();

    if (a instanceof Nihil) {
      rc = new NihilDot( j );
    }
    else
    if (a instanceof Activity) {
      rc = ((Activity) a).reify(rc);
    }
    else {
    }

    return rc;
  }

  private Work _work= null;
}


