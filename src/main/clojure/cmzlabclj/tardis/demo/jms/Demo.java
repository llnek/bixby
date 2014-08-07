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


package demo.jms;

import com.zotohlab.gallifrey.io.JMSEvent;
import com.zotohlab.wflow.*;
import com.zotohlab.wflow.core.Job;

import javax.jms.TextMessage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author kenl
 */
public class Demo implements PipelineDelegate {

  private AtomicInteger _count= new AtomicInteger(0);
  public int count() { return _count.incrementAndGet() ; }

  public Activity getStartActivity(Pipeline pipe) {
    return new PTask( new Work() {
      public Object perform(FlowNode cur, Job job, Object arg) {
        JMSEvent  ev= (JMSEvent) job.event();
        TextMessage msg= (TextMessage) ev.getMsg();
        try {
          System.out.println("-> Correlation ID= " + msg.getJMSCorrelationID());
          System.out.println("-> Msg ID= " + msg.getJMSMessageID());
          System.out.println("-> Type= " + msg.getJMSType());
          System.out.println("(" + count() + ") -> Text Message= " + msg.getText());
        } catch (Throwable e) {}
        return null;
      }
    });
  }

  public void onStop(Pipeline pipe) {}

  public Activity onError(Throwable err, FlowNode p) { return null; }

}

