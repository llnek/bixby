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

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;

import com.zotohlab.frwk.server.ServiceHandler;
import com.zotohlab.frwk.util.CU;
import com.zotohlab.frwk.util.RunnableWithId;
import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.wflow.Delay;

/**
 * @author kenl
 *
 */
@SuppressWarnings("unused")
public abstract class FlowDot implements RunnableWithId {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private long _pid = CU.nextSeqLong();

  private FlowDot _nextStep;
  protected Job _job;
  private Activity _defn;

  /**
   * @param c
   * @param a
   */
  protected FlowDot(FlowDot c, Activity a) {
    this( c.job() );
    _nextStep=c;
    _defn=a;
  }

  protected FlowDot(Job j) {
    _job=j;
    _defn= new Nihil();
  }

  public FlowDot next() { return _nextStep; }
  public Activity getDef() { return _defn; }
  public Object id() { return _pid; }

  public abstract FlowDot eval(Job j);

  protected void postRealize() {}

  protected FlowDot realize() {
    getDef().realize(this);
    postRealize();
    return this;
  }

  protected Schedulable core() {
    return _job.container().core();
  }

  public Job job() { return _job; }

  public void setNext(FlowDot n) {
    _nextStep=n;
  }

  public void rerun() {
    core().reschedule(this);
  }

  public void run() {
    Object par = _job.container();
    ServiceHandler svc = null;
    Activity err= null,
             d= getDef();
    FlowDot rc= null;

    core().dequeue(this);

    try {
      if (d.hasName()) {
        tlog().debug("FlowDot##{} :eval()", d.getName());
      }
      rc= eval( _job );
    } catch (Throwable e) {
      if (par instanceof ServiceHandler) {
        svc= (ServiceHandler)par;
      }
      if (svc != null) {
        Object ret= svc.handleError(new FlowError(this,"",e));
        if (ret instanceof Activity) {
          err= (Activity)ret;
        }
      }
      if (err == null) {
        tlog().error("",e);
        err= Nihil.apply();
      }
      rc= err.reify( new NihilDot( _job) );
    }

    if (rc==null) {
      tlog().debug("FlowDot: rc==null => skip");
      // indicate skip, happens with joins
    } else {
      runAfter(rc);
    }
  }

  private void runAfter(FlowDot rc) {
    FlowDot np= rc.next();

    if (rc instanceof DelayDot) {
      core().postpone( np, ((DelayDot) rc).delayMillis() );
    }
    else
    if (rc instanceof NihilDot) {
      rc.job().finz();
      //end
    }
    else {
      core().run(rc);
    }
  }

  public void XXXfinalize() throws Throwable {
    super.finalize();
    tlog().debug("FlowDot: " + getClass().getName() + " finz'ed");
  }

}

