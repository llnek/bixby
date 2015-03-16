/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/



package com.zotohlab.mock.jms;

import javax.jms.*;
import java.io.Serializable;


/**
 * @author kenl
 *
 */
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
