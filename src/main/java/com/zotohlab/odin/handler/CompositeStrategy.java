
package com.zotoh.odin.protocols;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.List;


public class CompositeProtocol implements ConnectProtocol {

  private List<ConnectProtocol> protos= new ArrayList<ConnectProtocol>();

  public CompositeProtocol(List<ConnectProtocol> lst) {
    protos.addAll(lst);
  }

  public void add(ConnectProtocol p) {
    protos.add(p);
  }

  @Override
  public boolean applyProtocol( ChannelPipeline pipe, ByteBuf buf) {
    for (ConnectProtocol p: protos) {
      if (p.applyProtocol(pipe, buf)) {
        return true;
      }
    }
    return false;
  }

}

