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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.json.JSONObject;
import static com.zotohlabs.frwk.netty.NettyFW.*;

/**
 * @author kenl
 */
public enum ErrorCatcher {
;

  public static ChannelHandler makeHandler(JSONObject options) {
    return new ChannelInboundHandlerAdapter() {
      public void exceptionCaught (ChannelHandlerContext ctx, Exception err) {
        replyXXX( ctx.channel(), 500);
      }
    };
  }

  public static ChannelPipeline addLast(ChannelPipeline pl, JSONObject options) {
    pl.addLast("err-catch", makeHandler(options));
    return pl;
  }

}

