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

package com.zotohlabs.frwk.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default thread factory - from javasoft code.  The reason why
 * we cloned this is so that we can control how the thread-id is
 * traced out. (we want some meaninful thread name).
 *
 * @author kenl
 */
public class TFac implements ThreadFactory {

  private ClassLoader _cl = Thread.currentThread().getContextClassLoader();
  private ThreadFactory _fac = Executors.defaultThreadFactory();
  private AtomicInteger _seq= new AtomicInteger(0);

  @SuppressWarnings("unused")
  private ThreadGroup _group;

  private String _pfx="";

  public TFac(String pfx) {
    SecurityManager sm = System.getSecurityManager();
    if (sm == null) {
      _group = Thread.currentThread().getThreadGroup();
    } else {
      _group = sm.getThreadGroup();
    }
    _pfx=pfx;
  }

  public Thread newThread(Runnable r) {
    Thread t = _fac.newThread(r);
    t.setName(mkTname());
    t.setPriority(Thread.NORM_PRIORITY);
    t.setDaemon(false);
    t.setContextClassLoader(_cl);
    return t;
  }

  private String mkTname() { return _pfx + _seq.incrementAndGet(); }

}

