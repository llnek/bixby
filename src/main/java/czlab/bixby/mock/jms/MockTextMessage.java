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


import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Random;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.TextMessage;

/**
 *
 */
public class MockTextMessage implements TextMessage {

  private String _type="Mock-Text-Message";
  private String _text;

  public MockTextMessage(String s) {
    _text =s;
  }

  @Override
  public void setText(String s) throws JMSException {
    _text =s;
  }

  @Override
  public String getText() throws JMSException {
    return _text;
  }

  @Override
  public String getJMSMessageID() throws JMSException {
    return "msg-" + new Random().nextInt(Integer.MAX_VALUE);
  }

  @Override
  public void setJMSMessageID(String s) throws JMSException {

  }

  @Override
  public long getJMSTimestamp() throws JMSException {
    return 0;
  }

  @Override
  public void setJMSTimestamp(long l) throws JMSException {

  }

  @Override
  public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
    try {
      return getJMSCorrelationID().getBytes("utf-8");
    } catch (UnsupportedEncodingException e) {
      throw new JMSException("bad correlation id.");
    }
  }

  @Override
  public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException {

  }

  @Override
  public void setJMSCorrelationID(String s) throws JMSException {

  }

  @Override
  public String getJMSCorrelationID() throws JMSException {
    return "" + new Random().nextInt(Integer.MAX_VALUE);
  }

  @Override
  public Destination getJMSReplyTo() throws JMSException {
    return null;
  }

  @Override
  public void setJMSReplyTo(Destination destination) throws JMSException {

  }

  @Override
  public Destination getJMSDestination() throws JMSException {
    return null;
  }

  @Override
  public void setJMSDestination(Destination destination) throws JMSException {

  }

  @Override
  public int getJMSDeliveryMode() throws JMSException {
    return 0;
  }

  @Override
  public void setJMSDeliveryMode(int i) throws JMSException {

  }

  @Override
  public boolean getJMSRedelivered() throws JMSException {
    return false;
  }

  @Override
  public void setJMSRedelivered(boolean b) throws JMSException {

  }

  @Override
  public String getJMSType() throws JMSException {
    return _type;
  }

  @Override
  public void setJMSType(String s) throws JMSException {
    _type=s;
  }

  @Override
  public long getJMSExpiration() throws JMSException {
    return 0;
  }

  @Override
  public void setJMSExpiration(long l) throws JMSException {

  }

  @Override
  public int getJMSPriority() throws JMSException {
    return 0;
  }

  @Override
  public void setJMSPriority(int i) throws JMSException {

  }

  @Override
  public void clearProperties() throws JMSException {

  }

  @Override
  public boolean propertyExists(String s) throws JMSException {
    return false;
  }

  @Override
  public boolean getBooleanProperty(String s) throws JMSException {
    return false;
  }

  @Override
  public byte getByteProperty(String s) throws JMSException {
    return 0;
  }

  @Override
  public short getShortProperty(String s) throws JMSException {
    return 0;
  }

  @Override
  public int getIntProperty(String s) throws JMSException {
    return 0;
  }

  @Override
  public long getLongProperty(String s) throws JMSException {
    return 0;
  }

  @Override
  public float getFloatProperty(String s) throws JMSException {
    return 0;
  }

  @Override
  public double getDoubleProperty(String s) throws JMSException {
    return 0;
  }

  @Override
  public String getStringProperty(String s) throws JMSException {
    return null;
  }

  @Override
  public Object getObjectProperty(String s) throws JMSException {
    return null;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Enumeration getPropertyNames() throws JMSException {
    return null;
  }

  @Override
  public void setBooleanProperty(String s, boolean b) throws JMSException {

  }

  @Override
  public void setByteProperty(String s, byte b) throws JMSException {

  }

  @Override
  public void setShortProperty(String s, short i) throws JMSException {

  }

  @Override
  public void setIntProperty(String s, int i) throws JMSException {

  }

  @Override
  public void setLongProperty(String s, long l) throws JMSException {

  }

  @Override
  public void setFloatProperty(String s, float v) throws JMSException {

  }

  @Override
  public void setDoubleProperty(String s, double v) throws JMSException {

  }

  @Override
  public void setStringProperty(String s, String s2) throws JMSException {

  }

  @Override
  public void setObjectProperty(String s, Object o) throws JMSException {

  }

  @Override
  public void acknowledge() throws JMSException {

  }

  @Override
  public void clearBody() throws JMSException {

  }
}


