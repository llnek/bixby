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

import com.zotohlab.frwk.core.Named;


/**
 * An Activity is a definition of work - a task to be done.
 * At runtime, it has to be reified - make alive.  This process
 * turns an Activity into a Step in the Workflow.
 *
 * @author kenl
 *
 */
public abstract class Activity implements Named {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }
  private String _name;

  protected Activity(String n) {
    if (n==null) {
      //n= getClass().getSimpleName();
      n="";
    }
    _name=n;
  }  
  
  protected Activity() { 
    this("");
  }
  
  public String getName() {
    return _name;
  }
  
  /**
   * Connect up another activity to make up a chain.
   *
   * @param a the unit of work to follow after this one.
   * @return an *ordered* list of work units.
   */
  public Activity chain( Activity a) {
    return new Block(this).chain(a);
  }

  /**
   * Instantiate a *live* version of this work unit as it becomes
   * part of the Workflow.
   *
   * @param cur current step.
   * @return a *live* version of this Activity.
   */
  public FlowNode reify(FlowNode cur) {
    FlowNode n= reifyNode(cur);
    n.realize();
    return n;
  }

  public boolean hasName() {
    return _name.length() > 0;
  }
  
  protected abstract FlowNode reifyNode(FlowNode cur) ;
  protected abstract void realize(FlowNode p);

  /*
  public void finalize() throws Throwable {
    super.finalize();
    //tlog().debug("=========================> Activity: " + getClass().getName() + " finz'ed");
  }
  */

}



