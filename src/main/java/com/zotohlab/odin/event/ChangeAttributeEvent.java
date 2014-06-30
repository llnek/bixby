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
// Copyright (c) 2014 Cherimoia, LLC. All rights reserved.
 ??*/


package com.zotohlab.odin.event;

/**
 * @author kenl
 */
public class ChangeAttributeEvent extends DefaultEvent {

  private Object key;
  private Object value;

  public ChangeAttributeEvent(Object key, Object value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public int getType() {
    return Events.CHANGE_ATTRIBUTE;
  }

  public Object getKey() {
    return key;
  }

  public void setKey(Object key) {
    this.key = key;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
    this.setSource(value);
  }

  @Override
  public String toString() {
    return "ChangeAttributeEvent [key=" + key + ", value=" + value
        + ", type=" + type + "]";
  }

}

