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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.apache.commons.lang3.StringUtils;

import static com.zotohlab.frwk.util.CoreUtils.nsb;

/**
 * @author kenl
 */
//@ChannelHandler.Sharable
public class HttpDemuxer extends ChannelInboundHandlerAdapter {

  private String wsockUri="";

/*
  private static final HttpDemuxer _INST = new HttpDemuxer();
  public static HttpDemuxer getInstance() {
    return _INST;
  }
*/
  public HttpDemuxer(String wsockUri) {
    this.wsockUri = nsb(wsockUri);
  }

  public HttpDemuxer() {
  }

  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ChannelPipeline pipe = ctx.pipeline();

    if (msg instanceof HttpRequest &&
        isWEBSock( (HttpRequest)msg)) {
      // turn this pipeline into a standard websock pipeline
      pipe.addAfter("HttpResponseEncoder",
          "WebSocketServerProtocolHandler",
          new WebSocketServerProtocolHandler(wsockUri));
      pipe.remove("HttpObjectAggregator");
    } else {
      // turn this pipeline into a standard http pipeline
      pipe.addAfter("HttpDemuxer", "Expect100", Expect100.getInstance());
      pipe.addAfter("Expect100", "HttpDemux", new HttpDemux());
      pipe.remove("HttpObjectAggregator");
    }

    ctx.fireChannelRead(msg);
    pipe.remove(this);
  }

  private boolean isWEBSock(HttpRequest  req) throws Exception {
    String mo = nsb(HttpHeaders.getHeader(req, "X-HTTP-Method-Override")).trim();
    String mtd = req.getMethod().name();
    if (StringUtils.isNotEmpty(mo)) {
      mtd=mo;
    }
    String ws = nsb(HttpHeaders.getHeader(req, "upgrade")).trim().toLowerCase();
    return "GET".equals(mtd) && "websocket".equals(ws);
  }

}
