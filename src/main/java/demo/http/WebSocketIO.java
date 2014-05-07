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


package demo.http;

import com.zotohlabs.frwk.io.XData;
import com.zotohlabs.frwk.net.ULFileItem;
import com.zotohlabs.frwk.net.ULFormItems;
import com.zotohlabs.gallifrey.io.HTTPEvent;
import com.zotohlabs.gallifrey.io.HTTPResult;
import com.zotohlabs.gallifrey.io.WebSockEvent;
import com.zotohlabs.gallifrey.io.WebSockResult;
import com.zotohlabs.wflow.*;
import com.zotohlabs.wflow.core.Job;

import java.util.ListIterator;

/**
 * @author kenl
 *
 */
public class WebSocketIO implements PipelineDelegate {

  private Work task1= new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      WebSockEvent ev= (WebSockEvent) job.event();
      WebSockResult res= ev.getResultObj();
      XData data = (XData) ev.getData();
      Object stuff = null;

      if (data != null && data.hasContent()) {
        stuff = data.content();
      }

      //System.out.println("evdata = " + stuff);
      if (stuff instanceof String) {
        System.out.println("Got poked by websocket client: " + stuff);
      }
      else if (stuff != null) try {
        System.out.println("Got poked by websocket client: " + new String (data.javaBytes(), "utf-8"));
      }
      catch (Throwable e) {
        e.printStackTrace();
      }

      return null;
    }
  };

  public Activity getStartActivity(Pipeline p) { return new PTask(task1); }
  public void onStop(Pipeline p) {}
  public Activity onError(Throwable e , FlowPoint c) { return null; }

}


