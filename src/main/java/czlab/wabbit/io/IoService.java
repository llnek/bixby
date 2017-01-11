/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.wabbit.io;

import czlab.wabbit.server.Container;
import czlab.wabbit.server.Service;

/**
 * @author Kenneth Leung
 */
public interface IoService extends Service {

  /**/
  public void dispatchEx(IoEvent evt, Object arg);

  /**/
  public void hold(IoTrigger t, long millis);

  /**/
  public void dispatch(IoEvent evt);

  /**/
  public Container server();

  /**/
  public Object config();

}


