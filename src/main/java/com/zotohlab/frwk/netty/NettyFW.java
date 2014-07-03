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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import com.zotohlab.frwk.io.*;
import io.netty.channel.*;
import io.netty.buffer.ByteBuf;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
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

  public static long sockItDown(ByteBuf cbuf, OutputStream out, long lastSum)
    throws IOException {
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

  public static OutputStream swapFileBacked(XData x, OutputStream out, long lastSum)
    throws IOException {
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

    return cfg.configure(options);

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
    try {
      tlog().debug("About to return HTTP status ({}) back to client", status);
      closeCF( ch.writeAndFlush(rsp), keepAlive);
    }
    catch (Throwable e) {
      tlog().warn("",e);
    }
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

  public static ChannelPipeline addWriteChunker(ChannelPipeline pipe) {
    pipe.addLast( "ChunkedWriteHandler", new ChunkedWriteHandler() );
    return pipe;
  }

  public static String getMethod(HttpRequest req) {
    String mo = HttpHeaders.getHeader(req, "X-HTTP-Method-Override");
    String mt = req.getMethod().name().toUpperCase();
    if (mo != null && mo.length() > 0) {
      return mo;
    } else {
     return mt;
    }
  }

  public static String getUriPath(HttpRequest req) {
    return new QueryStringDecoder( req.getUri()).path();
  }

  public static Set<String> getHeaderNames(JsonObject info) {
    JsonObject hds = info.getAsJsonObject("headers");
    Set<String> rc = new HashSet<String>();

    if (hds != null) for (Map.Entry<String,JsonElement> en: hds.entrySet()) {
      rc.add(en.getKey());
    }

    return rc;
  }

  public static List<String> getHeaderValues(JsonObject info, String header) {
    JsonObject hds = info.getAsJsonObject("headers");
    JsonArray arr = (hds == null || header ==null) ? null : hds.getAsJsonArray(header.toLowerCase());
    List<String> rc = new ArrayList<String>();

    if (arr != null) for (JsonElement e : arr) {
      rc.add(e.getAsString()) ;
    }

    return rc;
  }

  public static Set<String> getParameters(JsonObject info) {
    JsonObject hds = info.getAsJsonObject("params");
    Set<String> rc = new HashSet<String>();

    if (hds != null) for (Map.Entry<String,JsonElement> en: hds.entrySet()) {
      rc.add(en.getKey());
    }

    return rc;
  }

  public static List<String> getParameterValues(JsonObject info, String pm) {
    JsonObject hds = info.getAsJsonObject("params");
    JsonArray arr = (hds == null || pm ==null) ? null : hds.getAsJsonArray(pm);
    List<String> rc = new ArrayList<String>();

    if (arr != null) for (JsonElement e : arr) {
      rc.add(e.getAsString()) ;
    }

    return rc;
  }

  public static String[] readStrings(ByteBuf buffer, int numOfStrings) {
    return readStrings(buffer, numOfStrings, CharsetUtil.UTF_8);
  }

  public static String[] readStrings(ByteBuf buffer, int numOfStrings, Charset charset) {
    String[] strings = new String[numOfStrings];
    String theStr;
    for (int i = 0; i < numOfStrings; ++i) {
      theStr = readString(buffer,charset);
      if (null == theStr) { break; }
      strings[i] = theStr;
    }
    return strings;
  }

  public static String readString(ByteBuf buffer) {
    return readString(buffer, CharsetUtil.UTF_8);
  }

  public static String readString(ByteBuf buffer, Charset charset) {
    String rc = null;
    int len;
    if (buffer != null && buffer.readableBytes() > 2) {
      len= buffer.readUnsignedShort();
      rc = readString(buffer, len, charset);
    }
    return rc;
  }

  public static String readString(ByteBuf buffer, int length) {
    return readString(buffer, length, CharsetUtil.UTF_8);
  }

  public static String readString(ByteBuf buffer, int length, Charset charset) {
    if (charset == null) { charset = CharsetUtil.UTF_8; }
    String str = null;
    try {
      str= buffer.readSlice(length).toString(charset);
    }
    catch (Throwable e) {
      tlog().error( "Error occurred while trying to read string from buffer: {}", e);
    }
    return str;
  }

  public static ByteBuf writeStrings(String[] msgs) {
    return writeStrings(CharsetUtil.UTF_8, msgs);
  }

  public static ByteBuf writeStrings(Charset charset, String[] msgs) {
    ByteBuf theBuffer = null;
    ByteBuf buffer = null;
    for (String msg : msgs) {
      if (null == buffer) {
        buffer = writeString(msg,charset);
      } else {
        theBuffer = writeString(msg,charset);
        if (theBuffer != null) {
          buffer = Unpooled.wrappedBuffer(buffer, theBuffer);
        }
      }
    }
    return buffer;
  }

  public static ByteBuf writeString(String msg) {
    return writeString(msg, CharsetUtil.UTF_8);
  }

  public static ByteBuf writeString(String msg, Charset charset) {
    ByteBuf lengthBuffer;
    ByteBuf stringBuffer;
    int len;
    ByteBuf buffer = null;
    try {
      if (charset == null) { charset = CharsetUtil.UTF_8; }
      stringBuffer = Unpooled.copiedBuffer(msg, charset);
      len= stringBuffer.readableBytes();
      lengthBuffer = Unpooled.buffer(2);
      lengthBuffer.writeShort(len);
      buffer = Unpooled.wrappedBuffer(lengthBuffer, stringBuffer);
    }
    catch (Throwable e) {
      tlog().error("Error occurred while trying to write string buffer: {}", e);
    }
    return buffer;
  }

}


