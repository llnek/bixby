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



