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

import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.zotohlab.frwk.core.Identifiable;
import com.zotohlab.frwk.util.Schedulable;
import com.zotohlab.frwk.util.TCore;

/**
 * 
 * @author kenl
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked"})
public class FlowScheduler implements Schedulable {

  private static AtomicInteger _sn= new AtomicInteger(0);
  private Timer _timer;
  private Map _holdQ;
  private Map _runQ;
  private TCore _core;
  private String _id;
  
  public FlowScheduler() {
    _id= "FlowScheduler#" + _sn.incrementAndGet();
  }
  
  public void activate(Properties options) {
    boolean b = (boolean) options.getOrDefault("trace", true);
    int t = (int) options.getOrDefault("threads", 1);    
    _core = new TCore(_id, t,b);
    _timer= new Timer (_id, true);
    _holdQ= new ConcurrentHashMap();
    _runQ= new ConcurrentHashMap();
    _core.start();
  }
  
  public void deactivate() {
    _timer.cancel();
    _holdQ.clear();
    _runQ.clear();
    _core.stop();
  }
  
  private void addTimer(Runnable w, long delay) {
    FlowScheduler me= this;
    _timer.schedule(new TimerTask() {
      public void run() {
        me.wakeup(w);
      }      
    }, delay);
  }
  
  private Object xrefPid(Runnable w) {
    if (w instanceof Identifiable) {
      return ((Identifiable)w).id();
    } else {
      return null;
    }
  }
  
  @Override
  public void postpone(Runnable w, long delayMillis) {
    if (delayMillis < 0L) {
      hold(w);
    }
    else
    if (delayMillis == 0L) {
      run(w);
    }
    else {
      addTimer(w, delayMillis);
    }
  }

  @Override
  public void dequeue(Runnable w) {
    Object pid = xrefPid(w);
    if (pid != null) {
      _runQ.remove(pid);
    }
  }

  private void preRun(Runnable w) {
    Object pid = xrefPid(w);
    if (pid != null) {
      _holdQ.remove(pid);
      _runQ.put(pid,w);
    }
  }
  
  @Override
  public void run(Runnable w) {
    if (w != null) {
      preRun(w);
      _core.schedule(w);      
    }
  }

  @Override
  public void hold(Object pid, Runnable w) {
    if (pid != null && w != null) {
      _runQ.remove(pid,w);
      _holdQ.put(pid, w);
    }    
  }

  @Override
  public void hold(Runnable w) {
    hold(xrefPid(w), w);
  }

  @Override
  public void dispose() {
    _core.dispose();
  }

  @Override
  public void wakeAndRun(Object pid, Runnable w) {
    if (pid != null && w != null) {
      _holdQ.remove(pid);
      _runQ.put(pid, w);
      run(w);
    }
  }

  @Override
  public void wakeup(Runnable w) {
    wakeAndRun(xrefPid(w), w);
  }

  @Override
  public void reschedule(Runnable w) {
    run(w);
  }
  

}