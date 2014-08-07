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

package demo.file;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import java.io.File;
import org.apache.commons.io.FileUtils;

import com.zotohlab.frwk.server.Service;

import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;

/**
 * @author kenl
 * Create a new file every n secs
 *
 */
public class DemoGen implements PipelineDelegate {

  private static AtomicInteger _count= new AtomicInteger(0);
  public int count() { return  _count.incrementAndGet(); }

  public Activity getStartActivity(final Pipeline pipe) {
    return new PTask(new Work() {
      public Object perform(FlowNode cur, Job job, Object arg) {
        String s= "Current time is " + new Date();
        Service p = pipe.container().getService("default-sample");
        try {
          FileUtils.writeStringToFile( new File(p.getv("target").toString(), "ts-"+ count() +".txt"),
                                       s, "utf-8");
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      }
    });
  }

  public void onStop(Pipeline p) {}

  public Activity onError(Throwable e, FlowNode c) { return null; }

}

