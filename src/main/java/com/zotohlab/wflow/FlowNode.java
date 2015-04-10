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

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

import java.util.concurrent.atomic.AtomicLong;
import com.zotohlab.frwk.util.RunnableWithId;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.util.Schedulable;

/**
 * @author kenl
 *
 */
public abstract class FlowNode implements RunnableWithId {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private static AtomicLong _sn= new AtomicLong(0);
  private long _pid = _sn.incrementAndGet();

  private FlowNode _nextStep;
  protected Pipeline _pipe;
  private Object _closure;
  private Activity _defn;

  /**
   * @param s
   * @param a
   */
  protected FlowNode(FlowNode s, Activity a) {
    this( s.pipe() );
    _nextStep=s;
    _defn=a;
  }

  protected FlowNode(Pipeline p) {
    _pipe=p;
    _defn= new Nihil();
  }

  public FlowNode next() { return _nextStep; }
  public Activity getDef() { return _defn; }
  public Object id() { return _pid; }

  public void attachClosureArg(Object c) {
    _closure=c;
  }

  public abstract FlowNode eval(Job j);

  protected void postRealize() {}
  protected FlowNode realize() {
    getDef().realize(this);
    clsClosure();
    postRealize();
    return this;
  }

  public Object getClosureArg() { return _closure; }
  protected void clsClosure() { _closure=null; }
  public Object popClosureArg() {
    try {
      Object c=_closure;
      return c;
    } finally {
      _closure=null ;
    }
  }

  public Pipeline pipe() { return _pipe; }
  public void forceNext(FlowNode n) {
    _nextStep=n;
  }

  public void rerun() {
    ServerLike x= (ServerLike) pipe().container();
    x.core().reschedule(this);
  }

  public void run() {
    ServerLike x= (ServerLike) pipe().container() ;
    Pipeline pl = pipe();
    Activity err= null;
    FlowNode rc= null;

    x.core().dequeue(this);
    try {
      if (getDef().hasName()) {
        tlog().debug("FlowNode##{} :eval().",getDef().getName());
      }
      rc= eval( pl.job() );
    } catch (Throwable e) {
      err= pl.onError(e, this);
    }

    if (err != null) { rc= err.reify( new NihilNode(pl) );  }
    if (rc==null) {
      tlog().debug("FlowNode: rc==null => skip.");
      // indicate skip, happens with joins
    } else {
      runAfter(pl,rc);
    }
  }

  private void runAfter(Pipeline pl, FlowNode rc) {
    ServerLike x = (ServerLike) pl.container();
    FlowNode np= rc.next();
    Schedulable ct= x.core();

    if (rc instanceof DelayNode) {
      ct.postpone( np, ((DelayNode) rc).delayMillis() );
    }
    else
    if (rc instanceof AsyncWaitNode) {
      ct.hold( np);
    }
    else
    if (rc instanceof NihilNode) {
      pl.stop();
    }
    else {
      ct.run(rc);
    }
  }

  /*
  public void finalize() throws Throwable {
    super.finalize();
    //tlog().debug("=========================> FlowNode: " + getClass().getName() + " finz'ed");
  }
  */

}

