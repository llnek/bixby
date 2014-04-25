/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlabs.mock.mail;

import javax.mail.*;


/**
 * @author kenl
 *
 */
public class MockPop3Store extends Store {

  public MockPop3Store(Session s,URLName url) {
    super(s, url);
  }

  private String _name="pop3";
  protected int _dftPort = 110;
  protected int _portNum = -1;
  protected boolean _isSSL=false;
  protected String _host ="";
  protected String _user = "";
  protected String _pwd = "";

    /*
    if (url != null)
      name = url.getProtocol()
      */

  public synchronized boolean protocolConnect( String host, int portNum,
          String user, String pwd) {
    if ((host == null) || (pwd == null) || (user == null)) { return false; } else {
      _portNum = (portNum == -1) ? _dftPort : portNum ;
      _host = host;
      _user = user;
      _pwd = pwd;
      return true;
    }
  }

  public synchronized boolean isConnected() {
    return ( super.isConnected()) ? true : false;
  }

  public synchronized void close() throws MessagingException {
    super.close();
  }

  public Folder getDefaultFolder() {
    checkConnected();
    return new DefaultFolder(this);
  }

  public Folder getFolder(String name) {
    checkConnected();
    return new MockPop3Folder(name,this);
  }

  public Folder getFolder(URLName url) {
    checkConnected();
    return new MockPop3Folder( url.getFile(), this);
  }

  public void finalize() throws Throwable {
    super.finalize();
  }

  private void checkConnected()  {
    if (!super.isConnected())
      try {
        throw new MessagingException("Not connected");
      } catch (MessagingException e) {
        e.printStackTrace();
      }
  }

}

