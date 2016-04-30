/*
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


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

  public CLJShim getCljRt();

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

