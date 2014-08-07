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


package demo.timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;


import com.zotohlab.gallifrey.io.TimerEvent;

import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;


public class Demo implements PipelineDelegate {

  private AtomicInteger _count= new AtomicInteger(0);
  public int count() { return _count.incrementAndGet(); }

  public Activity getStartActivity(Pipeline pipe) {
    return new PTask( new Work() {
      public Object perform(FlowNode cur, Job job, Object arg) {
        TimerEvent ev= (TimerEvent) job.event();
        if ( ev.isRepeating() ) {
          System.out.println("-----> (" + count() +  ") repeating-update: " + new Date());
        } else {
          System.out.println("-----> once-only!!: " + new Date());
        }
        return null;
      }
    });
  }

  public void onStop(Pipeline p) {}

  public Activity onError(Throwable e, FlowNode p) { return null; }

}





