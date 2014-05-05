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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zotohlabs.frwk.io.IOUtils;
import com.zotohlabs.frwk.io.XData;
import com.zotohlabs.frwk.net.ULFileItem;
import com.zotohlabs.frwk.net.ULFormItems;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.zotohlabs.frwk.util.CoreUtils.*;
import static com.zotohlabs.frwk.io.IOUtils.*;

/**
 * @author kenl
 */
public abstract class AuxHttpDecoder extends SimpleChannelInboundHandler {

  protected static final AttributeKey MSGINFO_KEY= AttributeKey.valueOf("msginfo");
  protected static final AttributeKey CBUF_KEY =AttributeKey.valueOf("cbuffer");
  protected static final AttributeKey XDATA_KEY =AttributeKey.valueOf("xdata");
  protected static final AttributeKey XOS_KEY =AttributeKey.valueOf("ostream");

  private static Logger _log = LoggerFactory.getLogger(AuxHttpDecoder.class);
  public Logger tlog() { return _log; }

  public String getName() {
    return this.getClass().getSimpleName();
  }

  @SuppressWarnings("unchecked")
  protected void setAttr( ChannelHandlerContext ctx, AttributeKey akey,  Object aval) {
    ctx.attr(akey).set(aval);
  }

  @SuppressWarnings("unchecked")
  protected void delAttr(ChannelHandlerContext ctx , AttributeKey akey) {
    ctx.attr(akey).remove();
  }

  @SuppressWarnings("unchecked")
  protected Object getAttr( ChannelHandlerContext ctx, AttributeKey akey) {
    return ctx.attr(akey).get();
  }

  @SuppressWarnings("unchecked")
  protected void setAttr( Channel ch, AttributeKey akey,  Object aval) {
    ch.attr(akey).set(aval);
  }

  @SuppressWarnings("unchecked")
  protected void delAttr(Channel ch , AttributeKey akey) {
    ch.attr(akey).remove();
  }

  @SuppressWarnings("unchecked")
  protected Object getAttr( Channel ch, AttributeKey akey) {
    return ch.attr(akey).get();
  }

  protected void slurpByteBuf(ByteBuf buf, OutputStream os) throws IOException {
    int len = buf==null ? 0 :  buf.readableBytes();
    if (len > 0) {
      buf.readBytes( os, len);
      os.flush();
    }
  }

  protected JsonObject extractHeaders( HttpHeaders hdrs) {
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

  protected JsonObject extractParams(QueryStringDecoder decr) {
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

  protected JsonObject extractMsgInfo( HttpMessage msg) {
    JsonObject info= new JsonObject();
    info.addProperty("is-chunked", HttpHeaders.isTransferEncodingChunked(msg));
    info.addProperty("keep-alive", HttpHeaders.isKeepAlive(msg));
    info.addProperty("host", HttpHeaders.getHeader(msg, "Host", ""));
    info.addProperty("protocol", msg.getProtocolVersion().toString());
    info.addProperty("clen", HttpHeaders.getContentLength(msg, 0));
    info.addProperty("query", "");
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
      int pos = uriStr.indexOf('?');
      if (pos >= 0) {
        info.addProperty("query", uriStr.substring(pos));
      }
    }
    return info;
  }

  protected boolean tooMuchData(ByteBuf content, Object chunc) {
    ByteBuf buf= null;
    boolean rc=false;
    if (chunc instanceof WebSocketFrame) { buf = ((WebSocketFrame) chunc).content(); }
    else
    if (chunc instanceof HttpContent) { buf = ((HttpContent) chunc).content(); }
    if (buf != null) {
      rc = content.readableBytes() > streamLimit() - buf.readableBytes();
    }
    return rc;
  }

  protected OutputStream switchBufToFile(ChannelHandlerContext ctx, CompositeByteBuf bbuf)
    throws IOException {
    XData xs = (XData) getAttr(ctx, XDATA_KEY);
    Object[] fos = IOUtils.newTempFile(true);
    OutputStream os = (OutputStream) fos[1];
    File fp = (File) fos[0];
    slurpByteBuf(bbuf, os);
    os.flush();
    xs.resetContent(fp);
    setAttr( ctx, XOS_KEY, os);
    return os;
  }

  protected void flushToFile(OutputStream os , Object chunc) throws IOException {
    ByteBuf buf = null;
    if (chunc instanceof WebSocketFrame) { buf = ((WebSocketFrame) chunc).content(); }
    else
    if (chunc instanceof HttpContent) { buf = ((HttpContent) chunc).content(); }
    if (buf != null) {
      slurpByteBuf(buf, os);
      os.flush();
    }
  }

  protected void addMoreHeaders(ChannelHandlerContext ctx, HttpHeaders hds) {
    JsonObject info = (JsonObject) getAttr(ctx ,MSGINFO_KEY);
    JsonObject old = info.getAsJsonObject("headers");
    JsonObject nnw= extractHeaders(hds);
    for (Map.Entry<String,JsonElement> en: nnw.entrySet()) {
      old.add(en.getKey(), en.getValue());
    }
  }

  protected boolean maybeSSL(ChannelHandlerContext ctx) {
    return ctx.pipeline().get(SslHandler.class) != null;
  }

  public void channelReadComplete(ChannelHandlerContext ctx)
      throws Exception                    {
    tlog().debug("{}.channelRead - complete called().", getClass().getSimpleName() );
    //super.channelReadComplete(ctx);
    ctx.flush();
  }

}


