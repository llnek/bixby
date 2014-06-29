
package com.zotoh.odin.protocols;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;


/**
 * Applies a protocol to the incoming pipeline which will handle login.
 * Subsequent protocol may also be manipulated by these login handlers.
 *
 * @author Abraham Menacherry
 *
 */
public interface ConnectProtocol {

  public static final String HANDLER_NAME = "connect-handler";

  /**
   * Apply a protocol on the pipeline to handle login. Implementations will
   * first "search" if the incoming bytes correspond to the implementations
   * protocol, only if they match, the correspoinding protocol will be
   * applied.
   *
   * @return Returs true if the protocol was applied, else false.
   */
  public boolean applyProtocol(ChannelPipeline pipeline, ByteBuf buf);

}

