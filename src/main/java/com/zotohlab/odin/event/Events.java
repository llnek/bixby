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
// Copyright (c) 2013-2014 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlab.odin.event;

/**
 * @author kenl
 */
public enum Events {
;
  public static final byte PROTCOL_VERSION=0x01;
  public final static byte ANY = 0x00;

  // Lifecycle events.
  public final static byte CONNECT = 0x02;
  public static final byte RECONNECT = 0x3;
  public final static byte CONNECT_ERROR = 0x06;

  public static final byte LOG_IN = 0x08;
  public static final byte LOG_OUT = 0x0a;
  public static final byte LOG_IN_OK = 0x0b;
  public static final byte LOG_IN_ERROR = 0x0c;
  public static final byte LOG_OUT_OK = 0x0e;
  public static final byte LOG_OUT_ERROR = 0x0f;

  public static final byte GAME_LIST = 0x10;
  public static final byte ROOM_LIST = 0x12;
  public static final byte GAME_ROOM_JOIN = 0x14;
  public static final byte GAME_ROOM_LEAVE = 0x16;
  public static final byte GAME_ROOM_JOIN_OK = 0x18;
  public static final byte GAME_ROOM_JOIN_ERROR = 0x19;

  /**
   * Event sent from server to client to start message sending from client to server.
   */
  public static final byte START = 0x1a;

  /**
   * Event sent from server to client to stop messages from being sent to server.
   */
  public static final byte STOP = 0x1b;

  /**
   * Incoming data from another machine/JVM to this JVM (server or client)
   */
  public static final byte SESSION_MESSAGE = 0x1c;

  /**
   * This event is used to send data from the current machine to remote
   * machines using TCP or UDP transports. It is an out-going event.
   */
  public static final byte NETWORK_MESSAGE = 0x1d;


  public static final byte CHANGE_ATTRIBUTE = 0x20;

  /**
   * If a remote connection is disconnected or closed then raise this event.
   */
  public static final byte DISCONNECT = 0x22;

  /**
   * A network exception will in turn cause this even to be raised.
   */
  public static final byte EXCEPTION = 0x24;

}

