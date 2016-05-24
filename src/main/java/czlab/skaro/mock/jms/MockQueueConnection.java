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

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;

/**
 * @author kenl
 *
 */
@SuppressWarnings("unused")
public class MockQueueConnection implements QueueConnection {

  private volatile boolean _active=false;
  private String _user;
  private String _pwd;

  public MockQueueConnection( String user, String pwd) {
    _user = user;
    _pwd= pwd;
  }

  public MockQueueConnection()  {
    this("","");
    _active=true;
  }

  public void close() {
    stop();
  }

  public ConnectionConsumer createConnectionConsumer(Destination d,
      String a1, ServerSessionPool p, int a3) {
    return null;
  }

  public ConnectionConsumer createDurableConnectionConsumer(Topic t,
      String a1 , String a2 , ServerSessionPool p, int a4 ) {
    return null;
  }

  public Session createSession(boolean b, int a) { return null; }

  public String getClientID() { return ""; }

  public ExceptionListener getExceptionListener() { return null; }

  public ConnectionMetaData getMetaData() { return null; }

  public void setClientID(String a ) {}

  public void setExceptionListener(ExceptionListener e) {}

  public void start() {
    _active=true;
  }

  public void stop() {
    _active=false;
  }

  /**
   * @return
   */
  public boolean isActive() { return _active; }

  public ConnectionConsumer createConnectionConsumer(Queue q, String a1, ServerSessionPool p,
      int a3) {
    return null;
  }

  public QueueSession createQueueSession(boolean tx, int ack) {
    QueueSession s= new MockQueueSession(this, tx, ack);
    s.run() ;
    return s;
  }

}


