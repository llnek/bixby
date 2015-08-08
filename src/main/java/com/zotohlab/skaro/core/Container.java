// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.skaro.core;

import java.io.File;

import com.zotohlab.frwk.core.Disposable;
import com.zotohlab.frwk.core.Named;
import com.zotohlab.frwk.dbio.DBAPI;
import com.zotohlab.frwk.dbio.JDBCPool;
import com.zotohlab.frwk.server.ServerLike;
import com.zotohlab.frwk.server.ServiceProvider;

/**
 * @author kenl
 */
public interface Container extends ServerLike, ServiceProvider, Named, Disposable {

  public Object loadTemplate (String tpl, Object ctx);
  public boolean isEnabled();

  //public EventBus eventBus();

  public Object getEnvConfig();
  public Object getAppConfig();

  public byte[] getAppKeyBits();
  public String getAppKey();

  public String getName();
  public File getAppDir();

  public JDBCPool acquireDbPool(Object groupid);
  public DBAPI acquireDbAPI(Object groupid);

}

