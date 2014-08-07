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

import org.slf4j.*;

/**
 * An Activity is a definition of work - a task to be done.
 * At runtime, it has to be reified - make alive.  This process
 * turns an Activity into a Step in the Workflow.
 *
 * @author kenl
 *
 */
public abstract class Activity {

  private static Logger _log = LoggerFactory.getLogger(Activity.class);
  public Logger tlog() { return _log; }


  /**
   * Connect up another activity to make up a chain.
   *
   * @param a the unit of work to follow after this one.
   * @return an *ordered* list of work units.
   */
  public Activity chain( Activity a) { return new Block(this).chain(a); }

  /**
   * Instantiate a *live* version of this work unit as it becomes
   * part of the Workflow.
   *
   * @param cur current step.
   * @return a *live* version of this Activity.
   */
  public FlowNode reify(FlowNode cur) {
    return reifyNode(cur).realize();
  }

  protected abstract FlowNode reifyNode(FlowNode cur) ;

  /**
   * Configure the *live* version of this Activity.
   *
   *
   */
  protected abstract void realize(FlowNode p);

  public void finalize() throws Throwable {
    super.finalize();
    tlog().debug("=========================> Activity: " + getClass().getName() + " finz'ed");
  }

}



