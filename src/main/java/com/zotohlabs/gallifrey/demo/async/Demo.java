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

package demo.async;

import com.zotohlabs.gallifrey.runtime.AppMain;
import com.zotohlabs.gallifrey.core.Container;
import org.json.JSONObject;
import com.zotohlabs.wflow.*;
import com.zotohlabs.wflow.core.Job;


/**
 * @author kenl
 *
 */
public class Demo extends PipelineDelegate {

  public Demo() {}

  public Activity getStartActivity(Pipeline pipe) { 
    return new PTask( new Work() {
      public Activity perform(FlowPoint cur, Job job, Object arg) {
        final AsyncResumeToken t= new AsyncResumeToken( cur );
        System.out.println("/* Calling a mock-webservice which takes a long time (10secs),");
        System.out.println("- since the call is *async*, event loop is not blocked.");
        System.out.println("- When we get a *call-back*, the normal processing will continue */");
        DemoAsyncWS.doLongAsyncCall(new AsyncCallback() {
          public void onSuccess(Object result) {
            System.out.println("CB: Got WS callback: onSuccess");
            System.out.println("CB: Tell the scheduler to re-schedule the original process");
            // use the token to tell framework to restart the idled process
            t.resume(result);
          }
          public void onError(Throwable e) {
            t.resume(e);
          }
          public void onTimeout() {
            onError( new Exception("time out"));
          }
        });

        System.out.println("\n\n");
        System.out.println("+ Just called the webservice, the process will be *idle* until");
        System.out.println("+ the websevice is done.");
        System.out.println("\n\n");

        return new AsyncWait();
      }
    }).chain( new PTask(new Work() {
      public Activity perform(FlowPoint cur, Job job, Object arg) {
        System.out.println("-> The result from WS is: " + arg);
        return null;
      }
    }));
  }

  public void onStop(Pipeline pipe) {}

  public FlowPoint onError(Throwable err, FlowPoint cp) { return null; }

}

