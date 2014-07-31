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
import com.google.gson.JsonPrimitive;
import com.zotohlab.frwk.io.IOUtils;
import com.zotohlab.frwk.io.XData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

import static com.zotohlab.frwk.util.CoreUtils.nsb;


/**
 * @author kenl
 */
public enum NettyFW {
;

  public static final AttributeKey FORMDEC_KEY = AttributeKey.valueOf( "formdecoder");
  public static final AttributeKey FORMITMS_KEY= AttributeKey.valueOf("formitems");
  public static final AttributeKey MSGINFO_KEY= AttributeKey.valueOf("msginfo");
  public static final AttributeKey CBUF_KEY =AttributeKey.valueOf("cbuffer");
  public static final AttributeKey XDATA_KEY =AttributeKey.valueOf("xdata");
  public static final AttributeKey XOS_KEY =AttributeKey.valueOf("ostream");


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

  @SuppressWarnings("unchecked")
  public static void setAttr(ChannelHandlerContext ctx, AttributeKey akey, Object aval) {
    ctx.channel().attr(akey).set(aval);
  }

  @SuppressWarnings("unchecked")
  public static  void delAttr(ChannelHandlerContext ctx , AttributeKey akey) {
    ctx.channel().attr(akey).remove();
  }

  @SuppressWarnings("unchecked")
  public static Object getAttr(ChannelHandlerContext ctx, AttributeKey akey) {
    return ctx.channel().attr(akey).get();
  }

  @SuppressWarnings("unchecked")
  public static void setAttr(Channel ch, AttributeKey akey,  Object aval) {
    ch.attr(akey).set(aval);
  }

  @SuppressWarnings("unchecked")
  public static void delAttr(Channel ch , AttributeKey akey) {
    ch.attr(akey).remove();
  }

  @SuppressWarnings("unchecked")
  public static Object getAttr(Channel ch, AttributeKey akey) {
    return ch.attr(akey).get();
  }

  public static JsonObject extractHeaders(HttpHeaders hdrs) {
    JsonObject sum= new JsonObject();
    JsonArray arr;
    for (String n : hdrs.names()) {
      arr= new JsonArray();
      for (String s : hdrs.getAll(n)) {
        arr.add( new JsonPrimitive(s));
      }
      if (arr.size() > 0) {
        sum.add(n.toLowerCase(), arr);
      }
    }
    return sum;
  }

  public static void slurpByteBuf(ByteBuf buf, OutputStream os) throws IOException {
    int len = buf==null ? 0 :  buf.readableBytes();
    if (len > 0) {
      buf.readBytes( os, len);
      os.flush();
    }
  }

  public static byte[] slurpByteBuf(ByteBuf buf) throws IOException {
    ByteArrayOutputStream baos= new ByteArrayOutputStream(4096);
    slurpByteBuf(buf,baos);
    return baos.toByteArray();
  }

  public static JsonObject extractParams(QueryStringDecoder decr) {
    JsonObject sum= new JsonObject();
    JsonArray arr;
    for (Map.Entry<String,List<String>> en : decr.parameters().entrySet()) {
      arr= new JsonArray();
      for (String s : en.getValue()) {
        arr.add( new JsonPrimitive(s));
      }
      if (arr.size() > 0) {
        sum.add(en.getKey(), arr);
      }
    }
    return sum;
  }

  public static JsonObject extractMsgInfo(HttpMessage msg) {
    JsonObject info= new JsonObject();
    info.addProperty("is-chunked", HttpHeaders.isTransferEncodingChunked(msg));
    info.addProperty("keep-alive", HttpHeaders.isKeepAlive(msg));
    info.addProperty("host", HttpHeaders.getHeader(msg, "Host", ""));
    info.addProperty("protocol", msg.getProtocolVersion().toString());
    info.addProperty("clen", HttpHeaders.getContentLength(msg, 0));
    info.addProperty("uri2", "");
    info.addProperty("query", "");
    info.addProperty("wsock", false);
    info.addProperty("uri", "");
    info.addProperty("status", "");
    info.addProperty("code", 0);
    info.add("params", new JsonObject());
    info.addProperty("method", "");
    info.add("headers", extractHeaders(msg.headers() ));
    if (msg instanceof HttpResponse) {
      HttpResponseStatus s= ((HttpResponse) msg).getStatus();
      info.addProperty("status", nsb(s.reasonPhrase()));
      info.addProperty("code", s.code());
    }
    else
    if (msg instanceof HttpRequest) {
      String mo = HttpHeaders.getHeader(msg, "X-HTTP-Method-Override");
      HttpRequest req = (HttpRequest) msg;
      String uriStr = nsb( req.getUri()  );
      String md = req.getMethod().name();
      String mt;
      if (mo != null && mo.length() > 0) {
        mt= mo;
      } else {
        mt=md;
      }
      QueryStringDecoder dc = new QueryStringDecoder(uriStr);
      info.addProperty("method", mt.toUpperCase());
      info.add("params", extractParams(dc));
      info.addProperty("uri", dc.path());
      info.addProperty("uri2", uriStr);
      int pos = uriStr.indexOf('?');
      if (pos >= 0) {
        info.addProperty("query", uriStr.substring(pos));
      }
    }
    return info;
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


