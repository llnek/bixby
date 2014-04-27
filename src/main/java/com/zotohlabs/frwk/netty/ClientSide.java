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

import com.google.gson.JsonObject;
import com.zotohlabs.frwk.core.Callable;
import com.zotohlabs.frwk.io.XData;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.AttributeKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;

public enum ClientSide {
;

  private static Logger _log = LoggerFactory.getLogger(ClientSide.class) ;
  public static Logger tlog() { return ClientSide._log; }

  private static ChannelHandler makeChannelInitor(PipelineConfigurator cfg, JsonObject options) {
    return new ChannelInitializer() {
      public void initChannel(Channel ch) {
        cfg.assemble(ch.pipeline(), options);
      }
    };
  }

  public static Bootstrap initClientSide(PipelineConfigurator cfg, JsonObject options) {
    Bootstrap bs= new Bootstrap();
    bs.group( new NioEventLoopGroup() );
    bs.channel(NioSocketChannel.class);
    bs.option(ChannelOption.SO_KEEPALIVE,true);
    bs.option(ChannelOption.TCP_NODELAY,true);
    bs.handler( makeChannelInitor(cfg, options));
    return bs;
  }

  private static final AttributeKey URL_KEY = AttributeKey.valueOf("targetUrl");

  public static Channel connect(Bootstrap bs, URL targetUrl) throws IOException {
    boolean ssl = "https".equals(targetUrl.getProtocol());
    int pnum = targetUrl.getPort();
    int port = (pnum < 0) ? (ssl ?  443 : 80) : pnum;
    String host = targetUrl.getHost();
    InetSocketAddress sock = new InetSocketAddress( host, port);
    ChannelFuture cf = null;
    try {
      cf = bs.connect(sock).sync();
    } catch (InterruptedException e) {
      throw new IOException("Connect failed: ", e);
    }
    if ( ! cf.isSuccess() ) {
      throw new IOException("Connect error: ", cf.cause() );
    }
    Channel c= cf.channel();
    c.attr(URL_KEY).set(targetUrl);
    return c;
  }

  public static XData post(Channel c, XData data, JsonObject options) throws IOException {
    return send(c, "POST", data, options);
  }

  public static  XData post(Channel c, XData data) throws IOException {
    return send(c,"POST", data, new JsonObject() );
  }

  public static  XData get(Channel c, JsonObject options) throws IOException {
    return send(c, "GET", new XData(), options);
  }

  public static  XData get(Channel c) throws IOException {
    return send(c,"GET", new XData(), new JsonObject() );
  }

  private static XData send(Channel ch, String op, XData xdata, JsonObject options) throws IOException {
    long clen = (xdata == null) ?  0L : xdata.size();
    URL targetUrl = (URL) ch.attr(URL_KEY).get();
    String mo = options.has("override") ? options.get("override").getAsString() : null;
    String mt = op;
    if (mo != null) {
      mt= mo;
    }
    HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(mt), targetUrl.toString() );
    //TODO: fix this
    Callable cb = (Callable) options.get("presend");
    HttpHeaders.setHeader(req, "Connection",
        options.has("keepalive") && options.get("keep-alive").getAsBoolean() ? "keep-alive" : "close");
    HttpHeaders.setHeader(req, "host", targetUrl.getHost());
    if (cb != null) {
      cb.call(req);
    }

    String ct = HttpHeaders.getHeader(req, "content-type");
    if (clen > 0L && ct == null) {
      HttpHeaders.setHeader(req, "content-type",  "application/octet-stream");
    }

    HttpHeaders.setContentLength(req, clen);

    tlog().debug("Netty client: about to flush out request (headers)");
    tlog().debug( "Netty client: content has length " +  clen);

    ChannelFuture cf= ch.write(req);
    if (clen > 0L) {
      if (clen > com.zotohlabs.frwk.io.IOUtils.streamLimit() ) {
        cf= ch.writeAndFlush( new ChunkedStream(xdata.stream()) );
      } else {
        cf= ch.writeAndFlush(Unpooled.wrappedBuffer(xdata.javaBytes()));
      }
    }

    return null;
  }


}


