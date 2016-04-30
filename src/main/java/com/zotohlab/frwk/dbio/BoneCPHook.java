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

package com.zotohlab.frwk.dbio;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

import com.jolbox.bonecp.ConnectionHandle;
import com.jolbox.bonecp.hooks.AbstractConnectionHook;

/**
 * To debug BoneCP if necessary.
 *
 * @author kenl
 */
public class BoneCPHook extends AbstractConnectionHook {

  private static final Logger _log= getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  public void onCheckOut(ConnectionHandle h) {
    tlog().debug("BoneCP: checking out a connection =======================> {}", h);
  }

  public void onCheckIn(ConnectionHandle h) {
    tlog().debug("BoneCP: checking in a connection   =======================> {}", h);
  }

}
