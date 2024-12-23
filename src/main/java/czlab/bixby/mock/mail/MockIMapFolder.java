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


import java.util.Random;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;


/**
 *
 */
@SuppressWarnings("unused")
public class MockIMapFolder extends Folder {

  /**
   */
  public MockIMapFolder(String n, Store s) {
    super(s);
    _name=n;
  }

  private Random _rand= new Random(System.currentTimeMillis());
  private boolean _open = false;
  private int _count = 1;
  private String _name;

  /**
   */
  @Override
  public String getName() {
    return _name;
  }

  /**
   */
  @Override
  public String getFullName() {
    return _name;
  }

  /**
   */
  @Override
  public Folder getParent() throws MessagingException {
    return null;
  }

  /**
   */
  @Override
  public boolean exists() throws MessagingException {
    return true;
  }

  /**
   */
  @Override
  public Folder[] list(String s) throws MessagingException {
    return new Folder[0];
  }

  /**
   */
  @Override
  public char getSeparator() throws MessagingException {
    return 0;
  }

  /**
   */
  @Override
  public int getType() throws MessagingException {
    return 0;
  }

  /**
   */
  @Override
  public boolean create(int i) throws MessagingException {
    return false;
  }

  /**
   */
  @Override
  public boolean hasNewMessages() throws MessagingException {
    return false;
  }

  /**
   */
  @Override
  public Folder getFolder(String s) throws MessagingException {
    return null;
  }

  /**
   */
  @Override
  public boolean delete(boolean b) throws MessagingException {
    return false;
  }

  /**
   */
  @Override
  public boolean renameTo(Folder folder) throws MessagingException {
    return false;
  }

  /**
   */
  @Override
  public void open(int i) throws MessagingException {
    _open=true;
  }

  /**
   */
  @Override
  public void close(boolean b) throws MessagingException {
    _open=false;
  }

  /**
   */
  @Override
  public boolean isOpen() {
    return _open;
  }

  /**
   */
  @Override
  public Flags getPermanentFlags() {
    return null;
  }

  /**
   */
  @Override
  public int getMessageCount() throws MessagingException {
    _count= _rand.nextInt(10);
    if (_count < 1) _count = 1;
    return _count;
  }

  /**
   */
  @Override
  public Message getMessage(int pos) throws MessagingException {
    if (pos < 1) throw new MessagingException("wrong message num: " + pos);
    try {
      return new MockMsg(this, pos).newMimeMsg();
    } catch (Exception e) {
      e.printStackTrace();
      throw new MessagingException(e.getMessage()) ;
    }
  }

  /**
   */
  @Override
  public void appendMessages(Message[] messages) throws MessagingException {
  }

  /**
   */
  @Override
  public Message[] expunge() throws MessagingException {
    return new Message[0];
  }

}


