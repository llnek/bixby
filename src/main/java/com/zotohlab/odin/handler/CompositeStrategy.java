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

package com.zotohlab.odin.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kenl
 */
public class CompositeStrategy implements ConnectStrategy {

  private List<ConnectStrategy> protos= new ArrayList<ConnectStrategy>();

  public CompositeStrategy(List<ConnectStrategy> lst) {
    protos.addAll(lst);
  }

  public void add(ConnectStrategy p) {
    protos.add(p);
  }

  @Override
  public boolean applyProtocol( ChannelPipeline pipe, ByteBuf buf) {
    for (ConnectStrategy p: protos) {
      if (p.applyProtocol(pipe, buf)) {
        return true;
      }
    }
    return false;
  }

}

