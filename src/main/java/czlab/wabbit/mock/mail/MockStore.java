/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

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


