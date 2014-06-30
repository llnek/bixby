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


import com.zotohlab.odin.network.TCPSender;
import com.zotohlab.odin.network.UDPSender;

/**
 * @author kenl
 */
public class DefaultConnectEvent extends DefaultEvent implements ConnectEvent {

  protected TCPSender tcpSender;
  protected UDPSender udpSender;

  public DefaultConnectEvent(TCPSender tcp, UDPSender udp) {
    this.tcpSender = tcp;
    this.udpSender = udp;
  }

  public DefaultConnectEvent(TCPSender tcp) {
    this(tcp, null);
  }

  public DefaultConnectEvent(UDPSender udp) {
    this(null, udp);
  }

  @Override
  public int getType() {
    return Events.CONNECT;
  }

  @Override
  public void setType(int type) {
    throw new UnsupportedOperationException(
        "Type field is final, it cannot be reset");
  }

  @Override
  public Object getSource() {
    return tcpSender;
  }

  @Override
  public void setSource(Object source) {
    if (source instanceof TCPSender) {
      this.tcpSender = (TCPSender) source;
    }
  }

  public TCPSender getTcpSender() {
    return tcpSender;
  }

  public void setTcpSender(TCPSender tcp) {
    this.tcpSender = tcp;
  }

  public UDPSender getUdpSender() {
    return udpSender;
  }

  public void setUdpSender(UDPSender udp) {
    this.udpSender = udp;
  }

}

