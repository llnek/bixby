/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */


package czlab.wabbit.mock.jms;


import javax.jms.MessageListener;
import javax.jms.TopicSubscriber;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;


/**
 * @author Kenneth Leung
 *
 */
@SuppressWarnings("unused")
public class MockTopicSubscriber implements TopicSubscriber {

  public MockTopicSubscriber(Topic t, String n) {
    _topic =t;
    _name=n;
  }

  private MessageListener _sub;
  private String _name;
  private Topic _topic;

  @Override
  public Topic getTopic() throws JMSException {
    return null;
  }

  @Override
  public boolean getNoLocal() throws JMSException {
    return false;
  }

  @Override
  public String getMessageSelector() throws JMSException {
    return null;
  }

  @Override
  public MessageListener getMessageListener() throws JMSException {
    return _sub;
  }

  @Override
  public void setMessageListener(MessageListener ml) throws JMSException {
    _sub=ml;
  }

  @Override
  public Message receive() throws JMSException {
    return null;
  }

  @Override
  public Message receive(long l) throws JMSException {
    return null;
  }

  @Override
  public Message receiveNoWait() throws JMSException {
    return null;
  }

  @Override
  public void close() throws JMSException {
    _sub=null;
  }
}



