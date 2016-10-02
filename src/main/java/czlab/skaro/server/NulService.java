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


package czlab.skaro.server;

import czlab.skaro.io.IoEvent;
import czlab.xlib.Muble;

/**
 * @author Kenneth Leung
 */
public class NulService implements Service {

  public void dispatchEx(IoEvent evt, Object options) {}
  public void dispatch(IoEvent evt) {}
  public Muble getx() {return null;}
  public Container server() {return _s;}
  public Object config() {return null;}
  public String version() { return "0"; }
  public Object id() { return this; }
  public boolean isEnabled() {return true;}
  public boolean isActive() {return true;}
  public void setParent(Object o) {}
  public Object parent() { return null; }
  public void dispose() {}
  public void stop() {}
  public void start() {}
  public void suspend() {}
  public void resume() {}
  public void hold(EventTrigger t, long m) {}

  public NulService(Container s) { _s=s; }
  private Container _s;
}



