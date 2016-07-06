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


import javax.mail.MessagingException;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;

/**
 * @author Kenneth Leung
 *
 */
public class DefaultFolder extends Folder {

  /**
   */
  public DefaultFolder(Store s) {
    super(s);
  }

  /**
   */
  private Folder getInbox() {
    try {
      return getStore().getFolder("INBOX");
    } catch (MessagingException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   */
  @Override
  public String getName() {
    return "";
  }

  /**
   */
  @Override
  public String getFullName() {
    return "";
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
    return new Folder[] { getInbox() };
  }

  /**
   */
  @Override
  public char getSeparator() throws MessagingException {
    return '/';
  }

  /**
   */
  @Override
  public int getType() throws MessagingException {
    return 2;
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
  public Folder getFolder(String name) throws MessagingException {
    if (!name.equalsIgnoreCase("INBOX")) {
      throw new MessagingException("Only INBOX is supported");
    }
    return getInbox();
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
  }

  /**
   */
  @Override
  public void close(boolean b) throws MessagingException {
  }

  /**
   */
  @Override
  public boolean isOpen() {
    return false;
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
    return 0;
  }

  /**
   */
  @Override
  public Message getMessage(int i) throws MessagingException {
    return null;
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


