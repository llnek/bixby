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

package com.zotohlabs.wflow;

import static com.zotohlabs.frwk.util.CoreUtils.*;
import org.slf4j.*;
import com.zotohlabs.frwk.util.Schedulable;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicLong;
import com.zotohlabs.wflow.core.Job;
import com.zotohlabs.frwk.core.Startable;
import com.zotohlabs.frwk.server.ServerLike;


/**
 * @author kenl
 *
 */
public class Pipeline implements Startable {

  public Pipeline (Job scope, String cz) {
    _delegateClass= nsb(cz);
    _theScope=scope;
    _pid= nextId();
    Object obj = null;
    try {
      obj = Thread.currentThread().getContextClassLoader().loadClass(_delegateClass).getDeclaredConstructor().newInstance();
    } catch (Throwable e) {
      tlog().error("", e);
    }
    if (obj instanceof PipelineDelegate) {} else {
      throw new ClassCastException("Class " + _delegateClass + " must implement PipelineDelegate.");
    }
    _delegate = (PipelineDelegate) obj;
    tlog().debug("{}: {} => pid : {}" , "Pipeline", getClass().getName() , _pid);
    //assert(_theScope != null, "Scope is null.");
  }

  private static Logger _log = LoggerFactory.getLogger(Pipeline.class);
  public Logger tlog() { return Pipeline._log; }

  private long nextId() { return _sn.incrementAndGet(); }
  private AtomicLong _sn= new AtomicLong(0L);

  private PipelineDelegate _delegate;
  private String _delegateClass;
  private Job _theScope;

  private boolean _active=false;
  private long _pid;

  public Schedulable core() {
    ServerLike x = (ServerLike) container();
    return x.core();
  }

  public ServerLike container() { return _theScope.container(); }

  public boolean isActive() { return  _active; }

  public Job job() { return _theScope; }

  public long getPID() { return  _pid; }

  protected void onEnd() {
    _delegate.onStop(this);
  }

  protected Activity onError(Throwable e, FlowPoint cur) {
    tlog().error("", e);
    Activity a= _delegate.onError(e,cur) ;
    return (a==null) ? new Nihil() : a;
  }

  protected Activity onStart() {
    Activity x= _delegate.getStartActivity(this);
    return (x==null) ? new Nihil() : x;
  }

  public void start() {
    tlog().debug("{}: {} => pid : {} => starting" , "Pipeline", _delegateClass , _pid);
    try {
      FlowPoint f1= onStart().reify( new NihilPoint(this));
      _active=true;
      core().run(f1);
    }
    catch(Throwable e) {
      tlog().error("", e);
      stop();
    }
  }

  public void stop() {
    try {
      onEnd();
    }
    catch( Throwable e) {
      tlog().error("",e);
    }
    tlog().debug("{}: {} => pid : {} => end" , "Pipeline", _delegateClass , _pid);
  }

  public String toString() {
    return _delegateClass + "(" + _pid + ")";
  }

  public void finalize() throws Throwable {
    super.finalize();
    System.out.println("=========================> Pipeline: " + getClass().getName() + " finz'ed");
  }

}


