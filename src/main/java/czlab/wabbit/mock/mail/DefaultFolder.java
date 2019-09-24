/**
 * Copyright © 2013-2019, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.wabbit.mock.mail;


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


