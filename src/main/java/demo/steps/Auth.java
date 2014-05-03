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


package demo.steps;


import com.zotohlabs.wflow.core.Job;
import com.zotohlabs.wflow.*;

public enum Auth {
;

  public static Activity getAuthMtd(String t) {
      if (t.equals("facebook")) {  return new PTask(facebook_login); }
      if (t.equals("google+")) { return new PTask(gplus_login); }
      if (t.equals("openid")) {  return new PTask(openid_login); }
      return new PTask(db_login);
  }

  private static Work facebook_login = new Work() {
    public Object perform(FlowPoint p, Job job, Object arg) {
      System.out.println("-> using facebook to login.\n");
      return null;
    }
  };

  private static Work gplus_login = new Work() {
    public Object perform(FlowPoint p, Job job, Object arg) {
      System.out.println("-> using google+ to login.\n");
      return null;
    }
  };

  private static Work openid_login = new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      System.out.println("-> using open-id to login.\n");
      return null;
    }
  };

  private static Work db_login = new Work() {
    public Object perform(FlowPoint cur, Job job, Object arg) {
      System.out.println("-> using internal db to login.\n");
      return null;
    }
  };

}

