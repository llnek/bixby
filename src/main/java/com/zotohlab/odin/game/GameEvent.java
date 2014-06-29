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

package com.zotoh.odin.game;


public interface GameEvent {

  public void setPayload(Object payload);
  public Object getPayload();

  public void setPlayerSession(PlayerSession ps);
  public PlayerSession getPlayerSession();

  public void setOpcode(Object opcode);
  public Object getOpCode();

  public void setEventType(Object eventType);
  public Object getEventType();

  public void setEventName(String eventName);
  public String getEventName();

  public void setTimeStamp(long timeStamp);
  public long getTimeStamp();

}
