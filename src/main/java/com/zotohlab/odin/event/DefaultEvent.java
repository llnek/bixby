
package com.zotohlab.odin.event;


import java.io.Serializable;

public class DefaultEvent implements Event, Serializable {

  protected EventContext eventContext;
  protected int type;
  protected Object source;
  protected long timeStamp;

  public DefaultEvent() {
    timeStamp= System.currentTimeMillis();
  }

  public EventContext getContext() {
    return eventContext;
  }

  public int getType() {
    return type;
  }

  public Object getSource() {
    return source;
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  public void setContext(EventContext context) {
    this.eventContext = context;
  }

  public void setType(int type) {
    this.type = type;
  }

  public void setSource(Object source) {
    this.source = source;
  }

  public void setTimeStamp(long timeStamp) {
    this.timeStamp = timeStamp;
  }

  public String toString() {
    return "Event [type=" + type + ", source=" + source + ", timeStamp="
        + timeStamp + "]";
  }

}

