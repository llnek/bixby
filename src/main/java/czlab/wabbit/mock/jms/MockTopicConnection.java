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


package czlab.wabbit.mock.jms;


import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;


/**
 * @author Kenneth Leung
 *
 */
@SuppressWarnings("unused")
public class MockTopicConnection implements TopicConnection {

  public MockTopicConnection(String n, String p) {
    _user=n;
    _pwd=p;
  }

  private volatile boolean _active = true;
  private String _user;
  private String _pwd;

  public boolean isActive() { return _active; }

  @Override
  public TopicSession createTopicSession(boolean b, int i) throws JMSException {
    TopicSession s= new MockTopicSession(this, b, i);
    s.run();
    return s;
  }

  @Override
  public ConnectionConsumer createConnectionConsumer(Topic topic, String s, ServerSessionPool serverSessionPool, int i) throws JMSException {
    return null;
  }

  @Override
  public Session createSession(boolean b, int i) throws JMSException {
    return null;
  }

  @Override
  public String getClientID() throws JMSException {
    return null;
  }

  @Override
  public void setClientID(String s) throws JMSException {

  }

  @Override
  public ConnectionMetaData getMetaData() throws JMSException {
    return null;
  }

  @Override
  public ExceptionListener getExceptionListener() throws JMSException {
    return null;
  }

  @Override
  public void setExceptionListener(ExceptionListener exceptionListener) throws JMSException {

  }

  @Override
  public void start() throws JMSException {
    _active=true;
  }

  @Override
  public void stop() throws JMSException {
    _active=false;
  }

  @Override
  public void close() throws JMSException {
    stop();
  }

  @Override
  public ConnectionConsumer createConnectionConsumer(Destination destination, String s, ServerSessionPool serverSessionPool, int i) throws JMSException {
    return null;
  }

  @Override
  public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String s, String s2, ServerSessionPool serverSessionPool, int i) throws JMSException {
    return null;
  }
}



