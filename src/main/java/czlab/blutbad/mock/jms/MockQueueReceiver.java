/**
 * Copyright © 2013-2019, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.blutbad.mock.jms;

import javax.jms.MessageListener;
import javax.jms.QueueReceiver;
import javax.jms.Message;
import javax.jms.Queue;


/**
 *
 */
public class MockQueueReceiver implements QueueReceiver {

  public MockQueueReceiver(Queue q) {
    _Q=q;
  }

  private MessageListener _sub;
  private Queue _Q;

  public void close() {
    _sub=null;
    _Q= null;
  }

  public MessageListener getMessageListener() { return _sub; }

  public String getMessageSelector() { return ""; }

  public Message receive() { return null; }

  public Message receive(long a) { return null; }

  public Message receiveNoWait() { return null; }

  public void setMessageListener(MessageListener ml) {
    _sub=ml;
  }

  public Queue getQueue() { return _Q; }

}


