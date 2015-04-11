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
import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import com.zotohlab.frwk.core.Identifiable;
import com.zotohlab.frwk.core.Startable;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.util.Schedulable;


/**
 * @author kenl
 *
 */
public class Pipeline implements Startable, Identifiable {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  private AtomicLong _sn= new AtomicLong(0L);
  private String _name= "";
  private PDelegate _delegate;
  private Job _job;

  private boolean _active=false;
  private boolean _trace=true;
  private long _pid;

  public Pipeline(String name, Job job, PDelegate p, boolean traceable) {
    ctor(name, job, p, traceable);
  }

  public Pipeline(String name, Job job, PDelegate p) {
    this(name, job, p, true);
  }

  public Pipeline(String name, String cz, Job job, boolean traceable) {
    PDelegate p= null;;
    try {
      p = (PDelegate) dftCtor(cz);
    } catch (Throwable e) {
      tlog().error("", e);
    }
    if (p instanceof PDelegate) {} else {
      throw new ClassCastException(cz + " must implement PDelegate.");
    }
    ctor(name, job, p, traceable);
  }

  public Pipeline(String name, String cz, Job job) {
    this(name, cz, job,true);
  }

  private void ctor(String name, Job job, PDelegate p, boolean traceable) {
    _pid = _sn.incrementAndGet();
    _delegate =p;
    _job=job;
    _trace=traceable;
    _name= name;
    if (_trace) {
      tlog().debug("Pipeline##{} => pid : {} - created OK." , _name, _pid);
    }
  }

  public Schedulable core() {
    ServerLike x = (ServerLike) container();
    return x.core();
  }

  public ServerLike container() { return _job.container(); }
  public boolean isActive() { return  _active; }
  public Job job() { return _job; }
  public Object id() { return  _pid; }

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
    Activity x= _delegate.startWith(this);
    return (x==null) ? new Nihil() : x;
  }

  public void start() {
    if (_trace) {
      tlog().debug("Pipeline##{} => starting" , _name);
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
      _active=false;
      onEnd();
    } catch( Throwable e) {
      tlog().error("",e);
    }
    if (_trace) {
      tlog().debug("Pipeline##{} => end" , _name);
    }
  }

  public String toString() {
    return _delegate.getClass().getName() + "#(" + _pid + ")";
  }

  /*
  public void finalize() throws Throwable {
    super.finalize();
    //tlog().debug("Pipeline: " + getClass().getName() + " finz'ed");
  }
  */

}


