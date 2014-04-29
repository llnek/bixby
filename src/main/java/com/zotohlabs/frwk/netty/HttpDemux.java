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
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;

import static com.zotohlabs.frwk.util.CoreUtils.nsb;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class HttpDemux extends AuxHttpDecoder {

  private static final HttpDemux sharedHandler = new HttpDemux();
  public static HttpDemux getInstance() {
    return sharedHandler;
  }

  private boolean isFormPost ( HttpMessage req, String method) {
    String ct = nsb(HttpHeaders.getHeader(req, "content-type")).toLowerCase();
    // multipart form
    return ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) &&
         ( ct.indexOf("multipart/form-data") >= 0 ||
             ct.indexOf("application/x-www-form-urlencoded") >= 0 );
  }

  private boolean isWSock( HttpMessage req, String method) {
    String ws = HttpHeaders.getHeader( req ,"upgrade").trim().toLowerCase();
    return "GET".equals(method) && "websocket".equals(ws);
  }

  private void doRequest(ChannelHandlerContext ctx, HttpMessage msg) throws Exception {
    String mo = HttpHeaders.getHeader( msg, "X-HTTP-Method-Override");
    HttpRequest req = (HttpRequest) msg;
    String mt = req.getMethod().name().toUpperCase();
    ChannelPipeline pipe = ctx.pipeline();
    AuxHttpDecoder nxt = null;

    if (mo != null) {
      mt = mo.toUpperCase();
    }

    setAttr(ctx, MSGINFO_KEY, extractMsgInfo(msg));
    Expect100.handle100(ctx, msg);

    if (isFormPost(msg, mt)) {
      nxt = FormPostCodec.getInstance();
    }
    else
    if (isWSock(msg,mt)) {
      nxt= WebSockCodec.getInstance();
    }
    else {
      nxt=RequestCodec.getInstance();
    }

    if (nxt != null) {
      pipe.addAfter( getName(), nxt.getName(), nxt );
    }
  }

  public void channelRead0(ChannelHandlerContext ctx, Object obj) throws Exception {
    if (obj instanceof HttpRequest) {
      doRequest(ctx, (HttpMessage) obj);
    }
    ctx.fireChannelRead(obj);
  }

}


