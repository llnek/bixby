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


package demo.pop3;

import org.apache.commons.io.IOUtils;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.internet.MimeMessage;

import com.zotohlabs.gallifrey.runtime.AppMain;
import com.zotohlabs.gallifrey.core.Container;
import com.zotohlabs.gallifrey.io.EmailEvent;
import org.json.*;

import com.zotohlabs.wflow.core.Job;
import com.zotohlabs.wflow.*;

/**
 * @author kenl
 *
 */
public class Demo implements PipelineDelegate {

  private AtomicInteger _count= new AtomicInteger(0);
  public int count() { return _count.incrementAndGet(); }

  public Activity getStartActivity(Pipeline pipe) {
    return new PTask(new Work() {
      public Object perform(FlowPoint cur, Job job, Object arg) {
        EmailEvent ev= (EmailEvent) job.event();
        MimeMessage msg= ev.getMsg();
        System.out.println("######################## (" + count() + ")" );
        try {
          System.out.print(msg.getSubject() + "\r\n");
          System.out.print(msg.getFrom()[0].toString() + "\r\n");
          System.out.print(msg.getRecipients(Message.RecipientType.TO)[0].toString() + "\r\n");
          System.out.print("\r\n");
          Multipart p = (Multipart) msg.getContent();
          System.out.println(IOUtils.toString(p.getBodyPart(0).getInputStream(), "utf-8"));
        }
        catch (Throwable e) {}
        return null;
      }
    });
  }

  public void onStop(Pipeline p) {}
  public Activity onError(Throwable err, FlowPoint p) { return null; }

}

