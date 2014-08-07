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


package demo.tcpip;

import java.io.*;
import com.zotohlab.gallifrey.runtime.AppMain;
import com.zotohlab.gallifrey.core.Container;
import com.zotohlab.gallifrey.io.*;

import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;


public class DemoServer implements PipelineDelegate {

  public Activity onError(Throwable e, FlowPoint p) { return null; }
  public void onStop(Pipeline p) {
  }

  private String _clientMsg="";

  private Work task1= new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      SocketEvent ev= (SocketEvent) job.event();
      try {
        int clen=new DataInputStream(ev.getSockIn() ).readInt();
        InputStream bf= new BufferedInputStream( ev.getSockIn() );
        byte[] buf= new byte[clen];
        bf.read(buf);
        _clientMsg=new String(buf,"utf-8");
      }
      catch (Throwable e) {}
      // add a delay into the workflow before next step
      return new Delay(1500);
    }
  };

  private Work task2= new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      System.out.println("Socket Server Received: " + _clientMsg );
      return null;
    }
  };

  public Activity getStartActivity(Pipeline pipe) {
    return new PTask(task1).chain(new PTask(task2));
  }

}

