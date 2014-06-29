package com.zotoh.odin.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used to switch login-protocol based on the incoming bytes
 * sent by a client. So, based on the incoming bytes, it is possible to set SSL
 * enabled, normal HTTP, default nadron protocol, or custom user protocol for
 * allowing client to login to nadron. The appropriate protocol searcher needs
 * to be injected to this class. Since this class is a non-singleton, the
 * protocol searchers and other dependencies should actually be injected to
 * {@link ProtocolMultiplexerChannelInitializer} class and then passed in while
 * instantiating this class.
 *
 * @author Abraham Menacherry
 *
 */
public class ProtocolMuxDecoder extends ByteToMessageDecoder {

  private static final Logger _log = LoggerFactory.getLogger(ProtocolMuxDecoder.class);
  private final LoginProtocol loginProtocol;
  private final int bytesForProtocolCheck;

  public Logger tlog() { return _log; }

  public ProtocolMuxDecoder(int bytesForProtocolCheck, LoginProtocol loginProtocol) {
    this.loginProtocol = loginProtocol;
    this.bytesForProtocolCheck = bytesForProtocolCheck;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    // use the first bytes to detect a protocol.
    if (in.readableBytes() < bytesForProtocolCheck) {
      return;
    }

    ChannelPipeline pipe = ctx.pipeline();
    Channel ch = ctx.channel();
    byte[] bits;

    if (!loginProtocol.applyProtocol(in, pipe)) {
      bits = new byte[bytesForProtocolCheck];
      in.getBytes(in.readerIndex(), bits, 0, bytesForProtocolCheck);
      tlog().error(
          "Unknown protocol, discard everything and close the connection {}. Incoming Bytes {}",
          ch,
          Hex.encodeHex(bits));
      close(in, ctx);
    } else {
      pipe.remove(this);
    }
  }

  protected void close(ByteBuf buffer, ChannelHandlerContext ctx) {
    buffer.clear();
    ctx.close();
  }

  public LoginProtocol getLoginProtocol() {
    return loginProtocol;
  }

  public int getBytesForProtocolCheck() {
    return bytesForProtocolCheck;
  }

}
