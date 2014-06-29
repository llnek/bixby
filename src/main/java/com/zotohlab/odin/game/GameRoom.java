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
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotoh.odin.game;


import io.netty.channel.Channel;
import java.util.Set;


public interface GameRoom {

  public PlayerSession createPlayerSession(Player player);

  public void onEnter(PlayerSession playerSession);

  public boolean connectSession(PlayerSession playerSession);

  public void afterSessionConnect(PlayerSession playerSession);

  public boolean disconnectSession(PlayerSession session);

  public Set<PlayerSession> getSessions();

  public void setGameRoomName(String gameRoomName);
  public String getGameRoomName();

  public void setGame(Game g);
  public Game getGame();

  public void setStateManager(StateManager stateManager);
  public StateManager getStateManager();

  public void setProtocol(Protocol p);
  public Protocol getProtocol();

  public void setSessions(Set<PlayerSession> sessions);
  public void send(Event event);

  public void sendBroadcast(NetworkEvent networkEvent);

  public void close();

  public void setFactory(SessionFactory factory);
  public SessionFactory getFactory();

}


