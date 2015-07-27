// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.server;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;

import com.zotohlab.frwk.core.Activable;
import com.zotohlab.frwk.server.Event;
import com.zotohlab.frwk.server.NonEvent;
import com.zotohlab.frwk.server.NulEmitter;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.server.ServiceHandler;
import com.zotohlab.frwk.util.CU;
import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.wflow.Activity;
import com.zotohlab.wflow.FlowError;
import com.zotohlab.wflow.FlowDot;
import com.zotohlab.wflow.Job;
import com.zotohlab.wflow.Nihil;
import com.zotohlab.wflow.PTask;
import com.zotohlab.wflow.Work;
import com.zotohlab.wflow.WorkFlow;
import com.zotohlab.wflow.WorkFlowEx;
import com.zotohlab.wflow.WHandler;


/**
 *
 * @author kenl
 *
 */
public class FlowServer implements ServerLike, ServiceHandler {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  protected NulEmitter _mock;
  private JobCreator _jctor;
  private Schedulable _sch;

  public static void main(String[] args) {
    try {
      FlowServer s= new FlowServer(NulCore.apply()).start();
      Activity a, b, c,d,e,f;
      a= PTask.apply((FlowDot cur, Job job)-> {
          System.out.println("A");
          return null;
      });
      b= PTask.apply((FlowDot cur, Job job) -> {
          System.out.println("B");
          return null;
      });
      c= a.chain(b);
      d= PTask.apply((FlowDot cur, Job job) -> {
          System.out.println("D");
          return null;
      });
      e= PTask.apply((FlowDot cur, Job job) -> {
          System.out.println("E");
          return null;
      });
      f= d.chain(e);

      s.handle(c.chain(f), Collections.EMPTY_MAP);

      //Thread.sleep(1000);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public FlowServer(final Schedulable s) {
    _mock=new NulEmitter(this);
    _jctor= new JobCreator(this);
    _sch= s;
  }

  @Override
  public Schedulable core() {
    return _sch;
  }

  public FlowServer start(Properties options) {
    if (_sch instanceof Activable) {
      ((Activable)_sch).activate(options);
    }
    return this;
  }

  public FlowServer start() {
    return start(new Properties());
  }

  public void dispose() {
    _sch.dispose();
  }

  @Override
  public Object handleError(Throwable t) {
    WorkFlowEx ex = null;
    if (t instanceof FlowError) {
      FlowError fe = (FlowError)t;
      FlowDot n=fe.getLastDot();
      Object obj = null;
      if (n != null) {
        obj= n.job().wflow();
      }
      if (obj instanceof WorkFlowEx) {
        ex= (WorkFlowEx)obj;
        t= fe.getCause();
      }
    }
    if (ex != null) {
      return ex.onError(t);
    } else {
      return null;
    }
  }

  @Override
  public Object handle(Object work, Object options) throws Exception {
    WorkFlow wf= null;
    if (work instanceof WHandler) {
      final WHandler h = (WHandler)work;
      wf=() -> {
          return PTask.apply( (FlowDot cur, Job j) -> {
            return h.run(j);
          });
      };
    }
    else
    if (work instanceof WorkFlow) {
      wf= (WorkFlow) work;
    }
    else
    if (work instanceof Work) {
      wf= () -> {
          return new PTask((Work)work);
      };
    }
    else
    if (work instanceof Activity) {
      wf = () -> {
        return (Activity) work;
      };
    }

    if (wf == null) {
      throw new FlowError("no valid workflow to handle.");
    }

    FlowDot end= Nihil.apply().reify( _jctor.newJob(wf));
    core().run( wf.startWith().reify(end));
    return null;
  }

}



class JobCreator {

  protected static final String JS_FLATLINE = "____flatline";
  protected static final String JS_LAST = "____lastresult";
  private final FlowServer _server;

  public JobCreator(FlowServer s) {
    _server=s;
  }

  public Job newJob(WorkFlow wf) {
    return newJob(wf, new NonEvent(_server._mock));
  }

  public Job newJob(WorkFlow wf, final Event evt) {
    return new Job() {
      private Map<Object,Object> _m= new HashMap<>();
      private long _id= CU.nextSeqLong();

      @Override
      public Object getv(Object key) {
        return key==null ? null : _m.get(key);
      }

      @Override
      public void setv(Object key, Object p) {
        if (key != null) {
          _m.put(key, p);
        }
      }

      @Override
      public void unsetv(Object key) {
        if (key != null) {
          _m.remove(key);
        }
      }

      @Override
      public void finz() {
        FlowServer.tlog().debug("job##{} has been served.", _id);
      }

      @Override
      public ServerLike container() {
        return _server;
      }

      @Override
      public Event event() {
        return evt;
      }

      @Override
      public Object id() {
        return _id;
      }

      @Override
      public void setLastResult(Object v) {
        _m.put(JS_LAST, v);
      }

      @Override
      public void clrLastResult() {
        _m.remove(JS_LAST);
      }

      @Override
      public Object getLastResult() {
        return _m.get(JS_LAST);
      }

      @Override
      public WorkFlow wflow() {
        return wf;
      }

    };
  }

}

