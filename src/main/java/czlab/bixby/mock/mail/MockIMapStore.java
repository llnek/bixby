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
 * Copyright © 2013-2022, Kenneth Leung. All rights reserved. */

package czlab.bixby.mock.mail;

import javax.mail.MessagingException;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;


/**
 *
 */
@SuppressWarnings("unused")
public class MockIMapStore extends MockStore {

  /**
   */
  public MockIMapStore(Session s,URLName url) {
    super(s, url);
    _name="imap";
    _dftPort=143;
  }

  /**
   */
  @Override
  public Folder getFolder(String name) {
    checkConnected();
    return new MockIMapFolder(name,this);
  }

  /**
   */
  @Override
  public Folder getFolder(URLName url) {
    checkConnected();
    return new MockIMapFolder( url.getFile(), this);
  }

}


