/*
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.mock.jms;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;

/**
 * @author kenl
 *
 */
@SuppressWarnings("unused")
public class MockConnection implements Connection {

  private String _uid;
  private String _pwd;

  public MockConnection(String uid, String pwd) {
    _uid= uid;
    _pwd=pwd;
  }

  public MockConnection() {
    this("","");
  }

  public void close() {}

  public ConnectionConsumer createConnectionConsumer(Destination d, String a1, ServerSessionPool p, int a3) {
    return null;
  }

  public ConnectionConsumer createDurableConnectionConsumer(Topic t, String a1, String a2, ServerSessionPool p,
      int a4) {
    return null;
  }

  public Session createSession(boolean b, int ack) {
    return new MockSession(b, ack);
  }

  public String getClientID() { return ""; }

  public ExceptionListener getExceptionListener() {
    return null;
  }

  public ConnectionMetaData getMetaData() {
    return null;
  }

  public void setClientID(String a) {}

  public void setExceptionListener(ExceptionListener e) {}

  public void start() {}

  public void stop() {}

}

