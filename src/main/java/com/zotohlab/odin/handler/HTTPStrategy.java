
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
 * Searches the incoming bytes of a client connection to determine if its an
 * HTTP connection, in which case Websocket or HTTP related handlers will be
 * applied on the piepline.
 *
 * @author Abraham Menacherry
 *
 */
public class HTTPProtocol implements ConnectProtocol {

  private WEBSocketHandler handler;

  @Override
  public boolean applyProtocol( ChannelPipeline pipe, ByteBuf buf) {
    int b1 = buf.getUnsignedByte(buf.readerIndex());
    int b2 = buf.getUnsignedByte(buf.readerIndex() + 1);
    boolean matched = false;
    if (isHttp(b1,b2)) {
      pipe.addLast("decoder", new HttpRequestDecoder());
      pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
      pipeline.addLast("encoder", new HttpResponseEncoder());
      pipeline.addLast("handler", new WebSocketServerProtocolHandler("/odinsocket"));
      pipeline.addLast(LOGIN_HANDLER_NAME, handler);
      matched = true;
    }
    return matched;
  }

  /**
   * Method which checks if the first 2 incoming parameters are G, E or
   * similar combiantions which signal that its an HTTP protocol, since
   * some protocols like nadron's default protocol send the length
   * first (which is 2 arbitrary bytes), its better if this protocol is
   * searched last to avoid switching to HTTP protocol prematurely.
   *
   * @param magic1
   * @param magic2
   * @return true if the two incoming bytes match any of the first two
   *         letter of HTTP headers like GET, POST etc.
   */
  protected boolean isHttp(int magic1, int magic2) {
    return magic1 == 'G' && magic2 == 'E' || // GET
        magic1 == 'P' && magic2 == 'O' || // POST
        magic1 == 'P' && magic2 == 'U' || // PUT
        magic1 == 'H' && magic2 == 'E' || // HEAD
        magic1 == 'O' && magic2 == 'P' || // OPTIONS
        magic1 == 'P' && magic2 == 'A' || // PATCH
        magic1 == 'D' && magic2 == 'E' || // DELETE
        magic1 == 'T' && magic2 == 'R' || // TRACE
        magic1 == 'C' && magic2 == 'O'; // CONNECT
  }

}

