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

public class RequestCodec extends AuxHttpDecoder {

  protected void resetAttrs(ChannelHandlerContext ctx) {
    ByteBuf buf = (ByteBuf) getAttr(ctx, CBUF_KEY);

    if (buf != null) buf.release();
    delAttr(ctx,MSGINFO_KEY);
    delAttr(ctx,CBUF_KEY);
    delAttr(ctx,XDATA_KEY);
    delAttr(ctx,XOS_KEY);
  }

  protected void maybeFinzMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (! (msg instanceof LastHttpContent))  {
      return;
    }
    OutputStream os = (OutputStream) getAttr( ctx, XOS_KEY);
    ByteBuf cbuf = (ByteBuf) getAttr(ctx, CBUF_KEY);
    XData xs = (XData) getAttr( ctx, XDATA_KEY);
    JsonObject info = (JsonObject) getAttr( ctx, MSGINFO_KEY) ;
    addMoreHeaders( ctx, ((LastHttpContent ) msg).trailingHeaders());
    if (os == null) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      slurpByteBuf( cbuf, baos);
      xs.resetContent(baos);
    } else {
      org.apache.commons.io.IOUtils.closeQuietly(os);
    }
    Map<String,Object> result= new HashMap<String,Object>();
    long olen = info.get("clen").getAsLong();
    long clen =  xs.size();
    if (olen != clen) {
      tlog().warn("content-length read from headers = " +  olen +  ", new clen = " + clen );
      info.addProperty("clen", clen);
    }
    resetAttrs(ctx);
    result.put("payload", info);
    result.put("info", info);
    ctx.fireChannelRead(result);
  }

  protected void handleMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (msg instanceof HttpContent) {} else {
      return;
    }
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
    JsonObject info = (JsonObject) getAttr( ctx, MSGINFO_KEY);
    int c;
    boolean good= true;
    setAttr( ctx, CBUF_KEY, Unpooled.compositeBuffer(1024));
    setAttr( ctx, XDATA_KEY, new XData());
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
    if (good) {
      handleMsgChunk(ctx,msg);
    }
  }


}


