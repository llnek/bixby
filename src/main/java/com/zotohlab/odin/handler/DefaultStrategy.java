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

import com.zotohlab.odin.event.Events;
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

/**
 * @author kenl
 */
public class DefaultStrategy implements ConnectStrategy {

  private LengthFieldPrepender lengthFieldPrepender;
  private int frameSize = 1024;
  private EventDecoder eventDecoder;
  private ChannelHandler handler;

  @Override
  public boolean applyProtocol( ChannelPipeline pipe, ByteBuf buf) {
    boolean matched = false;
    int opcode = buf.getUnsignedByte(buf.readerIndex() + 2);
    int protocolVersion = buf.getUnsignedByte(buf.readerIndex() + 3);
    if ( isOdinProtocol(opcode, protocolVersion)) {
      pipe.addLast("framer", createLengthBasedFrameDecoder());
      pipe.addLast("eventDecoder", eventDecoder);
      pipe.addLast(HANDLER_NAME, handler);
      pipe.addLast("lengthFieldPrepender", lengthFieldPrepender);
      matched = true;
    }
    return matched;
  }

  protected boolean isOdinProtocol(int magic1, int magic2) {
    return ((magic1 == Events.LOG_IN ||
                magic1 == Events.RECONNECT) &&
                magic2 == Events.PROTCOL_VERSION);
  }

  public ChannelHandler createLengthBasedFrameDecoder() {
    return new LengthFieldBasedFrameDecoder(frameSize, 0, 2, 0, 2);
  }

  public int getFrameSize() {
    return frameSize;
  }

  public void setFrameSize(int frameSize) {
    this.frameSize = frameSize;
  }

}

