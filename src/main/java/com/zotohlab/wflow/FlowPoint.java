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
import org.slf4j.*;
import java.util.concurrent.atomic.AtomicLong;
import com.zotohlab.wflow.core.Job;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.util.RunnableWithId;

/**
 * @author kenl
 *
 */
public abstract class FlowPoint implements  RunnableWithId {

  protected FlowPoint (Pipeline p) {
    _parent=p;
  }

  private static Logger _log = LoggerFactory.getLogger(FlowPoint.class);
  public Logger tlog() { return FlowPoint._log; }

  private AtomicLong _sn= new AtomicLong(0);
  protected Pipeline _parent;

  private long nextID() { return _sn.incrementAndGet(); }

  private FlowPoint _nextPtr= null;
  private Activity _defn= null;
  private Object _closure= null;
  private long _pid=nextID();

  /**
   * @param s
   * @param a
   */
  protected FlowPoint(FlowPoint s, Activity a) {
    this(s.flow() );
    _nextPtr=s;
    _defn=a;
  }

  public abstract FlowPoint eval(Job j);

  public Object getId() { return _pid; }

  public FlowPoint nextPoint() { return _nextPtr; }

  public Activity getDef() { return _defn; }

  public void attachClosureArg(Object c) {
    _closure=c;
  }

  public FlowPoint realize() {
    getDef().realize(this);
    clsClosure();
    postRealize();
    return this;
  }

  protected void postRealize() {}

  protected void clsClosure() { _closure=null; }

  public Object getClosureArg() { return _closure; }

  public Object popClosureArg() {
    try {
      Object c=_closure;
      return c;
    }
    finally {
      _closure=null ;
    }
  }

  public void forceNext(FlowPoint n) {
    _nextPtr=n;
  }

  public Pipeline flow() { return _parent; }

  public void rerun() {
    ServerLike x= (ServerLike) flow().container();
    x.core().reschedule(this);
  }

  public void run() {
    FlowPoint rc= null;
    Activity err= null;
    Pipeline f= flow();

    ServerLike x= (ServerLike) f.container() ;
    x.core().dequeue(this);

    try {
      f.job().clrLastResult();
      rc= eval( f.job() );
    } 
    catch (Throwable e) {
      err= f.onError(e, this);
    }

    if (err != null) { rc= err.reify( new NihilPoint(f) );  }
    if (rc==null) {
      tlog().debug("FlowPoint: rc==null => skip.");
      // indicate skip, happens with joins
    } else {
      runAfter(f,rc);
    }

  }

  private void runAfter(Pipeline f, FlowPoint rc) {
    ServerLike x = (ServerLike) f.container();
    FlowPoint np= rc.nextPoint();
    Schedulable ct= x.core();

    if (rc instanceof DelayPoint) {
      ct.postpone( np, ((DelayPoint) rc).delayMillis() );
    }
    else
    if (rc instanceof AsyncWaitPoint) {
      ct.hold( np);
    }
    else
    if (rc instanceof NihilPoint) {
      f.stop();
    }
    else {
      ct.run(rc);
    }
  }

  public void finalize() throws Throwable {
    super.finalize();
    tlog().debug("=========================> FlowPoint: " + getClass().getName() + " finz'ed");
  }

}

