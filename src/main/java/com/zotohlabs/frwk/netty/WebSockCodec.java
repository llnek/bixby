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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
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

public class  WebSockCodec extends RequestCodec {

  protected static final AttributeKey WSHSK_KEY =AttributeKey.valueOf("wsockhandshaker");

  protected void resetAttrs(ChannelHandlerContext ctx) {
    delAttr(ctx,WSHSK_KEY);
    super.resetAttrs(ctx);
  }

  protected JsonObject extractMsgInfo( HttpMessage msg) {
    JsonObject info= super.extractMsgInfo(msg);
    if (msg instanceof HttpRequest) {
      String ws = HttpHeaders.getHeader( msg ,"upgrade").trim().toLowerCase();
      String mt= info.get("method").getAsString();
      info.addProperty("wsock", "GET".equals(mt) && "websocket".equals(ws));
    }
    return info;
  }

  protected void wsSSL(ChannelHandlerContext ctx) {
    SslHandler ssl  = ctx.pipeline().get(SslHandler.class);
    final Channel ch = ctx.channel();
    if (ssl != null) {
      ssl.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
        public void operationComplete(Future<Channel> ff) throws Exception {
          if (! ff.isSuccess()) {
            NettyFW.closeChannel(ff.get());
          }
        }
      });
    }
  }

  protected void handleWSock(ChannelHandlerContext ctx , FullHttpRequest req) {
    JsonObject info = (JsonObject) getAttr( ctx, MSGINFO_KEY);
    String prx = maybeSSL(ctx) ?  "wss://" : "ws://";
    String us = prx +  info.get("host").getAsString() + req.getUri();
    WebSocketServerHandshakerFactory wf= new WebSocketServerHandshakerFactory( us, null, false);
    WebSocketServerHandshaker hs =  wf.newHandshaker(req);
    Channel ch = ctx.channel();
    if (hs == null) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
      NettyFW.closeChannel(ch);
    } else {
      setAttr( ctx, CBUF_KEY, Unpooled.compositeBuffer(1024));
      setAttr( ctx, XDATA_KEY, new XData());
      setAttr( ctx, WSHSK_KEY, hs);
      hs.handshake(ch, req).addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture ff) {
          if (ff.isSuccess()) {
            wsSSL(ctx);
          } else {
            ctx.fireExceptionCaught(ff.cause());
          }
        }
      });
    }
  }

  protected void readFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws IOException {
    CompositeByteBuf cbuf = (CompositeByteBuf)  getAttr( ctx, CBUF_KEY);
    XData xs = (XData) getAttr( ctx, XDATA_KEY);
    OutputStream os = (OutputStream) getAttr(ctx, XOS_KEY);
    if ( !xs.hasContent() &&  tooMuchData( cbuf, frame)) {
      os= switchBufToFile( ctx, cbuf);
    }
    if (os == null) {
      frame.retain();
      cbuf.addComponent(frame.content());
      cbuf.writerIndex(cbuf.writerIndex() + frame.content().readableBytes());
    } else {
      flushToFile( os, frame);
    }
  }

  protected void maybeFinzFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws IOException {
    if (frame.isFinalFragment()) {} else {
      return;
    }
    CompositeByteBuf cbuf = (CompositeByteBuf) getAttr( ctx, CBUF_KEY);
    OutputStream os= (OutputStream) getAttr( ctx, XOS_KEY);
    final JsonObject info = (JsonObject) getAttr( ctx, MSGINFO_KEY);
    final XData xs = (XData) getAttr( ctx, XDATA_KEY);
    if (os != null) {
      org.apache.commons.io.IOUtils.closeQuietly(os);
    } else {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      slurpByteBuf( cbuf, baos);
      xs.resetContent(baos);
    }
    resetAttrs(ctx);
    ctx.fireChannelRead( new HashMap<String,Object>() {{
      put("payload", xs);
      put("info", info);
    }});
  }

  protected void handleWSockFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws IOException {
    WebSocketServerHandshaker hs = (WebSocketServerHandshaker) getAttr( ctx, WSHSK_KEY);
    Channel ch = ctx.channel();
    tlog().debug( "nio-wsframe: received a " + frame.getClass());
    if (frame instanceof CloseWebSocketFrame) {
      resetAttrs(ctx);
      hs.close(ch, (CloseWebSocketFrame) frame);
      NettyFW.closeChannel(ch);
    }
    else
    if ( frame instanceof PingWebSocketFrame) {
      ctx.write( new PongWebSocketFrame( frame.content() ));
    }
    else
    if ( frame instanceof ContinuationWebSocketFrame ||
         frame instanceof TextWebSocketFrame ||
         frame instanceof BinaryWebSocketFrame) {
      readFrame( ctx, frame);
      maybeFinzFrame( ctx, frame);
    }
    else {
      throw new IOException( "Bad wsock: unknown frame.");
    }
  }


}


