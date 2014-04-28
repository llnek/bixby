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

  public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpRequest) {} else {
      return;
    }
    HttpRequest req = (HttpRequest) msg;
    String mo = nsb(HttpHeaders.getHeader(req, "X-HTTP-Method-Override"));
    String md = req.getMethod().name();
    String mt = mo.length() > 0 ? mo.toUpperCase() : md.toUpperCase();
    ChannelPipeline pipe = ctx.pipeline();

    setAttr(ctx, MSGINFO_KEY, extractMsgInfo(req));
    Expect100.handle100(ctx, req);

    if (isFormPost(req, mt)) {
      pipe.addAfter( getName(), FormPostCodec.sharedHandler.getName(), FormPostCodec.sharedHandler );
    }
    else
    if (isWSock(req,mt)) {
      pipe.addAfter( getName(), WebSockCodec.sharedHandler.getName(), WebSockCodec.sharedHandler );
    }
    else {
      pipe.addAfter( getName(), RequestCodec.sharedHandler.getName(), RequestCodec.sharedHandler );
    }
  }

}


