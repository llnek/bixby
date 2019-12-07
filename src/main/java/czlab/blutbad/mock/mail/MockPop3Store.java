/**
 * Copyright © 2013-2019, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.blutbad.mock.mail;

import javax.mail.MessagingException;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;


/**
 *
 */
@SuppressWarnings("unused")
public class MockPop3Store extends MockStore {

  /**
   */
  public MockPop3Store(Session s,URLName url) {
    super(s, url);
    _name="pop3";
    _dftPort=110;
  }

  /**
   */
  @Override
  public Folder getFolder(String name) {
    checkConnected();
    return new MockPop3Folder(name,this);
  }

  /**
   */
  @Override
  public Folder getFolder(URLName url) {
    checkConnected();
    return new MockPop3Folder( url.getFile(), this);
  }

}


