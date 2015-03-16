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
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.netty;

import com.zotohlab.frwk.core.CallableWithArgs;
import com.zotohlab.frwk.io.IOUtils;
import com.zotohlab.frwk.io.XData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static com.zotohlab.frwk.io.IOUtils.streamLimit;
import static com.zotohlab.frwk.netty.NettyFW.*;

/**
 * @author kenl
 */
public abstract class AuxHttpFilter extends SimpleInboundFilter {

  private static Logger _log = LoggerFactory.getLogger(AuxHttpFilter.class);
  public Logger tlog() { return _log; }

  /** Clean up any attached attributes.
   */
  public void resetAttrs(ChannelHandlerContext ctx) {

    ByteBuf buf= (ByteBuf) getAttr(ctx, CBUF_KEY);
    if (buf != null) { buf.release(); }

    delAttr(ctx,MSGFUNC_KEY);
    delAttr(ctx,MSGINFO_KEY);
    delAttr(ctx,CBUF_KEY);
    delAttr(ctx,XDATA_KEY);
    delAttr(ctx,XOS_KEY);
  }

  public void handleMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (msg instanceof HttpContent) {
      tlog().debug("Got a valid http-content chunk, part of a message.");
    } else {
      return;
    }
    CompositeByteBuf cbuf = (CompositeByteBuf) getAttr(ctx, CBUF_KEY);
    OutputStream os = (OutputStream) getAttr(ctx, XOS_KEY);
    XData xs = (XData) getAttr(ctx, XDATA_KEY);
    HttpContent chk = (HttpContent) msg;
    // if we have not done already, may be see if we need to switch to file.
    if ( !xs.hasContent() && tooMuchData(cbuf, msg)) {
      os = switchBufToFile(ctx, cbuf);
    }
    ByteBuf cc= chk.content();
    if (cc.isReadable()) {
      if (os == null) {
        chk.retain();
        cbuf.addComponent( cc);
        cbuf.writerIndex( cbuf.writerIndex() + cc.readableBytes() );
      } else {
        flushToFile(os, chk);
      }
    }
    // is this the last chunk?
    maybeFinzMsgChunk(ctx, msg);
  }

  protected void maybeFinzMsgChunk(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (msg instanceof LastHttpContent) {
      tlog().debug("Got the final last-http-content chunk, end of message.");
    } else  {
      return;
    }
    OutputStream os = (OutputStream) getAttr(ctx, XOS_KEY);
    CallableWithArgs func= (CallableWithArgs) getAttr(ctx, MSGFUNC_KEY) ;
    addMoreHeaders(ctx, ((LastHttpContent ) msg).trailingHeaders());
    ByteBuf cbuf = (ByteBuf) getAttr(ctx, CBUF_KEY);
    XData xs = (XData) getAttr(ctx, XDATA_KEY);
    if (os == null) {
      OutputStream baos = new ByteArrayOutputStream();
      slurpByteBuf(cbuf, baos);
      xs.resetContent(baos);
    } else {
      org.apache.commons.io.IOUtils.closeQuietly(os);
      os=null;
    }
    func.run(new Object[]{ ctx, "setContentLength", xs.size() });
    // all done.
    finzAndDone(ctx, xs);
  }

  protected void finzAndDone(ChannelHandlerContext ctx, XData xs)
      throws IOException {
    tlog().debug("fire fully decoded message to the next handler");
    Map<?,?> info = (Map<?,?>) getAttr(ctx, MSGINFO_KEY);
    resetAttrs(ctx);
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
    setAttr(ctx, XOS_KEY, os);
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
    CallableWithArgs func= (CallableWithArgs) getAttr(ctx, MSGFUNC_KEY) ;
    func.run(new Object[]{ ctx, "appendHeaders", hds });
  }

  protected boolean maybeSSL(ChannelHandlerContext ctx) {
    return ctx.pipeline().get(SslHandler.class) != null;
  }

  @SuppressWarnings("unchecked")
  public void channelReadXXX(ChannelHandlerContext ctx, Object msg) throws Exception {
    channelRead0(ctx, msg);
  }

  public void XXXchannelReadComplete(ChannelHandlerContext ctx)
      throws Exception                    {
    tlog().debug("{}.channelRead - complete called().", getClass().getSimpleName() );
    //super.channelReadComplete(ctx);
    ctx.flush();
  }

}


