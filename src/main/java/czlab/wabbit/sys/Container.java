/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.wabbit.server;

import czlab.flux.server.ServerLike;
import czlab.wabbit.base.Component;
import czlab.jasal.Hierarchial;
import czlab.jasal.Disposable;
import czlab.jasal.Nameable;
import czlab.jasal.LifeCycle;
import czlab.horde.JdbcPool;
import czlab.horde.DbApi;
import java.io.File;

/**
 * @author Kenneth Leung
 */
public interface Container extends Component
                                   ,ServerLike
                                   ,Nameable
                                   ,LifeCycle
                                   ,Hierarchial
                                   {

  /**/
  public boolean hasService(Object serviceId);

  /**/
  public Service service(Object serviceId);

  /** load freemarker template */
  public Object loadTemplate(String tpl, Object ctx);

  /**/
  public boolean isEnabled();

  /**/
  public Cljshim cljrt();

  /**/
  public Object podConfig();

  /**/
  public byte[] podKeyBits();

  /**/
  public String podKey();

  /**/
  public File podDir();

  /**/
  public JdbcPool acquireDbPool(Object groupid);

  /**/
  public DbApi acquireDbAPI(Object groupid);

  /**/
  public JdbcPool acquireDbPool();

  /**/
  public DbApi acquireDbAPI();

}


