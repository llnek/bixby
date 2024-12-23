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
 * Copyright © 2013-2022, Kenneth Leung. All rights reserved. */


package czlab.bixby.mock.jms;


import javax.jms.TopicConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.Connection;


/**
 *
 */
public class MockTopicConnFactory implements TopicConnectionFactory {

  public MockTopicConnFactory() {}

  public Connection createConnection() {
    return  createTopicConnection();
  }

  public Connection createConnection(String user, String pwd) {
    return createTopicConnection(user, pwd);
  }

  public TopicConnection createTopicConnection() {
    return new MockTopicConnection("","");
  }

  public TopicConnection createTopicConnection(String user, String pwd) {
    return new MockTopicConnection(user, pwd);
  }

}


