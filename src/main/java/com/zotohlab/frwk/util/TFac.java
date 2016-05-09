/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */

package com.zotohlab.frwk.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The default thread factory - from javasoft code.  The reason why
 * we cloned this is so that we can control how the thread-id is
 * traced out. (we want some meaninful thread name).
 *
 * @author kenl
 */
@SuppressWarnings("unused")
@Deprecated
public class TFac implements ThreadFactory {

  private ThreadFactory _fac = Executors.defaultThreadFactory();
  private AtomicInteger _seq= new AtomicInteger(0);

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
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread t = _fac.newThread(r);
    t.setName(mkTname());
    t.setPriority(Thread.NORM_PRIORITY);
    t.setDaemon(false);
    t.setContextClassLoader(cl);
    return t;
  }

  private String mkTname() { return _pfx + "-" + _seq.incrementAndGet(); }

}


