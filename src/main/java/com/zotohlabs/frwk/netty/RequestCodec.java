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

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class RequestCodec extends AuxHttpDecoder {

  private static final RequestCodec shared = new RequestCodec();
  public static RequestCodec getInstance() {
    return shared;
  }

  public RequestCodec() {
  }

  protected void resetAttrs(ChannelHandlerContext ctx) {
    ByteBuf buf = (ByteBuf) getAttr(ctx, CBUF_KEY);
    if (buf != null) buf.release();

    delAttr(ctx,MSGINFO_KEY);
    delAttr(ctx,CBUF_KEY);
    delAttr(ctx,XDATA_KEY);
    delAttr(ctx,XOS_KEY);
  }

  protected void maybeFinzMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (msg instanceof LastHttpContent) {} else  {
      return;
    }
    tlog().debug("Got the final last-http-content chunk, end of message.");
    JsonObject info = (JsonObject) getAttr( ctx, MSGINFO_KEY) ;
    OutputStream os = (OutputStream) getAttr( ctx, XOS_KEY);
    ByteBuf cbuf = (ByteBuf) getAttr(ctx, CBUF_KEY);
    XData xs = (XData) getAttr( ctx, XDATA_KEY);
    addMoreHeaders( ctx, ((LastHttpContent ) msg).trailingHeaders());
    if (os == null) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      slurpByteBuf( cbuf, baos);
      xs.resetContent(baos);
    } else {
      org.apache.commons.io.IOUtils.closeQuietly(os);
    }
    long olen = info.get("clen").getAsLong();
    long clen =  xs.size();
    if (olen != clen) {
      tlog().warn("content-length read from headers = " +  olen +  ", new clen = " + clen );
      info.addProperty("clen", clen);
    }
    finzAndDone(ctx, info, xs);
  }

  private void finzAndDone(ChannelHandlerContext ctx, JsonObject info, XData xs) {
    resetAttrs(ctx);
    tlog().debug("fire fully decoded message to the next handler");
    ctx.fireChannelRead( new DemuxedMsg(info, xs));
  }

  protected void handleMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (msg instanceof HttpContent) {} else {
      return;
    }
    tlog().debug("Got a valid http-content chunk, part of a message.");
    CompositeByteBuf cbuf = (CompositeByteBuf) getAttr( ctx, CBUF_KEY);
    OutputStream os = (OutputStream) getAttr(ctx, XOS_KEY);
    XData xs = (XData) getAttr( ctx, XDATA_KEY);
    HttpContent chk = (HttpContent ) msg;
    if ( !xs.hasContent() && tooMuchData(cbuf,msg)) {
      os = switchBufToFile( ctx, cbuf);
    }
    if (chk.content().isReadable()) {
      if (os == null) {
        chk.retain();
        cbuf.addComponent( chk.content());
        cbuf.writerIndex( cbuf.writerIndex() + chk.content().readableBytes() );
      } else {
        flushToFile( os, chk);
      }
    }
    maybeFinzMsgChunk( ctx, msg);
  }

  protected void handleRedirect(ChannelHandlerContext ctx, HttpMessage msg) {
    IOException err = new IOException( "Redirect is not supported at this time.");
    tlog().error("", err);
    ctx.fireExceptionCaught(err);
  }

  protected void handleInboundMsg(ChannelHandlerContext ctx, HttpMessage msg) throws IOException {
    JsonObject info = (JsonObject) getAttr( ctx.channel(), MSGINFO_KEY);
    if (info == null) { info = extractMsgInfo(msg); }
    delAttr(ctx.channel(), MSGINFO_KEY);
    setAttr(ctx, MSGINFO_KEY, info);
    tlog().debug( "" + info.toString());
    boolean isc = info.get("is-chunked").getAsBoolean();
    String mtd = info.get("method").getAsString();
    boolean good= true;
    int clen = info.get("clen").getAsInt();
    int c;
    setAttr( ctx, CBUF_KEY, Unpooled.compositeBuffer(1024));
    setAttr( ctx, XDATA_KEY, new XData());

    if (mtd.equals("POST") || mtd.equals("PUT"))
    {}
    else
    if (msg instanceof HttpResponse) {
      c = info.get("code").getAsInt();
      if (c >= 200 && c < 300) {
      }
      else
      if (c >= 300 && c < 400) {
        handleRedirect(ctx, msg);
        good= false;
      }
      else {
        tlog().warn( "received http-response with error code " + c);
      }
    }
    /*
    else
    if (clen == 0) {
      finzAndDone(ctx, info, new XData());
      good=false;
    }
*/
    if (good) {
      handleMsgChunk(ctx,msg);
    }

  }


  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    tlog().debug("channel read0 called with msg {}", msg.getClass()) ;
    if (msg instanceof HttpRequest ||
        msg instanceof HttpResponse) {
      tlog().debug("handle new inbound msg {}", msg.getClass() );
      HttpMessage m= (HttpMessage) msg;
      handleInboundMsg(ctx, m);
      //tlog().debug("handled inbound msg. OK" );
    }
    else
    if (msg instanceof HttpContent) {
      tlog().debug("handle inbound msg chunk");
      handleMsgChunk(ctx, msg);
    }
    else {
      tlog().error("Unexpected message type {}",  msg==null ? "(null)" : msg.getClass());
    }
  }
}


