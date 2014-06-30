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

package com.zotohlab.odin.game;


import com.zotohlab.odin.event.Event;
import com.zotohlab.odin.event.EventDispatcher;
import com.zotohlab.odin.event.EventHandler;
import com.zotohlab.odin.network.TCPSender;
import com.zotohlab.odin.network.UDPSender;

import java.util.Collection;

/**
 * @author kenl
 */
public interface Session {

  enum Status {
    NOT_CONNECTED,
    CONNECTING ,
    CONNETED,
    CLOSED
  };

  public Object getId();
  public void setId(Object id);

  public void setAttr(Object key, Object value);
  public Object getAttr(Object key);
  public void removeAttr(Object key);

  public void onEvent(Event event);
  public EventDispatcher getEventDispatcher();

  public boolean isWriteable();
  public void setWriteable(boolean writeable);

  public boolean isUDPEnabled();
  public void setUDPEnabled(boolean isEnabled);

  public boolean isShuttingDown();

  public long getCreationTime();

  public long getLastReadWriteTime();

  public void setStatus(Status status);
  public Status getStatus();

  public boolean isConnected();

  public void addHandler(EventHandler eventHandler);
  public void removeHandler(EventHandler eventHandler);
  public Collection<EventHandler> getEventHandlers(int eventType);

  public void close();

  public void setUDPSender(UDPSender s);
  public UDPSender getUDPSender();

  public void setTCPSender(TCPSender s);
  public TCPSender getTCPSender();

}

