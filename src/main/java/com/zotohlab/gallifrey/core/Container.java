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
// Copyright (c) 2013, Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.gallifrey.core;

import com.zotohlab.frwk.core.Named;
import com.zotohlab.frwk.dbio.JDBCPool;
import com.zotohlab.gallifrey.io.IOEvent;
import com.zotohlab.frwk.core.Disposable;
import java.io.File;
import java.util.List;
import java.util.Map;

import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.dbio.DBAPI;

/**
 * @author kenl
 */
public interface Container extends ServerLike , Named, Disposable {

  public void notifyObservers(IOEvent evt, Map<?,?> options );

  public Map<String,?> getEnvConfig();
  public Map<String,?> getAppConfig();

  public byte[] getAppKeyBits();
  public String getAppKey();

  public String getName();
  public File getAppDir();

  public JDBCPool acquireDbPool(Object groupid);
  public DBAPI acquireDbAPI(Object groupid);

  public List<?> loadTemplate (String tpl, Map<?,?> ctx);
  public boolean isEnabled();

}

