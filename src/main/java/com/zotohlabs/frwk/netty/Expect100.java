/*??
*
* Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
*
* This library is distributed in the hope that it will be useful
* but without any warranty; without even the implied warranty of
* merchantability or fitness for a particular purpose.
*
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
*
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
*
 ??*/

package com.zotohlabs.frwk.netty;

import com.zotohlabs.wflow.Pipeline;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.json.JSONObject;

public enum Expect100 {
;
  private static HttpResponse c100_rsp() {
    return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                  HttpResponseStatus.CONTINUE);
  }

  public static ChannelHandler makeHandler(JSONObject options) {
    return new ChannelInboundHandlerAdapter() {
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpMessage &&
                   HttpHeaders.is100ContinueExpected( (HttpMessage) msg)) {
          ctx.writeAndFlush( c100_rsp() ).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
              if (! f.isSuccess()) {
                ctx.fireExceptionCaught(f.cause() );
              }
            }
          });
        } else {
          ctx.fireChannelRead(msg);
        }
      }
    };
  }

  public static ChannelPipeline addLast(ChannelPipeline pl, JSONObject options) {
    pl.addLast("ex-100", makeHandler( options ));
    return pl;
  }

}


