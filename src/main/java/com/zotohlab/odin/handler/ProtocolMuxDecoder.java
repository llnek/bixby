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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kenl
 */
public class ProtocolMuxDecoder extends ByteToMessageDecoder {

  private static final Logger _log = LoggerFactory.getLogger(ProtocolMuxDecoder.class);
  private final ConnectStrategy connectStrategy;
  private final int bytesForProtocolCheck;

  public Logger tlog() { return _log; }

  public ProtocolMuxDecoder(int bytesForProtocolCheck, ConnectStrategy s) {
    this.connectStrategy = s;
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

    if (!connectStrategy.applyProtocol( pipe, in)) {
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

  public ConnectStrategy getLoginProtocol() {
    return connectStrategy;
  }

  public int getBytesForProtocolCheck() {
    return bytesForProtocolCheck;
  }

}
