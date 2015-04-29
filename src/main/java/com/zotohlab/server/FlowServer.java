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

package com.zotohlab.server;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;

import com.zotohlab.frwk.server.Event;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.server.ServiceHandler;
import com.zotohlab.frwk.util.CoreUtils;
import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.wflow.Activity;
import com.zotohlab.wflow.FlowError;
import com.zotohlab.wflow.FlowNode;
import com.zotohlab.wflow.Job;
import com.zotohlab.wflow.Nihil;
import com.zotohlab.wflow.PTask;
import com.zotohlab.wflow.Work;

/**
 *
 * @author kenl
 *
 */
public class FlowServer implements ServerLike, ServiceHandler {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  private JobCreator _jctor;
  private FlowCore _sch;

  public static void main(String[] args) {
    try {
      FlowServer s= new FlowServer().start();
      Activity a, b, c,d,e,f;
      a= PTask.apply(new Work() {
        public Object exec(FlowNode cur, Job job) {
          System.out.println("A");
          return null;
        }
      });
      b= PTask.apply(new Work() {
        public Object exec(FlowNode cur, Job job) {
          System.out.println("B");
          return null;
        }
      });
      c= a.chain(b);
      d= PTask.apply(new Work() {
        public Object exec(FlowNode cur, Job job) {
          System.out.println("D");
          return null;
        }
      });
      e= PTask.apply(new Work() {
        public Object exec(FlowNode cur, Job job) {
          System.out.println("E");
          return null;
        }
      });
      f= d.chain(e);

      s.handle(c.chain(f), Collections.EMPTY_MAP);

      //Thread.sleep(1000);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public FlowServer() {
    _jctor= new JobCreator(this);
    _sch= new FlowCore();
  }

  @Override
  public Schedulable core() {
    return _sch;
  }

  public FlowServer start(Properties options) {
    _sch.activate(options);
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
    return null;
  }

  @Override
  public Object handle(Object work, Object options) throws Exception {
    Activity a=null;
    if (work instanceof WorkHandler) {
      final WorkHandler h = (WorkHandler)work;
      a=new PTask(new Work(){
        @Override
        public Object exec(FlowNode cur, Job j) {
          return h.workOn(j);
        }});
    }
    else
    if (work instanceof WorkFlow) {
      WorkFlow w = (WorkFlow) work;
      a= w.startWith();
    }
    else
    if (work instanceof Activity) {
      a= (Activity) work;
    }

    if (a == null) {
      throw new FlowError("no valid activity to handle.");
    }

    FlowNode end= Nihil.apply().reify( _jctor.newJob(null));
    core().run( a.reify(end));
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

  public Job newJob(final Event evt) {
    return new Job() {
      private Map<Object,Object> _m= new HashMap<>();
      private long _id= CoreUtils.nextSeqLong();
      {
        if (evt==null) {
          _m.put(JS_FLATLINE,true);
        }
      }
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
        Object o= _m.get(JS_FLATLINE);
        if (o != null) {
          _server.dispose();
        }
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
      public Activity handleError(Throwable e) {
        return null;
      }

    };
  }

}

