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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class  WebSockCodec extends RequestCodec {

  protected static final AttributeKey WSHSK_KEY =AttributeKey.valueOf("wsockhandshaker");
  protected static final AttributeKey FULLMSG_KEY =AttributeKey.valueOf("fullmsg");

  private static final WebSockCodec shared = new WebSockCodec();
  public static  WebSockCodec getInstance() {
    return shared;
  }

  public WebSockCodec() {
  }

  protected void resetAttrs(ChannelHandlerContext ctx) {
//    delAttr(ctx, FULLMSG_KEY);
//    delAttr(ctx.channel(),WSHSK_KEY);
    super.resetAttrs(ctx);
  }

  protected void wsSSL(ChannelHandlerContext ctx) {
    SslHandler ssl  = ctx.pipeline().get(SslHandler.class);
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

  protected void handleWSock(final ChannelHandlerContext ctx, FullHttpRequest req) {
    JsonObject info = (JsonObject) getAttr( ctx.channel(), MSGINFO_KEY);
    String prx = maybeSSL(ctx) ?  "wss://" : "ws://";
    String us = prx +  info.get("host").getAsString() + req.getUri();
    WebSocketServerHandshakerFactory wf= new WebSocketServerHandshakerFactory( us, null, false);
    WebSocketServerHandshaker hs =  wf.newHandshaker(req);
    Channel ch = ctx.channel();
    if (hs == null) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
      resetAttrs(ctx);
      NettyFW.closeChannel(ch);
    } else {
      setAttr( ch, MSGINFO_KEY, info);
      setAttr( ch, WSHSK_KEY, hs);
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
    OutputStream os = (OutputStream) getAttr(ctx, XOS_KEY);
    XData xs = (XData) getAttr( ctx, XDATA_KEY);
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
    final JsonObject info = (JsonObject) getAttr( ctx.channel(), MSGINFO_KEY);
    CompositeByteBuf cbuf = (CompositeByteBuf) getAttr( ctx, CBUF_KEY);
    OutputStream os= (OutputStream) getAttr( ctx, XOS_KEY);
    final XData xs = (XData) getAttr( ctx, XDATA_KEY);
    if (os != null) {
      org.apache.commons.io.IOUtils.closeQuietly(os);
    } else {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      slurpByteBuf( cbuf, baos);
      xs.resetContent(baos);
    }
    resetAttrs(ctx);
    ctx.fireChannelRead( new DemuxedMsg(info, xs) );
  }

  protected void handleWSockFrame(ChannelHandlerContext ctx, WebSocketFrame frame)
    throws IOException {

    tlog().debug( "nio-wsframe: received a " + frame.getClass());
    Channel ch = ctx.channel();
    WebSocketServerHandshaker hs = (WebSocketServerHandshaker) getAttr( ch, WSHSK_KEY);

    if (frame instanceof CloseWebSocketFrame) {
      hs.close(ch, (CloseWebSocketFrame) frame.retain());
      resetAttrs(ctx);
      //NettyFW.closeChannel(ch);
    }
    else
    if ( frame instanceof PingWebSocketFrame) {
      ctx.write( new PongWebSocketFrame( frame.content().retain() ));
    }
    else
    if ( frame instanceof BinaryWebSocketFrame ||
         frame instanceof TextWebSocketFrame) {

      setAttr( ctx, CBUF_KEY, Unpooled.compositeBuffer(1024));
      setAttr( ctx, XDATA_KEY, new XData());

      readFrame( ctx, frame);
      maybeFinzFrame( ctx, frame);
    }
    else
    if ( frame instanceof ContinuationWebSocketFrame) {
      readFrame( ctx, frame);
      maybeFinzFrame( ctx, frame);
    }
    else {
      throw new IOException( "Bad wsock: unknown frame.");
    }
  }


  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof FullHttpRequest) {
      FullHttpRequest req = (FullHttpRequest) msg;
      handleWSock(ctx, req);
    }
    else if (msg instanceof HttpRequest) {
      HttpRequest req = (HttpRequest) msg;
      FullHttpRequest fmsg = new DefaultFullHttpRequest(
          req.getProtocolVersion(), req.getMethod(), req.getUri(), Unpooled.EMPTY_BUFFER);
      fmsg.headers().set(req.headers());
      setAttr(ctx, FULLMSG_KEY, fmsg)  ;
      // wait for last chunk
    }
    else if (msg instanceof HttpContent) {
      if (msg instanceof LastHttpContent) {
        FullHttpRequest fmsg = (FullHttpRequest) getAttr(ctx, FULLMSG_KEY) ;
        LastHttpContent trailer = (LastHttpContent) msg;
        fmsg.headers().add(trailer.trailingHeaders());
        handleWSock(ctx, fmsg);
      }
    }
    else if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;
      handleWSockFrame(ctx, frame);
    }
    else {
      tlog().error("Unexpected message type {}",  msg==null ? "(null)" : msg.getClass());
      // what is this ? let downstream deal with it
      ReferenceCountUtil.retain(msg);
      ctx.fireChannelRead(msg);
    }
  }
}


