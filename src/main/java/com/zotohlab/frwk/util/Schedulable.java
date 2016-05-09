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

import com.zotohlab.frwk.core.Disposable;

/**
 * @author kenl
 */
public interface Schedulable extends Disposable {

  public void postpone(Runnable w, long delayMillis);

  public void dequeue(Runnable w);

  public void run(Runnable w);

  public void hold(Object pid, Runnable w);

  public void hold(Runnable w);

  public void wakeAndRun(Object pid,Runnable w);

  public void wakeup(Runnable w);

  public void reschedule(Runnable w);

}


