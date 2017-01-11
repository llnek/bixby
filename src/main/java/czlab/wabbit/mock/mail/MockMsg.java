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


import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Enumeration;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;


/**
 * @author Kenneth Leung
 *
 */
@SuppressWarnings("unused")
public class MockMsg {
  private static final String _mime=
  "From: Some One <someone@example.com>\r\n"+
  "To: Some Body <somebody@ex.com>\r\n"+
  "Subject: Hello Jack\r\n"+
  "MIME-Version: 1.0\r\n"+
  "Content-Type: multipart/mixed;boundary=\"XXXXboundary text\"\r\n"+
  "This is a multipart message in MIME format.\r\n"+
  "\r\n"+
  "--XXXXboundary text\r\n"+
  "Content-Type: text/plain\r\n"+
  "\r\n"+
  "this is the time {{TS}}\r\n"+
  "\r\n"+
  "--XXXXboundary text\r\n"+
  "Content-Type: text/plain\r\n"+
  "Content-Disposition: attachment; filename=\"test.txt\"\r\n"+
  "\r\n"+
  "this is the attachment text\r\n"+
  "\r\n"+
  "--XXXXboundary text--\r\n";


  /**
   */
  public static void main(String[] args) {
    try {
      start(args);
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   */
  private static void start(String[] args) throws Exception {
    MimeMessage m = new MimeMessage( Session.getInstance(System.getProperties()) ,
        new ByteArrayInputStream(_mime.getBytes("utf-8")));
    m.saveChanges();
    Enumeration<?> h=m.getAllHeaderLines();
    String ct=m.getContentType();
    Object cc= m.getContent();
    if (cc instanceof Multipart) {
      Multipart p= (Multipart)cc;
      int c=p.getCount();
      BodyPart pp=p.getBodyPart(0);
      c=0;
    }
    Address s= m.getFrom()[0];
    Address r= m.getRecipients(Message.RecipientType.TO)[0];
    int n=m.getMessageNumber();
    n=0;
  }

  /**
   */
  public MockMsg(Folder f, int m)  {
  }

  /**
   */
  public MimeMessage newMimeMsg() throws Exception {
    String s= new Date().toString();
    MimeMessage m= new MimeMessage(
        Session.getInstance(System.getProperties()),
        new ByteArrayInputStream(_mime.replace("{{TS}}", s).getBytes("utf-8")));
    m.saveChanges();
    return m;
  }


}



