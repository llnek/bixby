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


package com.zotohlab.wflow;

import com.zotohlab.frwk.core.Debuggable;
import com.zotohlab.frwk.core.Gettable;
import com.zotohlab.frwk.core.Identifiable;
import com.zotohlab.frwk.core.Settable;
import com.zotohlab.frwk.server.Event;
import com.zotohlab.frwk.server.ServerLike;

/**
 * @author kenl
 */
public interface Job extends Gettable , Settable, Identifiable, Debuggable {

  public void setLastResult( Object v) ;

  public void clrLastResult() ;

  public Object getLastResult() ;

  public ServerLike container();

  public void clear();

  public Event event() ;

  public WorkFlow wflow();
}




