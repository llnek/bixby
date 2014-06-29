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

import java.util.List;


public interface Session {

  enum Status
  {
    NOT_CONNECTED, CONNECTING, CONNECTED, CLOSED
  }

  Object getId();

  void setId(Object id);

  void setAttribute(String key, Object value);

  Object getAttribute(String key);

  void removeAttribute(String key);

  void onEvent(Event event);

  EventDispatcher getEventDispatcher();

  boolean isWriteable();
  void setWriteable(boolean writeable);

  boolean isUDPEnabled();
  void setUDPEnabled(boolean isEnabled);

  boolean isShuttingDown();

  long getCreationTime();

  long getLastReadWriteTime();

  void setStatus(Status status);
  Status getStatus();

  boolean isConnected();

  void addHandler(EventHandler eventHandler);
  void removeHandler(EventHandler eventHandler);
  List<EventHandler> getEventHandlers(int eventType);

  void close();

  public void setUdpSender(Fast udpSender);

  public Fast getUdpSender();

  public void setTcpSender(Reliable tcpSender);

  public Reliable getTcpSender();
}

