
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

public class AuxRequestDecoder {

  private static Logger _log = LoggerFactory.getLogger(AuxRequestDecoder.class);
  public Logger tlog() { return _log; }

  protected static final AttributeKey FORMDEC_KEY =AttributeKey.valueOf( "formdecoder");
  protected static final AttributeKey FORMITMS_KEY= AttributeKey.valueOf("formitems");
  protected static final AttributeKey XDATA_KEY =AttributeKey.valueOf("xdata");
  protected static final AttributeKey XOS_KEY =AttributeKey.valueOf("ostream");
  protected static final AttributeKey MSGINFO_KEY= AttributeKey.valueOf("msginfo");
  protected static final AttributeKey CBUF_KEY =AttributeKey.valueOf("cbuffer");
  protected static final AttributeKey WSHSK_KEY =AttributeKey.valueOf("wsockhandshaker");

  private void setAttr( ChannelHandlerContext ctx, AttributeKey akey,  Object aval) {
    ctx.attr(akey).set(aval);
  }

  private void delAttr(ChannelHandlerContext ctx , AttributeKey akey) {
    ctx.attr(akey).remove();
  }

  private Object getAttr( ChannelHandlerContext ctx, AttributeKey akey) {
    return ctx.attr(akey).get();
  }

  private void slurpByteBuf(ByteBuf buf, OutputStream os) throws IOException {
    int len =  buf.readableBytes();
    if (len > 0) {
      buf.readBytes( os, len);
    }
  }

  private void resetAttrs(ChannelHandlerContext ctx) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC_KEY);
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);
    ByteBuf buf = (ByteBuf) getAttr(ctx, CBUF_KEY);

    delAttr(ctx,FORMITMS_KEY);
    delAttr(ctx,MSGINFO_KEY);
    delAttr(ctx,FORMDEC_KEY);
    delAttr(ctx,CBUF_KEY);
    delAttr(ctx,XDATA_KEY);
    delAttr(ctx,XOS_KEY);
    delAttr(ctx,WSHSK_KEY);
    if (buf != null) buf.release();
    if (dc != null) dc.destroy();
    if (fis != null) fis.destroy();
  }

  private boolean isFormPost ( HttpMessage req, String method) {
    String ct = nsb(HttpHeaders.getHeader(req, "content-type")).toLowerCase();
    // multipart form
    return ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) &&
         ( ct.indexOf("multipart/form-data") >= 0 ||
             ct.indexOf("application/x-www-form-urlencoded") >= 0 );
  }

  private JsonObject extractHeaders(HttpHeaders hdrs) {
    JsonObject sum= new JsonObject();
    JsonArray arr;
    for (String n : hdrs.names()) {
      arr= new JsonArray();
      for (String s : hdrs.getAll(n)) {
        arr.add( new JsonPrimitive(s));
      }
      sum.add(n.toLowerCase(), arr);
    }
    return sum;
  }

  private JsonObject extractParams(QueryStringDecoder decr) {
    JsonObject sum= new JsonObject();
    JsonArray arr;
    for (Map.Entry<String,List<String>> en : decr.parameters().entrySet()) {
      en.getKey();
      arr= new JsonArray();
      for (String s : en.getValue()) {
        arr.add( new JsonPrimitive(s));
      }
      sum.add(en.getKey(), arr);
    }
    return sum;
  }

  private JsonObject extractMsgInfo( HttpMessage msg) {
    JsonObject info= new JsonObject();
    info.add("is-chunked", new JsonPrimitive( HttpHeaders.isTransferEncodingChunked(msg)));
    info.add("keep-alive", new JsonPrimitive(HttpHeaders.isKeepAlive(msg)));
    info.addProperty("host", HttpHeaders.getHeader(msg, "Host", ""));
    info.addProperty("protocol", msg.getProtocolVersion().toString());
    info.addProperty("clen", HttpHeaders.getContentLength(msg, 0));
    info.add("uri", new JsonPrimitive(""));
    info.add("status", new JsonPrimitive( ""));
    info.add("code", new JsonPrimitive(0));
    info.add("formpost", new JsonPrimitive( false));
    info.add("wsock", new JsonPrimitive( false));
    info.add("params", new JsonObject());
    info.add("method", new JsonPrimitive(""));
    info.add("headers", extractHeaders(msg.headers() ));
    if (msg instanceof HttpResponse) {
      HttpResponseStatus s= ((HttpResponse) msg).getStatus();
      info.add("status", new JsonPrimitive( nsb(s.reasonPhrase())));
      info.addProperty("code", s.code());
    }
    else
    if (msg instanceof HttpRequest) {
      String ws = HttpHeaders.getHeader( msg ,"upgrade").trim().toLowerCase();
      String mo = HttpHeaders.getHeader(msg, "X-HTTP-Method-Override");
      HttpRequest req = (HttpRequest) msg;
      String md = req.getMethod().name();
      String mt;
      if (mo != null) {
        mt= mo;
      } else {
        mt=md;
      }
      QueryStringDecoder dc = new QueryStringDecoder(req.getUri());
      mt=mt.toUpperCase();
      info.add("wsock", new JsonPrimitive( "GET".equals(mt) && "websocket".equals(ws)));
      info.add("formpost", new JsonPrimitive( isFormPost( msg, mt)));
      info.add("params", extractParams(dc));
      info.add("uri", new JsonPrimitive( dc.path()));
      info.add("methos", new JsonPrimitive(mt));
    }
    return info;
  }

  private void handleFormPost(ChannelHandlerContext ctx , HttpRequest req) {
    DefaultHttpDataFactory fac= new DefaultHttpDataFactory(com.zotohlabs.frwk.io.IOUtils.streamLimit());
    HttpPostRequestDecoder dc = new HttpPostRequestDecoder( fac, req);
    setAttr(ctx ,FORMITMS_KEY, new ULFormItems() );
    setAttr( ctx, FORMDEC_KEY, dc);
    handleFormPostChunk(ctx, req);
  }

  private void writeHttpData(ChannelHandlerContext ctx, InterfaceHttpData data) throws IOException {
    if (data==null) { return; }
    InterfaceHttpData.HttpDataType dt= data.getHttpDataType();
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);
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
          ByteBuf buf = fu.content();
          slurpByteBuf( buf, os);
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

  private void readHttpDataChunkByChunk(ChannelHandlerContext ctx, HttpPostRequestDecoder dc) throws IOException {
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

  private void handleFormPostChunk(ChannelHandlerContext ctx, Object msg) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC_KEY);
    Throwable err= null;
    if (msg instanceof HttpContent) {
      try {
        dc.offer( (HttpContent) msg) ;
        readHttpDataChunkByChunk(ctx, dc);
      }
      catch (Throwable e) {
        err= e;
        ctx.fireExceptionCaught(e);
      }
    }
    if (err==null && msg instanceof LastHttpContent) {
      ULFormItems fis= (ULFormItems) getAttr(ctx, FORMITMS_KEY);
      JsonObject info = (JsonObject) getAttr(ctx, MSGINFO_KEY);
      JsonObject result= new JsonObject();
      XData xs = (XData) getAttr( ctx, XDATA_KEY);
      delAttr(ctx, FORMITMS_KEY);
      xs.resetContent(fis);
      resetAttrs(ctx);
      ctx.fireChannelRead(new HashMap<String,Object>() {{
        put("payload", xs);
        put("info", info);
        }});
    }
  }

  protected boolean tooMuchData(ByteBuf content, Object chunc) {
    ByteBuf buf= null;
    boolean rc=false;
    if (chunc instanceof WebSocketFrame) {
      buf = ((WebSocketFrame) chunc).content();
    }
    else
    if (chunc instanceof HttpContent) {
      buf = ((HttpContent) chunc).content();
    }
    if (buf != null) {
      rc = content.readableBytes() > com.zotohlabs.frwk.io.IOUtils.streamLimit() - buf.readableBytes();
    }
    return rc;
  }

  protected OutputStream switchBufToFile(ChannelHandlerContext ctx, CompositeByteBuf bbuf) throws IOException {
    Object[] fos = IOUtils.newTempFile(true);
    OutputStream os = (OutputStream) fos[1];
    File fp = (File) fos[0];
    XData xs = (XData) getAttr(ctx, XDATA_KEY);
    slurpByteBuf(bbuf, os);
    os.flush();
    xs.resetContent(fp);
    setAttr( ctx, XOS_KEY, os);
    return os;
  }

  protected void flushToFile(OutputStream os , Object chunc) throws IOException {
    ByteBuf buf = null;
    if (chunc instanceof WebSocketFrame) { buf = ((WebSocketFrame) chunc).content(); }
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

  protected boolean maybeSSL(ChannelHandlerContext ctx) {
    return ctx.pipeline().get(SslHandler.class) != null;
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


