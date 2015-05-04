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

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;

import com.zotohlab.frwk.server.ServiceHandler;
import com.zotohlab.frwk.util.CoreUtils;
import com.zotohlab.frwk.util.RunnableWithId;
import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.wflow.Delay;

/**
 * @author kenl
 *
 */
@SuppressWarnings("unused")
public abstract class FlowNode implements RunnableWithId {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private long _pid = CoreUtils.nextSeqLong();
  
  private FlowNode _nextStep;
  protected Job _job;
  private Activity _defn;

  /**
   * @param s
   * @param a
   */
  protected FlowNode(FlowNode c, Activity a) {
    this( c.job() );
    _nextStep=c;
    _defn=a;
  }

  protected FlowNode(Job j) {
    _job=j;
    _defn= new Nihil();
  }

  public FlowNode next() { return _nextStep; }
  public Activity getDef() { return _defn; }
  public Object id() { return _pid; }

  public abstract FlowNode eval(Job j);

  protected void postRealize() {}
  protected FlowNode realize() {
    getDef().realize(this);
    postRealize();
    return this;
  }

  protected Schedulable core() {
    return _job.container().core();
  }
  
  public Job job() { return _job; }
  public void setNext(FlowNode n) {
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
    FlowNode rc= null;
    core().dequeue(this);
    try {
      if (d.hasName()) {
        tlog().debug("FlowNode##{} :eval().", d.getName());
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
        //tlog().error("",e);
        err= Nihil.apply();
      }
      rc= err.reify( new NihilNode( _job) );  
    }

    if (rc==null) {
      tlog().debug("FlowNode: rc==null => skip.");
      // indicate skip, happens with joins
    } else {
      runAfter(rc);
    }
  }

  private void runAfter(FlowNode rc) {
    FlowNode np= rc.next();

    if (rc instanceof DelayNode) {
      core().postpone( np, ((DelayNode) rc).delayMillis() );
    }
    else
    if (rc instanceof NihilNode) {
      rc.job().finz();
      //end
    }
    else {
      core().run(rc);
    }
  }

  /*
  public void finalize() throws Throwable {
    super.finalize();
    //tlog().debug("FlowNode: " + getClass().getName() + " finz'ed");
  }
  */

}

