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

package com.zotohlab.frwk.netty;

import static com.zotohlab.frwk.netty.NettyFW.replyXXX;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class ErrorCatcher extends SimpleChannelInboundHandler {

  private static Logger _log = LoggerFactory.getLogger(ErrorCatcher.class);
  public Logger tlog() { return _log; }

  private static final ErrorCatcher shared = new ErrorCatcher();
  public static ErrorCatcher getInstance() {
    return shared;
  }


  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(ErrorCatcher.class.getSimpleName(), shared);
    return pipe;
  }

  public ErrorCatcher() {
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    replyXXX( ctx.channel(), 500);
  }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) throws Exception {
    tlog().error("", t);
    replyXXX(ctx.channel(), 500);
  }

}

