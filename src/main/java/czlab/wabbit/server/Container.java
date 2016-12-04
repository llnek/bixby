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

package czlab.wabbit.server;

import czlab.flux.server.ServerLike;
import czlab.xlib.Hierarchial;
import czlab.xlib.Disposable;
import czlab.xlib.Nameable;
import czlab.xlib.Startable;
import czlab.horde.JDBCPool;
import czlab.horde.DBAPI;
import java.io.File;

/**
 * @author Kenneth Leung
 */
public interface Container extends Component, ServerLike, ServiceProvider, Nameable, Disposable, Startable, Hierarchial {

  /** load freemarker template */
  public Object loadTemplate(String tpl, Object ctx);

  /**/
  public boolean isEnabled();

  /**/
  public Cljshim cljrt();

  /**/
  public Object envConfig();

  /**/
  public Object appConfig();

  /**/
  public byte[] appKeyBits();

  /**/
  public String appKey();

  /**/
  public File appDir();

  /**/
  public JDBCPool acquireDbPool(Object groupid);

  /**/
  public DBAPI acquireDbAPI(Object groupid);

  /**/
  public JDBCPool acquireDbPool();

  /**/
  public DBAPI acquireDbAPI();

}

