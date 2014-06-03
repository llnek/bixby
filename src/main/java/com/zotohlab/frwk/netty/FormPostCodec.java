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
import com.zotohlab.frwk.io.IOUtils;
import com.zotohlab.frwk.io.XData;
import com.zotohlab.frwk.net.ULFileItem;
import com.zotohlab.frwk.net.ULFormItems;
import static com.zotohlab.frwk.util.CoreUtils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.AttributeKey;
import org.apache.shiro.util.StringUtils;
import io.netty.util.ReferenceCountUtil;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.HashMap;

import static com.zotohlab.frwk.io.IOUtils.streamLimit;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class FormPostCodec extends RequestCodec {

  protected static final AttributeKey FORMDEC_KEY = AttributeKey.valueOf( "formdecoder");
  protected static final AttributeKey FORMITMS_KEY= AttributeKey.valueOf("formitems");

  private static FormPostCodec shared = new FormPostCodec();
  public static FormPostCodec getInstance() {
    return shared;
  }

  public FormPostCodec() {
  }

  protected void resetAttrs(ChannelHandlerContext ctx) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC_KEY);
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);

    delAttr(ctx,FORMITMS_KEY);
    delAttr(ctx,FORMDEC_KEY);

    if (fis != null) { fis.destroy(); }
    if (dc != null) { dc.destroy(); }

    super.resetAttrs(ctx);
  }

  private void handleFormPost(ChannelHandlerContext ctx , HttpRequest req)
    throws IOException {
    JsonObject info = (JsonObject) getAttr(ctx.channel(), MSGINFO_KEY);
    String ctype = nsb(HttpHeaders.getHeader(req, "content-type"));
    setAttr( ctx , FORMITMS_KEY, new ULFormItems() );
    setAttr( ctx, XDATA_KEY, new XData() );
    if (ctype.indexOf("multipart") < 0) {
      // nothing to decode.
      setAttr( ctx, CBUF_KEY, Unpooled.compositeBuffer(1024));
      handleMsgChunk(ctx,req);
    }
    else {
      DefaultHttpDataFactory fac= new DefaultHttpDataFactory(streamLimit());
      HttpPostRequestDecoder dc = new HttpPostRequestDecoder( fac, req);
      setAttr( ctx, FORMDEC_KEY, dc);
      handleFormPostChunk(ctx, req);
    }
  }

  private void writeHttpData(ChannelHandlerContext ctx, InterfaceHttpData data)
    throws IOException {
    if (data==null) { return; }
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);
    InterfaceHttpData.HttpDataType dt= data.getHttpDataType();
    String nm = dt.name();
    if (dt == InterfaceHttpData.HttpDataType.FileUpload) {
      FileUpload fu = (FileUpload)data;
      String ct = fu.getContentType();
      String fnm = fu.getFilename();
      if (fu.isCompleted()) {
        if (fu instanceof DiskFileUpload) {
          File fp = (File) IOUtils.newTempFile(false)[0];
          ((DiskFileUpload) fu).renameTo(fp);
          fis.add (new ULFileItem( nm , ct, fnm , new XData( fp)));
        } else {
          Object[] fos = IOUtils.newTempFile(true);
          OutputStream os = (OutputStream)fos[1];
          File fp = (File)fos[0];
          slurpByteBuf( fu.content() , os);
          org.apache.commons.io.IOUtils.closeQuietly(os);
          fis.add( new ULFileItem( nm , ct, fnm,  new XData( fp)));
        }
      }
    }
    else
    if (dt == InterfaceHttpData.HttpDataType.Attribute) {
      ByteArrayOutputStream baos= new ByteArrayOutputStream();
      Attribute attr =  (Attribute) data;
      slurpByteBuf(attr.content(), baos);
      fis.add(new ULFileItem( nm, baos.toByteArray() ));
    }
    else {
      throw new IOException( "Bad POST: unknown http data.");
    }
  }

  private void readHttpDataChunkByChunk(ChannelHandlerContext ctx, HttpPostRequestDecoder dc)
    throws IOException {
    try {
      while (dc.hasNext() ) {
        InterfaceHttpData data = dc.next();
        try {
          writeHttpData( ctx, data);
        }
        finally {
          data.release();
        }
      }
    }
    catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
      //eat it => indicates end of content chunk by chunk
    }
  }

  protected void finzAndDone(ChannelHandlerContext ctx, JsonObject info, XData xs)
      throws IOException {
    resetAttrs(ctx);
    String s = xs.hasContent() ? new String(xs.javaBytes(), "utf-8") : "";
    ULFormItems itms =splitBodyParams(s);
    xs.resetContent(itms);
    tlog().debug("fire fully decoded message to the next handler");
    ctx.fireChannelRead( new DemuxedMsg(info, xs));
  }

  private ULFormItems splitBodyParams(String body) throws IOException {
    tlog().debug("About to split form body *************************\n" +
    body + "\n" +
    "****************************************************************");
    String[] tkns = StringUtils.split(body, '&');
    String t, fn, fv;
    String[] ss;
    ULFormItems fis= new ULFormItems();
    if (tkns != null) for (int i=0; i < tkns.length; ++i) {
      t = nsb(tkns[i]);
      ss= StringUtils.split(t, '=');
      if (ss != null && ss.length > 0) {
        fn = URLDecoder.decode(ss[0], "utf-8");
        fv="";
        if (ss.length > 1) {
          fv= URLDecoder.decode( ss[1], "utf-8");
        }
        fis.add( new ULFileItem(fn,  fv.getBytes("utf-8")) );
      }
    }
    return fis;
  }

  private void handleFormPostChunk(ChannelHandlerContext ctx, Object msg)
    throws IOException {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC_KEY);
    Throwable err= null;
    HttpContent hc;

    if (dc == null) {
      handleMsgChunk(ctx, msg);
      return;
    }

    if (msg instanceof HttpContent) {
      hc = (HttpContent) msg;
      if (hc.content() != null &&
          hc.content().isReadable())
      try {
        dc.offer( (HttpContent) msg);
        readHttpDataChunkByChunk(ctx, dc);
      } catch (Throwable e) {
        err = e;
        ctx.fireExceptionCaught(e);
      }
    }
    if (err==null && msg instanceof LastHttpContent) {
      ULFormItems fis= (ULFormItems) getAttr(ctx, FORMITMS_KEY);
      JsonObject info = (JsonObject) getAttr(ctx, MSGINFO_KEY);
      XData xs = (XData) getAttr( ctx, XDATA_KEY);
      delAttr(ctx, FORMITMS_KEY);
      xs.resetContent(fis);
      resetAttrs(ctx);
      ctx.fireChannelRead(new DemuxedMsg(info, xs));
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpRequest) {
      HttpRequest req = (HttpRequest) msg;
      handleFormPost(ctx, req);
    }
    else
    if (msg instanceof HttpContent) {
      HttpContent c = (HttpContent) msg;
      handleFormPostChunk(ctx,c);
    }
    else {
      tlog().error("Unexpected message type {}",  msg==null ? "(null)" : msg.getClass());
      // what is this ? let downstream deal with it
      ReferenceCountUtil.retain(msg);
      ctx.fireChannelRead(msg);
    }
  }
}


