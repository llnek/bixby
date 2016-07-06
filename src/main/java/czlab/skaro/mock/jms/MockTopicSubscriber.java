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


package czlab.skaro.mock.jms;


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
    return null;
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



