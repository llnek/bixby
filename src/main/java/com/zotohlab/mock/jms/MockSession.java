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


package com.zotohlab.mock.jms;


import java.io.Serializable;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;


/**
 * @author kenl
 *
 */
@SuppressWarnings("unused")
public class MockSession implements Session {

  private boolean _tx;
  private int _ack;

  public MockSession(boolean b, int a) {
    _ack=a;
    _tx=b;
  }

  @Override
  public BytesMessage createBytesMessage() throws JMSException {
    return null;
  }

  @Override
  public MapMessage createMapMessage() throws JMSException {
    return null;
  }

  @Override
  public Message createMessage() throws JMSException {
    return null;
  }

  @Override
  public ObjectMessage createObjectMessage() throws JMSException {
    return null;
  }

  @Override
  public ObjectMessage createObjectMessage(Serializable serializable) throws JMSException {
    return null;
  }

  @Override
  public StreamMessage createStreamMessage() throws JMSException {
    return null;
  }

  @Override
  public TextMessage createTextMessage() throws JMSException {
    return null;
  }

  @Override
  public TextMessage createTextMessage(String s) throws JMSException {
    return null;
  }

  @Override
  public boolean getTransacted() throws JMSException {
    return false;
  }

  @Override
  public int getAcknowledgeMode() throws JMSException {
    return 0;
  }

  @Override
  public void commit() throws JMSException {

  }

  @Override
  public void rollback() throws JMSException {

  }

  @Override
  public void close() throws JMSException {

  }

  @Override
  public void recover() throws JMSException {

  }

  @Override
  public MessageListener getMessageListener() throws JMSException {
    return null;
  }

  @Override
  public void setMessageListener(MessageListener messageListener) throws JMSException {

  }

  @Override
  public void run() {

  }

  @Override
  public MessageProducer createProducer(Destination destination) throws JMSException {
    return null;
  }

  @Override
  public MessageConsumer createConsumer(Destination d) throws JMSException {
    return new MockMsgConsumer(d);
  }

  @Override
  public MessageConsumer createConsumer(Destination destination, String s) throws JMSException {
    return null;
  }

  @Override
  public MessageConsumer createConsumer(Destination destination, String s, boolean b) throws JMSException {
    return null;
  }

  @Override
  public Queue createQueue(String s) throws JMSException {
    return null;
  }

  @Override
  public Topic createTopic(String s) throws JMSException {
    return null;
  }

  @Override
  public TopicSubscriber createDurableSubscriber(Topic topic, String s) throws JMSException {
    return null;
  }

  @Override
  public TopicSubscriber createDurableSubscriber(Topic topic, String s, String s2, boolean b) throws JMSException {
    return null;
  }

  @Override
  public QueueBrowser createBrowser(Queue queue) throws JMSException {
    return null;
  }

  @Override
  public QueueBrowser createBrowser(Queue queue, String s) throws JMSException {
    return null;
  }

  @Override
  public TemporaryQueue createTemporaryQueue() throws JMSException {
    return null;
  }

  @Override
  public TemporaryTopic createTemporaryTopic() throws JMSException {
    return null;
  }

  @Override
  public void unsubscribe(String s) throws JMSException {

  }
}



