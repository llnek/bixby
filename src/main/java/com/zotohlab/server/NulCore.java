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

package com.zotohlab.server;

import com.zotohlab.frwk.util.Schedulable;

/**
 * 
 * @author kenl
 *
 */
public class NulCore implements Schedulable {

  public static NulCore apply() { return new NulCore(); }
  //private String _id;
  
  private NulCore() {
    //_id= "NulScheduler#" + CoreUtils.nextSeqInt();    
  }
  
  @Override
  public void postpone(Runnable w, long delayMillis) {
    if (delayMillis > 0L) 
    try {
      Thread.sleep(delayMillis);
    } catch (Throwable e)
    {}
    run(w);
  }

  @Override
  public void dequeue(Runnable w) {
  }

  @Override
  public void run(Runnable w) {
    if (w != null) {
      w.run();
    }
  }

  @Override
  public void hold(Object pid, Runnable w) {
    throw new RuntimeException("Not Implemented");
  }

  @Override
  public void hold(Runnable w) {
    hold(0, w);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void wakeAndRun(Object pid, Runnable w) {
    run(w);
  }

  @Override
  public void wakeup(Runnable w) {
    wakeAndRun(0, w);
  }

  @Override
  public void reschedule(Runnable w) {
    run(w);
  }

}