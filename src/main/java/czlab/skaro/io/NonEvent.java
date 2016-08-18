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

package czlab.skaro.io;

import czlab.skaro.server.NulService;
import czlab.skaro.server.Service;
import czlab.skaro.server.Container;


/**
 * @author Kenneth Leung
 */
public class NonEvent implements IOEvent {

  private void init(Container s) {
    _svc=new NulService(s);
  }

  public NonEvent(Container s) {
    init(s);
  }

  public NonEvent() {
    this(null);
  }


  @Override
  public Object id() {
    return "nada";
  }

  @Override
  public Service source() {
    return _svc;
  }

  private Service _svc;

}



