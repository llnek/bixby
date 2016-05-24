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


package czlab.skaro.mock.mail;


import javax.mail.Session;
import javax.mail.URLName;


/**
 * @author kenl
 *
 */
public class MockPop3SSLStore extends MockPop3Store {

  public MockPop3SSLStore(Session s,URLName url) {
    super(s, url);
  }

  public boolean _isSSL=true;
  public int _dftPort = 995;

}



