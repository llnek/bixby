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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import com.zotohlab.frwk.server.Event;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.server.Service;
import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.wflow.Activity;
import com.zotohlab.wflow.FlowNode;
import com.zotohlab.wflow.Job;
import com.zotohlab.wflow.SDelegate;
import com.zotohlab.wflow.PTask;
import com.zotohlab.wflow.Pipeline;
import com.zotohlab.wflow.Work;

/**
 * 
 * @author kenl
 *
 */
public class FlowServer implements ServerLike {
  
  private static final String JS_LAST = "____lastresult";
  private final AtomicLong _sn= new AtomicLong(0L);
  private FlowScheduler _sch;

  public static void main(String[] args) {
    try {
      FlowServer s= new FlowServer().start();
      Job j= s.reifyJob(null);
      new Pipeline("hello",  j, new SDelegate() {
        public Activity startWith(Pipeline p) {
          return PTask.apply(new Work() {
            public Object exec(FlowNode cur, Job job, Object arg) {
              System.out.println("do something!");
              return null;
            }            
          });
        }
      }).start();
      Thread.sleep(10000);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
  
  public FlowServer() {
    _sch= new FlowScheduler();
  }

  @Override
  public boolean hasService(Object serviceId) {
    return false;
  }

  @Override
  public Service getService(Object serviceId) {
    return null;
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
  
  public Job reifyJob(final Event evt) {
    final FlowServer me=this;
    return new Job() {
      private Map<Object,Object> _m= new HashMap<>();
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
      public ServerLike container() {
        return me;
      }

      @Override
      public Event event() {
        return evt;
      }

      @Override
      public Object id() {
        return me._sn.getAndIncrement();
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
      
    };
  }
  
}