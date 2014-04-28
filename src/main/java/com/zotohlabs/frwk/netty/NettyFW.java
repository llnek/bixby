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
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import com.zotohlabs.frwk.io.*;
import io.netty.channel.*;
import io.netty.buffer.ByteBuf;
import java.io.*;

import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.*;


/**
 * @author kenl
 */
public enum NettyFW {
;

  private static Logger _log=LoggerFactory.getLogger(NettyFW.class);
  public static Logger tlog() {
    return _log;
  }

  public static ChannelFutureListener dbgNettyDone(final String msg) {
    return new ChannelFutureListener() {
      public void operationComplete(ChannelFuture fff) {
        _log.debug("netty-op-complete: {}", msg);
      }
    };
  }

  public static ChannelPipeline getPipeline(ChannelHandlerContext ctx) {
    return ctx.pipeline();
  }

  public static ChannelPipeline getPipeline(Channel ch) {
    return ch.pipeline();
  }

  public static ChannelFuture writeFlush(Channel ch, Object obj) {
    return ch.writeAndFlush(obj);
  }

  public  static Channel flush(Channel ch) {
    return ch.flush();
  }

  public static ChannelFuture writeOnly(Channel ch, Object obj) {
    return ch.write(obj);
  }

  public static ChannelFuture closeChannel(Channel ch) {
    return ch.close();
  }

  public static long sockItDown(ByteBuf cbuf, OutputStream out, long lastSum) throws IOException {
    int cnt= (cbuf==null) ? 0 : cbuf.readableBytes();
    if (cnt > 0) {
      byte[] bits= new byte[4096];
      int total=cnt;
      while (total > 0) {
        int len = Math.min(4096, total);
        cbuf.readBytes(bits, 0, len);
        out.write(bits, 0, len);
        total -= len;
      }
      out.flush();
    }
    return lastSum + cnt;
  }

  public static OutputStream swapFileBacked(XData x, OutputStream out, long lastSum) throws IOException {
    if (lastSum > IOUtils.streamLimit() ) {
      Object[] fos = IOUtils.newTempFile(true);
      x.resetContent(fos[0] );
      return (OutputStream) fos[1];
    } else {
      return out;
    }
  }

  public static ChannelHandler makeInboundAdaptor(final ChannelReader rdr, final JsonObject options) {

    return new ChannelInboundHandlerAdapter() {
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
        rdr.handle(ctx, msg, options);
      }
    };

  }

  public static ChannelHandler makeInboundHandler(final ChannelReader rdr, final JsonObject options) {

    return new SimpleChannelInboundHandler<Object>() {
      public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        rdr.handle(ctx, msg, options);
      }
    };

  }

  public static ChannelHandler makeChannelInitor(final PipelineConfigurator cfg, final JsonObject options) {

    return new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel ch) {
        cfg.assemble(ch.pipeline() , options);
      }
    };

  }

  public static void closeCF(ChannelFuture cf, boolean keepAlive) {

    if (cf != null && !keepAlive) {
      cf.addListener( ChannelFutureListener.CLOSE);
    }

  }

  public static HttpResponse makeHttpReply(int code) {
    return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code));
  }

  public static HttpResponse makeHttpReply() {
    return makeHttpReply(200);
  }

  public static void replyXXX(Channel ch, int status, boolean keepAlive) {
    HttpResponse rsp = makeHttpReply(status);
    HttpHeaders.setContentLength(rsp, 0);
    closeCF( ch.write(rsp), keepAlive);
  }

  public static void replyXXX(Channel ch, int status) {
    replyXXX(ch, status, false);
  }

  public static boolean maybeSSL(ChannelHandlerContext ctx) {
    return ctx.channel().pipeline().get(SslHandler.class) != null;
  }

  public static HttpResponse makeFullHttpReply(int status, ByteBuf payload) {
    return new DefaultFullHttpResponse( HttpVersion.HTTP_1_1 , HttpResponseStatus.valueOf(status), payload);
  }
  public static HttpResponse makeFullHttpReply(int status) {
    return new DefaultFullHttpResponse( HttpVersion.HTTP_1_1 , HttpResponseStatus.valueOf(status));
  }
  public static HttpResponse makeFullHttpReply() {
    return makeFullHttpReply(200);
  }

  public static void sendRedirect(Channel ch, boolean permanent, String targetUrl) {
    HttpResponse rsp = makeFullHttpReply( permanent ? 301 : 307);
    tlog().debug("Redirecting to -> " + targetUrl);
    HttpHeaders.setHeader(rsp, "location", targetUrl);
    closeCF( writeFlush(ch,rsp), false);
  }

  public static void continue100(ChannelHandlerContext ctx) {
    writeFlush( ctx.channel(), makeFullHttpReply(100));
  }

  public static ChannelPipeline addServerCodec(ChannelPipeline pipe) {
    pipe.addLast( "codec", new HttpServerCodec());
    return pipe;
  }

  public static ChannelPipeline addWriteChunker(ChannelPipeline pipe) {
    pipe.addLast( "wrt-chunker", new ChunkedWriteHandler() );
    return pipe;
  }

}


