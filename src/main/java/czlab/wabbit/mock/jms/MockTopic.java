/**
 * Copyright © 2013-2019, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */


package czlab.wabbit.mock.jms;

import javax.jms.Topic;


/**
 *
 */
public class MockTopic implements Topic {

  public String getTopicName() { return _name; }

  private String _name;

  public MockTopic(String n) {
    _name=n;
  }


}


