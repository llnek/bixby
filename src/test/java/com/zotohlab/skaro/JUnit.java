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

package com.zotohlab.skaro;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zotohlab.server.FlowServer;
import com.zotohlab.server.NulCore;
import com.zotohlab.wflow.Activity;
import com.zotohlab.wflow.FlowDot;
import com.zotohlab.wflow.For;
import com.zotohlab.wflow.If;
import com.zotohlab.wflow.Job;
import com.zotohlab.wflow.PTask;
import com.zotohlab.wflow.Split;
import com.zotohlab.wflow.Switch;
import com.zotohlab.wflow.While;

public class JUnit {

  public static junit.framework.Test suite()     {
        return
        new JUnit4TestAdapter(JUnit.class);
    }

    @BeforeClass
    public static void iniz() throws Exception    {
    }

    @AfterClass
    public static void finz()    {
    }

    @Before
    public void open() throws Exception    {
    }

    @After
    public void close() throws Exception    {
    }

    @Test
    public void testDummy() throws Exception {
      assertEquals(1, 1);
    }

    @Test
    public void testWFlowSplit() throws Exception {
      FlowServer s= new FlowServer(NulCore.apply());
      s.start();
      final AtomicInteger out= new AtomicInteger(0);
      int testValue=10;
      Activity a,b,c;
      a=PTask.apply( (FlowDot n, Job j) -> {
        out.set(10);
        //System.out.println("All Right! " + System.currentTimeMillis());
        return null;
      });
      b=PTask.apply( (FlowDot n, Job j) -> {
        //System.out.println("Dude! " + System.currentTimeMillis());
        try { Thread.sleep(2000);  } catch (Exception e) {}
        return null;
      });
      c=PTask.apply( (FlowDot n, Job j) -> {
        //System.out.println("Yo! " + System.currentTimeMillis());
        try { Thread.sleep(3000);  } catch (Exception e) {}
        return null;
      });
      a=Split.applyAnd(a).includeMany(b,c);
      s.handle(a, null);
      //try { Thread.sleep(5000);  } catch (Exception e) {}
      assertEquals(testValue, out.get());

      a=PTask.apply( (FlowDot n, Job j) -> {
        out.set(10);
        //System.out.println("All Right! " + System.currentTimeMillis());
        return null;
      });
      b=PTask.apply( (FlowDot n, Job j) -> {
        //System.out.println("Dude! " + System.currentTimeMillis());
        try { Thread.sleep(2000);  } catch (Exception e) {}
        return null;
      });
      c=PTask.apply( (FlowDot n, Job j) -> {
        //System.out.println("Yo! " + System.currentTimeMillis());
        try { Thread.sleep(3000);  } catch (Exception e) {}
        return null;
      });
      a=Split.applyOr(a).includeMany(b,c);
      s.handle(a, null);
      //try { Thread.sleep(5000);  } catch (Exception e) {}
      assertEquals(testValue, out.get());


      a=PTask.apply( (FlowDot n, Job j) -> {
        out.set(10);
        //System.out.println("****All Right! " + System.currentTimeMillis());
        return null;
      });
      b=PTask.apply( (FlowDot n, Job j) -> {
        //System.out.println("Dude! " + System.currentTimeMillis());
        //try { Thread.sleep(2000);  } catch (Exception e) {}
        return null;
      });
      c=PTask.apply( (FlowDot n, Job j) -> {
       // System.out.println("Yo! " + System.currentTimeMillis());
        //try { Thread.sleep(3000);  } catch (Exception e) {}
        return null;
      });
      a=Split.apply().includeMany(b,c).chain(a);
      s.handle(a, null);
      //try { Thread.sleep(5000);  } catch (Exception e) {}
      assertEquals(testValue, out.get());
    }

    @Test
    public void testWFlowIf() throws Exception {
      FlowServer s= new FlowServer(NulCore.apply());
      s.start();
      AtomicInteger out= new AtomicInteger(0);
      int testValue=10;
      Activity a;
      Activity t= new PTask( (FlowDot n, Job j)-> {
        out.set(10);
        return null;
      });
      Activity e= new PTask( (FlowDot n, Job j)-> {
        out.set(20);
        return null;
      });
      a= If.apply( (Job j) -> {
        return true;
      }, t,e);
      s.handle(a,  null);
      assertEquals(testValue, out.get());

      testValue=20;
      t= new PTask( (FlowDot n, Job j)-> {
        out.set(10);
        return null;
      });
      e= new PTask( (FlowDot n, Job j)-> {
        out.set(20);
        return null;
      });
      a= If.apply( (Job j) -> {
        return false;
      }, t,e);
      s.handle(a,  null);
      assertEquals(testValue, out.get());

    }

    @Test
    public void testWFlowSwitch() throws Exception {
      FlowServer s= new FlowServer(NulCore.apply());
      s.start();
      AtomicInteger out= new AtomicInteger(0);
      final int testValue=10;
      Activity a=null;
      a= PTask.apply( (FlowDot cur, Job j) -> {
          out.set(10);
          return null;
      });
      Activity dummy= new PTask( (FlowDot n, Job j)-> {
        return null;
      });
      a=Switch.apply((Job j) -> {
        return "bonjour";
      }).withChoice("hello", dummy)
      .withChoice("goodbye", dummy)
      .withChoice("bonjour", a);
      s.handle(a,null);
      assertEquals(testValue, out.get());

      a=Switch.apply((Job j) -> {
        return "bonjour";
      }).withChoice("hello", dummy)
      .withChoice("goodbye", dummy)
      .withDft(a);
      s.handle(a,null);
      assertEquals(testValue, out.get());

    }

    @Test
    public void testWFlowFor() throws Exception {
      FlowServer s= new FlowServer(NulCore.apply());
      s.start();
      AtomicInteger out= new AtomicInteger(0);
      final int testValue=10;
      Activity a=null;
      a= PTask.apply( (FlowDot cur, Job j) -> {
          //System.out.println("index = " + j.getv(For.JS_INDEX));
          out.incrementAndGet();
          return null;
      });
      a=For.apply( (Job j) -> { return testValue; }, a);
      s.handle(a,null);
      assertEquals(testValue, out.get());
    }

    @Test
    public void testWFlowWhile() throws Exception {
      FlowServer s= new FlowServer(NulCore.apply());
      s.start();
      AtomicInteger out= new AtomicInteger(0);
      final int testValue=10;
      Activity a=null;
      a= PTask.apply( (FlowDot cur, Job j) -> {
          int v= (int) j.getv("count");
          j.setv("count", (v+1));
          out.getAndIncrement();
          System.out.println("count = " + v);
          return null;
      });
      a=While.apply( (Job j) -> {
        Object v= j.getv("count");
        if (v==null) {
          j.setv("count", 0);
        }
        return (int)j.getv("count")< testValue;
      }, a);
      s.handle(a,null);
      assertEquals(testValue, out.get());
    }


}

