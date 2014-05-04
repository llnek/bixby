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

package com.zotohlabs.frwk.netty;

import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;

import static com.zotohlabs.frwk.util.CoreUtils.nsb;
import com.zotohlabs.frwk.netty.NettyFW;
import io.netty.handler.codec.http.HttpResponse;


/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class HttpDemux extends AuxHttpDecoder {

  private static final HttpDemux shared = new HttpDemux();
  public static HttpDemux getInstance() {
    return shared;
  }

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(HttpDemux.class.getSimpleName(), shared);
    return pipe;
  }

  private boolean isFormPost ( HttpMessage msg, String method) {
    String ct = nsb(HttpHeaders.getHeader(msg, "content-type")).toLowerCase();
    // multipart form
    return ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) &&
         ( ct.indexOf("multipart/form-data") >= 0 ||
             ct.indexOf("application/x-www-form-urlencoded") >= 0 );
  }

  private boolean isWSock( HttpMessage msg, String method) {
    String ws = nsb(HttpHeaders.getHeader( msg ,"upgrade")).trim().toLowerCase();
    return "GET".equals(method) && "websocket".equals(ws);
  }

  private void maybeClear(ChannelPipeline pipe, String nm) {
    if (pipe.get(nm) != null) {
      pipe.remove(nm);
    }
  }

  private void doDemux(ChannelHandlerContext ctx, HttpMessage msg, JsonObject info)
    throws Exception {
    String mt = info.get("method").getAsString();
    ChannelPipeline pipe = ctx.pipeline();
    AuxHttpDecoder nxt = null;

    setAttr(ctx.channel(), MSGINFO_KEY, info);
    Expect100.handle100(ctx, msg);

    // clean out last pass, in case this channel is being reused.
    maybeClear(pipe, FormPostCodec.class.getSimpleName());
    maybeClear(pipe, WebSockCodec.class.getSimpleName());
    maybeClear(pipe, RequestCodec.class.getSimpleName());

    if (isFormPost(msg, mt)) {
      nxt = FormPostCodec.getInstance();
      info.addProperty("formpost", true);
    }
    else
    if (isWSock(msg,mt)) {
      nxt= WebSockCodec.getInstance();
      info.addProperty("wsock", true);
    }
    else {
      nxt=RequestCodec.getInstance();
    }

    if (nxt != null ) {
      tlog().debug("Inserting new handler {} after {}", nxt.getName(), getName());
      pipe.addAfter( getName(), nxt.getName(), nxt );
    }
  }

  public void channelRead0(ChannelHandlerContext ctx, Object obj) throws Exception {
    if (obj instanceof HttpRequest || obj instanceof HttpResponse) {
      HttpMessage msg = (HttpMessage) obj;
      doDemux(ctx, msg, extractMsgInfo(msg));
    }
    tlog().debug("Demux message to the next handler");
    ctx.fireChannelRead(obj);
  }

}


