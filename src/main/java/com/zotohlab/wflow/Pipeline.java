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

package com.zotohlab.wflow;

import static com.zotohlab.frwk.util.CoreUtils.dftCtor;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.invoke.MethodHandles.*;

import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.*;

import com.zotohlab.frwk.core.Startable;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.util.Schedulable;


/**
 * @author kenl
 *
 */
public class Pipeline implements Startable {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private AtomicLong _sn= new AtomicLong(0L);
  private PDelegate _delegate;
  private Job _theScope;

  private boolean _trace=true;
  private boolean _active=false;
  private long _pid;
  
  public Pipeline (Job scope, String cz, boolean traceable) {

    _pid = _sn.incrementAndGet();
    _theScope=scope;
    _trace=traceable;
    
    try {
      _delegate = (PDelegate) dftCtor(cz);
    } catch (Throwable e) {
      tlog().error("", e);
    }
    if (_delegate instanceof PDelegate) {} else {
      throw new ClassCastException("Class " + cz + " must implement PDelegate.");
    }
    if (_trace) {
      tlog().debug("{}: {} => pid : {}" , "Pipeline", cz , _pid);      
    }
    //assert(_theScope != null, "Scope is null.");    
  }
  
  public Pipeline (Job scope, String cz) {
    this(scope,cz,true);
  }

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

  protected Activity onError(Throwable e, FlowNode cur) {
    // give the delegate a chance to handle the error, returning
    // a different flow if necessary.
    Activity a= _delegate.onError(e,cur) ;
    if (a == null) {
      tlog().error("", e);
      a= new Nihil();
    }
    return a;
  }

  protected Activity onStart() {
    Activity x= _delegate.getStartActivity(this);
    return (x==null) ? new Nihil() : x;
  }

  public void start() {
    if (_trace) {
      tlog().debug("{}: {} => starting" , "Pipeline", this);      
    }
    try {
      FlowNode f1= onStart().reify( new NihilNode(this));
      _active=true;
      core().run(f1);
    } catch(Throwable e) {
      tlog().error("", e);
      stop();
    }
  }

  public void stop() {
    try {
      onEnd();
    } catch( Throwable e) {
      tlog().error("",e);
    }
    if (_trace) {
      tlog().debug("{}: {} => end" , "Pipeline", this);      
    }
  }

  public String toString() {
    return _delegate.getClass().getName() + "#(" + _pid + ")";
  }

  /*
  public void finalize() throws Throwable {
    super.finalize();
    //tlog().debug("=========================> Pipeline: " + getClass().getName() + " finz'ed");
  }
  */

}


