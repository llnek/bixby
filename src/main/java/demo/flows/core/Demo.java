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
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package demo.flows.core;

import static java.lang.System.out;

import static com.zotohlab.wflow.PTask.*;

import com.zotohlab.wflow.*;

import static demo.flows.core.Auth.*;


/**
 * What this example demostrates is a webservice which takes in some user info, authenticate the
 * user, then exec some EC2 operations such as granting permission to access an AMI, and
 * permission to access/snapshot a given volume.  When all is done, a reply will be sent back
 * to the user.
 *
 * This flow showcases the use of conditional activities such a Switch() &amp; If().  Shows how to loop using
 * While(), and how to use Split &amp; Join.
 *
 * @author kenl
 *
 */
public class Demo implements PipelineDelegate {

  public Activity onError(Throwable err, FlowNode p) { return null; }

  public void onStop(Pipeline p) {
    out.println("Finally, workflow is done.!");
  }

  // step1. choose a method to authenticate the user
  // here, we'll use a switch() to pick which method
  private Activity AuthUser = Switch.apply( (j) -> {
    // hard code to use facebook in this example, but you
    // could check some data from the job, such as URI/Query params
    // and decide on which mth-value to switch() on.
    out.println("Step(1): Choose an authentication method.");
    return "facebook";
  }).withChoice("facebook", getAuthMtd("facebook")).
     withChoice("google+", getAuthMtd("google+")).
     withChoice("openid", getAuthMtd("openid")).
     withDft( getAuthMtd("db"));

  // step2.
  private Activity GetProfile = PTaskWrapper( (p,j,c) -> {
    out.println("Step(2): Get user profile\n-> user is superuser.\n");
    return null;
  });

  // step3. we are going to dummy up a retry of 2 times to simulate network/operation
  // issues encountered with EC2 while trying to grant permission.
  // so here , we are using a while() to do that.
  private Activity prov_ami = While.apply( (j) -> {
    Object v= j.getv("ami_count");
    int n, c=0;
    if (v instanceof Integer) {
      // we are going to dummy up so it will retry 2 times
      c= (Integer)v + 1;
    }
    j.setv("ami_count", c);
    return c < 3;
  },
  PTaskWrapper((p,j,a) -> {
    Object obj = j.getv("ami_count");
    if (obj instanceof Integer) {
      int n= (Integer)obj;
      if (n==2) {
        out.println("Step(3): Granted permission for user to launch this ami(id).\n");
      } else {
        out.println("Step(3): Failed to contact ami- server, will retry again... ("+n+") ");
      }
    }
    return null;
  }));


  // step3'. we are going to dummy up a retry of 2 times to simulate network/operation
  // issues encountered with EC2 while trying to grant volume permission.
  // so here , we are using a while() to do that.
  private Activity prov_vol = While.apply( (j) -> {
    Object jv = j.getv( "vol_count");
    int n, c=0;
    if (jv instanceof Integer) {
      // we are going to dummy up so it will retry 2 times
      c= (Integer)jv + 1;
    }
    j.setv("vol_count", c);
    return c < 3;
  },
  PTaskWrapper((p,j,a) -> {
    Object jv= j.getv("vol_count");
    if (jv instanceof Integer) {
      int n= (Integer)jv;
      if (n==2) {
        out.println("Step(3'): Granted permission for user to access/snapshot this volume(id).\n");
      } else {
        out.println("Step(3'): Failed to contact vol- server, will retry again... ("+n+") ");
      }
    }
    return null;
  }));

  // step4. pretend to write stuff to db. again, we are going to dummy up the case
  // where the db write fails a couple of times.
  // so again , we are using a while() to do that.
  private Activity save_sdb = While.apply( (j) -> {
    Object jv = j.getv("wdb_count");
    int n, c=0;
    if (jv instanceof Integer) {
      // we are going to dummy up so it will retry 2 times
      c = (Integer) jv + 1;
    }
    j.setv("wdb_count", c);
    return c < 3;
  },
  PTaskWrapper((p,j,a) -> {
    Object jv = j.getv("wdb_count");
    if (jv instanceof Integer) {
      int n= (Integer)jv;
      if (n==2) {
        out.println("Step(4): Wrote stuff to database successfully.\n");
      } else {
        out.println("Step(4): Failed to contact db- server, will retry again... ("+n+") ");
      }
    }
    return null;
  }));

  // this is the step where it will do the provisioning of the AMI and the EBS volume
  // in parallel.  To do that, we use a split-we want to fork off both tasks in parallel.  Since
  // we don't want to continue until both provisioning tasks are done. we use a AndJoin to hold/freeze
  // the workflow.
  private Activity Provision = Split.applyAnd(save_sdb).includeMany(prov_ami,prov_vol);

  // this is the final step, after all the work are done, reply back to the caller.
  // like, returning a 200-OK.
  private Activity ReplyUser = PTaskWrapper( (p,j,a) -> {
    out.println("Step(5): We'd probably return a 200 OK back to caller here.\n");
    return null;
  });

  private Activity ErrorUser = PTaskWrapper( (p,j,a) -> {
    out.println("Step(5): We'd probably return a 200 OK but with errors.\n");
    return null;
  });

  // do a final test to see what sort of response should we send back to the user.
  private Activity FinalTest = If.apply( (j) -> {
      // we hard code that all things are well.
      return true;
    },
    ReplyUser,
    ErrorUser );

  // returning the 1st step of the workflow.
  public Activity getStartActivity(Pipeline pipe) {
    // so, the workflow is a small (4 step) workflow, with the 3rd step (Provision) being
    // a split, which forks off more steps in parallel.
    return Block.apply(AuthUser).chainMany(GetProfile, Provision, FinalTest);
  }

}

