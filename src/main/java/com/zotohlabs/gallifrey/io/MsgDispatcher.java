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

package com.zotohlabs.gallifrey.io;

import com.google.gson.JsonObject;
import com.zotohlabs.frwk.core.Callable;
import com.zotohlabs.frwk.netty.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;

import static com.zotohlabs.frwk.util.CoreUtils.nsb;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class MsgDispatcher extends SimpleChannelInboundHandler<DemuxedMsg> {

  private static final MsgDispatcher shared = new MsgDispatcher();
  public static MsgDispatcher getInstance() {
    return shared;
  }

  public static final AttributeKey DISPKEY= AttributeKey.valueOf("disp-key");

  public MsgDispatcher() {
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, DemuxedMsg msg)
      throws Exception {
    Object obj = ctx.channel().attr(DISPKEY).get();
    if (obj instanceof Callable) {
      Callable cb =  (Callable) obj;
      cb.run( new Object[] { ctx, msg });
    } else {
      ctx.fireChannelRead(msg);
    }
  }

}


