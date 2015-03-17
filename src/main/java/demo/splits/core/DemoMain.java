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
// Copyright (c) 2013, Ken Leung. All rights reserved.
 ??*/

package demo.splits.core;

import com.google.gson.JsonObject;
import com.zotohlab.gallifrey.runtime.AppMain;
import com.zotohlab.gallifrey.core.Container;

import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;

import java.util.Map;
import java.util.Objects;


/**
 * @author kenl
 */
public class DemoMain implements AppMain {

  public void contextualize(Container c) {
    Object a= c.getAppConfig();
    Object b= c.getEnvConfig();

    System.out.println("configure: env config=");
    System.out.println(Objects.toString(b));

    System.out.println("configure: app config=");
    System.out.println(Objects.toString(a));
  }

  public void initialize() {
    System.out.println("Demo fork(split)/join of tasks..." );
  }
  public void configure(Map<String,?> j) {
    System.out.println("configure: config=");
    System.out.println(Objects.toString(j));
  }

  public void start() {}
  public void stop() {
  }
  public void dispose() {
  }
}

