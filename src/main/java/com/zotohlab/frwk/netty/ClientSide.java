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

import com.google.gson.JsonObject;
import com.zotohlab.frwk.io.XData;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
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
import java.util.Map;

import static com.zotohlab.frwk.io.IOUtils.streamLimit;

/**
 * @author kenl
 */
@SuppressWarnings("unchecked")
public enum ClientSide {
;

  private static final AttributeKey URL_KEY = AttributeKey.valueOf("targetUrl");
  private static Logger _log = LoggerFactory.getLogger(ClientSide.class) ;
  public static Logger tlog() { return _log; }

  /**
   */
  public static Bootstrap initClientSide(PipelineConfigurator cfg, Map<?,?> options) {
    Bootstrap bs= new Bootstrap();
    bs.group( new NioEventLoopGroup() );
    bs.channel(NioSocketChannel.class);
    bs.option(ChannelOption.SO_KEEPALIVE,true);
    bs.option(ChannelOption.TCP_NODELAY,true);
    bs.handler( cfg.configure(options));
    return bs;
  }

  /**
   */
  public static Channel connect(Bootstrap bs, URL targetUrl) throws IOException {
    boolean ssl = "https".equals(targetUrl.getProtocol());
    int pnum = targetUrl.getPort();
    int port = (pnum < 0) ? (ssl ?  443 : 80) : pnum;
    String host = targetUrl.getHost();
    InetSocketAddress sock = new InetSocketAddress(host, port);
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

  public static void post(Channel c, XData data, JsonObject options) throws IOException {
    send(c, "POST", data, options);
  }

  public static void post(Channel c, XData data) throws IOException {
    send(c,"POST", data, new JsonObject() );
  }

  public static  void get(Channel c, JsonObject options) throws IOException {
    send(c, "GET", new XData(), options);
  }

  public static  void get(Channel c) throws IOException {
    send(c,"GET", new XData(), new JsonObject() );
  }

  private static ChannelFuture send(Channel ch, String op, XData xdata, JsonObject options)
    throws IOException {
    long clen = (xdata == null) ?  0L : xdata.size();
    URL targetUrl = (URL) ch.attr(URL_KEY).get();
    String mo = options.has("override") ? options.get("override").getAsString() : null;
    HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(op),
                                             targetUrl.toString() );
    HttpHeaders.setHeader(req, "Connection",
                               options.has("keep-alive") && options.get("keep-alive").getAsBoolean()
                               ? "keep-alive" : "close");

    HttpHeaders.setHeader(req, "host", targetUrl.getHost());

    String ct = HttpHeaders.getHeader(req, "content-type");
    if (clen > 0L && ct == null) {
      HttpHeaders.setHeader(req, "content-type",  "application/octet-stream");
    }

    if (mo != null) { HttpHeaders.setHeader(req, "X-HTTP-Method-Override", mo); }
    HttpHeaders.setContentLength(req, clen);

    tlog().debug("Netty client: about to flush out request (headers)");
    tlog().debug( "Netty client: content has length " +  clen);

    ChannelFuture cf= ch.write(req);
    if (clen > 0L) {
      if (clen > streamLimit() ) {
        cf= ch.writeAndFlush( new ChunkedStream(xdata.stream()) );
      } else {
        cf= ch.writeAndFlush(Unpooled.wrappedBuffer(xdata.javaBytes()));
      }
    }

    return cf;
  }


}


