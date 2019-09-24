/**
 * Copyright © 2013-2019, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.wabbit.mock.jms;


import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Destination;
import javax.jms.Message;


/**
 * @author Kenneth Leung
 *
 */
@SuppressWarnings("unused")
public class MockMsgConsumer implements MessageConsumer {

  private MessageListener _sub;
  private Destination _dest;

  public MockMsgConsumer(Destination dest) {
    _dest= dest;
  }

  public void close() {
    _sub=null;
  }

  public MessageListener getMessageListener() {
    return _sub;
  }

  public String getMessageSelector() { return ""; }

  public Message receive() { return null; }

  public Message receive(long a) { return null; }

  public Message receiveNoWait() { return null; }

  public void setMessageListener(MessageListener ml) {
    _sub=ml;
  }

}


