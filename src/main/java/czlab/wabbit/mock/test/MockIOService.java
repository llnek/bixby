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

package czlab.wabbit.mock.test;

import czlab.wabbit.server.Container;
import czlab.wabbit.io.IoEvent;
import czlab.wabbit.io.IoService;
import czlab.wabbit.io.IoTrigger;
import czlab.xlib.Muble;

/**
 * @author Kenneth Leung
 */
public class MockIOService implements IoService {

  private Container _c;

  /**
   */
  public MockIOService() {
    _c = new MockContainer();
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public Muble getx() {
    return null;
  }

  @Override
  public Object id() {
    return null;
  }

  @Override
  public String version() {
    return null;
  }

  @Override
  public void init(Object arg0) {
  }

  @Override
  public Object restart(Object a) {
    return this;
  }

  @Override
  public Object start(Object a) {
    return this;
  }

  @Override
  public void stop() {
  }

  @Override
  public Object parent() {
    return null;
  }

  @Override
  public void setParent(Object arg0) {
  }

  @Override
  public void dispatchEx(IoEvent evt, Object arg) {
  }

  @Override
  public void hold(IoTrigger t, long millis) {
  }

  @Override
  public void dispatch(IoEvent evt) {
  }

  @Override
  public Container server() {
    return _c;
  }

  @Override
  public Object config() {
    return null;
  }

}
