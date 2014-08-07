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
import java.util.ListIterator;


import com.zotohlab.frwk.io.XData;
import com.zotohlab.frwk.net.ULFileItem;
import com.zotohlab.frwk.net.ULFormItems;
import com.zotohlab.gallifrey.runtime.AppMain;
import com.zotohlab.gallifrey.core.Container;
import com.zotohlab.gallifrey.io.*;

import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;

/**
 * @author kenl
 *
 */
public class FormPost implements PipelineDelegate {

  private Work task1= new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      HTTPEvent ev= (HTTPEvent) job.event();
      HTTPResult res= ev.getResultObj();
      XData data = (XData) ev.data();
      Object stuff = null;

      if (data != null && data.hasContent()) {
        stuff = data.content();
      }

      //System.out.println("evdata = " + stuff);

      if (stuff instanceof ULFormItems) {
        ULFormItems itms= (ULFormItems) stuff;
        //System.out.println("itms inside = " + itms.size());
        ListIterator<ULFileItem> lst = itms.getAll();
        ULFileItem fi;
        while (lst.hasNext()) {
          fi = lst.next();
          System.out.println("Fieldname : " + fi.getFieldName());
          System.out.println("Name : " + fi.getName());
          System.out.println("Formfield : " + fi.isFormField());
          if (fi.isFormField()) {
            System.out.println("Field value: " + fi.getString("utf-8"));
          }
          else {
            XData xs = fi.fileData();
            if (xs != null) try {
              System.out.println("Field file = " + xs.filePath());
            } catch (Throwable e) {
              e.printStackTrace();
            }
          }
        }
      }

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


