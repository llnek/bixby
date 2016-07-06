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


