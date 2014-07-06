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
import com.zotohlab.frwk.net.ULFileItem;
import com.zotohlab.frwk.net.ULFormItems;
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

import static com.zotohlab.frwk.util.CoreUtils.*;
import static com.zotohlab.frwk.io.IOUtils.*;

import static com.zotohlab.frwk.netty.NettyFW.*;

/**
 * @author kenl
 */
@SuppressWarnings("unchecked")
public abstract class AuxHttpDecoder extends SimpleInboundHandler {

  private static Logger _log = LoggerFactory.getLogger(AuxHttpDecoder.class);
  public Logger tlog() { return _log; }

  public void resetAttrs(ChannelHandlerContext ctx) {
    ByteBuf buf = (ByteBuf) getAttr(ctx, CBUF_KEY);
    if (buf != null) buf.release();

    delAttr(ctx,MSGINFO_KEY);
    delAttr(ctx,CBUF_KEY);
    delAttr(ctx,XDATA_KEY);
    delAttr(ctx,XOS_KEY);
  }

  public void handleMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
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

  public void finzAndDone(ChannelHandlerContext ctx, JsonObject info, XData xs)
      throws IOException {
    resetAttrs(ctx);
    tlog().debug("fire fully decoded message to the next handler");
    ctx.fireChannelRead( new DemuxedMsg(info, xs));
  }


  public String getName() {
    return this.getClass().getSimpleName();
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
    JsonObject nnw= NettyFW.extractHeaders(hds);
    for (Map.Entry<String,JsonElement> en: nnw.entrySet()) {
      old.add(en.getKey(), en.getValue());
    }
  }

  protected boolean maybeSSL(ChannelHandlerContext ctx) {
    return ctx.pipeline().get(SslHandler.class) != null;
  }

  public void channelReadXXX(ChannelHandlerContext ctx, Object msg) throws Exception {
    channelRead0(ctx, msg);
  }

  public void __channelReadComplete(ChannelHandlerContext ctx)
      throws Exception                    {
    tlog().debug("{}.channelRead - complete called().", getClass().getSimpleName() );
    //super.channelReadComplete(ctx);
    ctx.flush();
  }

}


