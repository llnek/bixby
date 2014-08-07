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


import com.zotohlab.gallifrey.io.FileEvent;
import com.zotohlab.wflow.*;
import com.zotohlab.wflow.core.Job;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;


/**
 * @author kenl
 *
 */
public class DemoPick implements PipelineDelegate    {

  public Activity getStartActivity(Pipeline pipe) {
    return new PTask(new Work() {
      public Object perform(FlowNode cur, Job job, Object arg) {
        FileEvent ev= (FileEvent) job.event();
        File f=ev.getFile();
        System.out.println("Picked up new file: " + f);
        try {
          System.out.println("Content: " + FileUtils.readFileToString(f, "utf-8"));
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
        //FileUtils.deleteQuietly(f);
      }
    });
  }

  public void onStop(Pipeline p) {}
  public Activity onError(Throwable e, FlowNode p) { return null; }

}


