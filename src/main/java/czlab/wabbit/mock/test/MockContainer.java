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

import java.io.File;

import czlab.horde.DBAPI;
import czlab.horde.JDBCPool;
import czlab.wabbit.server.Cljshim;
import czlab.wabbit.server.Container;
import czlab.wabbit.server.Service;
import czlab.xlib.Muble;
import czlab.xlib.Schedulable;

/**
 * @author Kenneth Leung
 */
public class MockContainer implements Container {

  private Cljshim _clj;

  public MockContainer() {
    _clj= Cljshim.newrt(Thread.currentThread().getContextClassLoader(), "m1");
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
  public Schedulable core() {
    return null;
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public void start() {
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
  public boolean hasService(Object serviceId) {
    return false;
  }

  @Override
  public Service service(Object serviceId) {
    return null;
  }

  @Override
  public Object loadTemplate(String tpl, Object ctx) {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public Cljshim cljrt() {
    return _clj;
  }

  @Override
  public Object podConfig() {
    return null;
  }

  @Override
  public byte[] podKeyBits() {
    return null;
  }

  @Override
  public String podKey() {
    return null;
  }

  @Override
  public File podDir() {
    return null;
  }

  @Override
  public JDBCPool acquireDbPool(Object groupid) {
    return null;
  }

  @Override
  public DBAPI acquireDbAPI(Object groupid) {
    return null;
  }

  @Override
  public JDBCPool acquireDbPool() {
    return null;
  }

  @Override
  public DBAPI acquireDbAPI() {
    return null;
  }

}
