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


package czlab.skaro.server;

import java.util.HashMap;
import java.util.Map;
import czlab.server.*;

/**
 * @author Kenneth Leung
 *
 */
public class NulEmitter implements EventEmitter {

  private static final Map<String,?> _cfg = new HashMap<>();
  private ServerLike _server;

  public NulEmitter(ServerLike s) {
    _server=s;
  }

  public EventHolder release(Object obj) { return null; }

  public void dispatch(Event evt, Object options) {}

  public ServerLike container() { return _server; }

  public Object getConfig() { return _cfg; }

  public boolean isEnabled() { return false; }
  public boolean isActive() { return false; }

  public void hold(EventHolder obj) {}

  public void suspend() {}

  public void resume() {}

}






