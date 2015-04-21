// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package com.zotohlab.frwk.netty;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;

/**
 * Handle 100-continue.
 * 
 * @author kenl
 */
@ChannelHandler.Sharable
public class Expect100Filter extends SimpleInboundFilter {

  private static final Expect100Filter shared = new Expect100Filter();

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(Expect100Filter.class.getSimpleName(), shared);
    return pipe;
  }

  protected Expect100Filter() {}

  public static void handle100(final ChannelHandlerContext ctx, HttpMessage msg) {
    if (HttpHeaders.is100ContinueExpected(msg)) {
      ctx.writeAndFlush( c100_rsp() ).addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture f) {
          if (!f.isSuccess()) {
            ctx.fireExceptionCaught(f.cause());
          }
        }
      });
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpMessage) {
      handle100(ctx, (HttpMessage) msg);
    }
    // simplechannelinboundhandler does a release, so add one here
    ReferenceCountUtil.retain(msg);
    ctx.fireChannelRead(msg);
  }

  private static HttpResponse c100_rsp() {
    return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                       HttpResponseStatus.CONTINUE);
  }

}


