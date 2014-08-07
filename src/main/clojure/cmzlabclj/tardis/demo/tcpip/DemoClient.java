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

import org.apache.commons.lang3.StringUtils;
import java.net.Socket;
import java.io.*;
import java.util.Date;

import static com.zotohlab.frwk.util.CoreUtils.*;

import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;

import com.zotohlab.frwk.server.Service;


/**
 * @author kenl
 *
 */
public class DemoClient implements PipelineDelegate {

  private String _textMsg= "Hello World, time is ${TS} !";

  public Activity onError(Throwable e, FlowNode f) { return null; }

  public void onStop(Pipeline p) {
  }

  public Activity getStartActivity(final Pipeline pipe) {
    // opens a socket and write something back to parent process

    return new Delay(2000).chain( new PTask( new Work() {
      public Object perform(FlowNode cur, Job job, Object arg) {
        Service tcp= (Service) pipe.container().getService("default-sample");
        String s= StringUtils.replace(_textMsg,"${TS}", new Date().toString() );
        System.out.println("TCP Client: about to send message" + s );
        Socket soc=null;
        try {
          Integer port= (Integer) tcp.getv("port");
          byte[] bits= s.getBytes("utf-8");
          String host= nsb( tcp.getv("host"));
          soc=new Socket( host, port==null? -1 : (int)port);
          OutputStream os= soc.getOutputStream();
          DataOutputStream dos=new DataOutputStream(os);
          dos.writeInt(bits.length);
          os.write(bits);
          os.flush();
        } catch (Throwable e) {
          e.printStackTrace();
        } finally {
          if(soc != null) try { soc.close(); } catch (Throwable e) {}
        }
        return null;
      }
    }) );

  }

}

