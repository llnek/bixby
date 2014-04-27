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

import java.text.SimpleDateFormat;
import java.util.Date;


import com.zotohlabs.gallifrey.runtime.AppMain;
import com.zotohlabs.gallifrey.core.Container;
import com.zotohlabs.gallifrey.io.*;

import com.zotohlabs.wflow.core.Job;
import com.zotohlabs.wflow.*;

/**
 * @author kenl
 *
 */
public class Demo implements PipelineDelegate {

  private String fmtXml() { 
    return  "<?xml version = \"1.0\" encoding = \"utf-8\"?>" +
            "<hello xmlns=\"http://simple/\">" +
            "<world>" +
            "  Holy Batman!" +
            "</world>" +
            "</hello>"; 
  }

  private Work task1= new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      HTTPEvent ev= (HTTPEvent) job.event();
      HTTPResult res= ev.getResultObj();
        /*
        val text= <html>
        <h1>The current date-time is:</h1>
        <p>
          { new SimpleDateFormat("yyyy/MM/dd' 'HH:mm:ss.SSSZ").format( new JDate() ) }
        </p>
        </html>.buildString(false)
*/
        // construct a simple html page back to caller
        // by wrapping it into a stream data object
      res.setHeader("content-type", "text/xml");
      res.setContent( fmtXml() );
      res.setStatus(200);

      // associate this result with the orignal event
      // this will trigger the http response
      ev.replyResult();
      return null;
    }
  };

  public Activity getStartActivity(Pipeline p) { return new PTask(task1); }
  public void onStop(Pipeline p) {}
  public Activity onError(Throwable e , FlowPoint c) { return null; }

}

