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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;

import static com.zotohlabs.frwk.util.CoreUtils.nsb;
import com.zotohlabs.frwk.netty.NettyFW;
import io.netty.handler.codec.http.HttpResponse;

import java.io.IOException;


/**
 * @author kenl
 */
//@ChannelHandler.Sharable
public class HttpDemux extends AuxHttpDecoder {

//  private static final HttpDemux shared = new HttpDemux();
//  public static HttpDemux getInstance() {
//    return shared;
//  }

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(HttpDemux.class.getSimpleName(), new HttpDemux() );
    return pipe;
  }

  private AuxHttpDecoder myDelegate;

  public HttpDemux() {
//    formpost= FormPostCodec.getInstance();
//    wsock = WebSockCodec.getInstance();
//    basic = RequestCodec.getInstance();
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

  private void doDemux(ChannelHandlerContext ctx, Object inboundObject)
    throws Exception {
    HttpMessage msg = (HttpMessage) inboundObject;
    JsonObject info = extractMsgInfo(msg);
    Channel ch = ctx.channel();

    String mt = info.get("method").getAsString();
    String uri= info.get("uri").getAsString();

    tlog().debug("HttpDemux: first level demux of message\n{}", inboundObject);
    tlog().debug( "" + info.toString());

    setAttr(ctx, MSGINFO_KEY, info);
    setAttr(ch, MSGINFO_KEY, info);
    myDelegate=null;

    if (uri.trim().startsWith("/favicon.")) {
      // ignore this crap
      NettyFW.replyXXX(ch, 404);
      return;
    }

    Expect100.handle100(ctx, msg);

    if (isFormPost(msg, mt)) {
      myDelegate = FormPostCodec.getInstance();
      info.addProperty("formpost", true);
    }
    else
    if (isWSock(msg,mt)) {
      myDelegate= WebSockCodec.getInstance();
      info.addProperty("wsock", true);
    }
    else {
      myDelegate =RequestCodec.getInstance();
    }

    if (myDelegate != null) {
      myDelegate.channelReadXXX(ctx, msg);
    }

  }

  public void channelRead0(ChannelHandlerContext ctx, Object obj) throws Exception {

    if (obj instanceof HttpRequest ||
        obj instanceof HttpResponse) {
        doDemux(ctx, obj);
    }
    else
    if (myDelegate != null) {
      myDelegate.channelReadXXX(ctx, obj);
    }
    else {
      throw new IOException("Fatal error while reading http message.");
    }

  }

}


