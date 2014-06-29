
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


/**
 * This is the default protocol of nadron. If incoming event is of type
 * LOG_IN and also has appropriate protocol version as defined in the
 * {@link Events} class, then this protocol will be applied. The 3rd and 4th
 * bytes of the incoming transmission are searched to get this information.
 *
 * @author Abraham Menacherry
 *
 */
public class DefaultProtocol implements ConnectProtocol {

  private LengthFieldPrepender lengthFieldPrepender;
  private int frameSize = 1024;
  private EventDecoder eventDecoder;
  private ConnectHandler handler;

  @Override
  public boolean applyProtocol( ChannelPipeline pipe, ButeBuf buf) {
    boolean matched = false;
    int opcode = buf.getUnsignedByte(buf.readerIndex() + 2);
    int protocolVersion = buf.getUnsignedByte(buf.readerIndex() + 3);
    if (isNadProtocol(opcode, protocolVersion)) {
      pipe.addLast("framer", createLengthBasedFrameDecoder());
      pipe.addLast("eventDecoder", eventDecoder);
      pipe.addLast(LOGIN_HANDLER_NAME, handler);
      pipe.addLast("lengthFieldPrepender", lengthFieldPrepender);
      matched = true;
    }
    return matched;
  }

  protected boolean isNadProtocol(int magic1, int magic2) {
    return ((magic1 == LOG_IN || magic1 == RECONNECT) && magic2 == PROTCOL_VERSION);
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

