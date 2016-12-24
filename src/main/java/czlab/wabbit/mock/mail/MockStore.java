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

package czlab.wabbit.mock.mail;

import javax.mail.MessagingException;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;


/**
 * @author Kenneth Leung
 *
 */
public abstract class MockStore extends Store {

  /**
   */
  protected MockStore(Session s,URLName url) {
    super(s, url);
  }

  protected boolean _isSSL=false;
  protected String _name="";
  protected int _dftPort;
  protected int _portNum;
  protected String _host ="";
  protected String _user = "";
  protected String _pwd = "";

  /**
   */
  @Override
  protected synchronized boolean protocolConnect(String host, int portNum,
          String user, String pwd)  throws MessagingException {
    if ((host == null) || (pwd == null) || (user == null)) { return false; } else {
      _portNum = (portNum == -1) ? _dftPort : portNum ;
      _host = host;
      _user = user;
      _pwd = pwd;
      return true;
    }
  }

  /**
   */
  @Override
  public synchronized boolean isConnected() {
    return super.isConnected() ? true : false;
  }

  /**
   */
  @Override
  public synchronized void close() throws MessagingException {
    super.close();
  }

  /**
   */
  @Override
  public Folder getDefaultFolder() {
    checkConnected();
    return new DefaultFolder(this);
  }

  /**
   */
  protected void checkConnected()  {
    if (!super.isConnected())
    try {
      throw new MessagingException("Not connected");
    } catch (MessagingException e) {
      e.printStackTrace();
    }
  }

}


