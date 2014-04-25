/*??
*
* Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
*
* This library is distributed in the hope that it will be useful
* but without any warranty; without even the implied warranty of
* merchantability or fitness for a particular purpose.
*
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
*
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
*
 ??*/

package com.zotohlabs.frwk.netty;

import io.netty.handler.codec.http.Cookie;

import java.util.*;
import java.io.*;

import io.netty.handler.codec.http.*;
import io.netty.buffer.*;
import io.netty.channel.*;

import org.apache.commons.lang3.StringUtils;
import com.zotohlabs.frwk.io.XData;
import org.json.*;
import org.slf4j.*;
import org.apache.commons.io.IOUtils;
import static com.zotohlabs.frwk.util.CoreUtils.nsb;
import static com.zotohlabs.frwk.io.IOUtils.newTempFile;


/**
 * @author kenl
 *
 */
public class BasicChannelHandler extends SimpleChannelInboundHandler<HttpObject> {

  private static Logger _log= LoggerFactory.getLogger(BasicChannelHandler.class);
  private long _thold= com.zotohlabs.frwk.io.IOUtils.streamLimit();
  private JSONObject _props= new JSONObject();
  private long _clen=0L;
  private boolean _keepAlive=false;

  private List<String> _cookies = null;
  private OutputStream _os= null;
  private File _fOut=null;

  public Logger tlog() { return BasicChannelHandler._log; }
  public boolean isKeepAlive() { return _keepAlive; }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable ev) {
    Channel c= ctx.channel();
    if (c != null)
    try {
        c.close();
    }
    catch (Throwable t) {
    }
    tlog().error("", ev);
  }

  // false to stop further processing
  protected boolean onRecvRequest(JSONObject msgInfo) { return true; }

  public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {

    String msgType= (msg==null) ? "???" : msg.getClass().getName();
    Channel ch = ctx.channel();

    if (msg instanceof HttpMessage) {
      _os= new ByteArrayOutputStream();
      _props= new JSONObject();
      msg_recv_0( (HttpRequest) msg);
    }

    if (msg instanceof HttpResponse) {
      HttpResponse res= (HttpResponse) msg;
      HttpResponseStatus s=  res.getStatus();
      String r= s.reasonPhrase();
      int c= s.code();
      tlog().debug("BasicChannelHandler: got a response: code {} {}", c, r, "");
      _props.put("headers", iterHeaders(res) );
      _props.put("reason", r);
      _props.put("dir", -1);
      _props.put("code", c);
      if (c >= 200 && c < 300) {
        onRes(ctx,s, res);
      } else if (c >= 300 && c < 400) {
        // TODO: handle redirect
        handleResError(ctx, new IOException("redirect not supported."));
      } else {
        handleResError(ctx, new IOException("error code: " + c));
      }
    }
    else
    if (msg instanceof HttpRequest) {
      tlog().debug("BasicChannelHandler: got a request: ");
      HttpRequest req = (HttpRequest) msg;
      if (HttpHeaders.is100ContinueExpected(req)) {
        send100Continue(ch);
      }
      _keepAlive = HttpHeaders.isKeepAlive(req);
      onReqIniz(ctx, req);
      _props.put("method", req.getMethod().name() );
      _props.put("uri", req.getUri() );
      _props.put("headers", iterHeaders(req) );
      _props.put("dir", 1);
      if ( onRecvRequest(_props) ) {
        onReq(ctx,req);
      } else {
        send403(ch);
      }
    }
    else
    if (msg instanceof HttpContent) {
      HttpContent x= (HttpContent) msg;
      onChunk(ctx, x);
    }
    else {
      tlog().error("BasicChannelHandler:  unexpected msg type: " + msgType);
    }

  }

  private void send100Continue(Channel ch) {

      ch.writeAndFlush( new DefaultHttpResponse( HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
  }

  private void send403(Channel ch) {
    ch.writeAndFlush( new DefaultHttpResponse( HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
    tlog().error("403 Forbidden");
  }

  protected void onReq(ChannelHandlerContext ctx, HttpRequest msg) {
    if ( ! HttpHeaders.isTransferEncodingChunked(msg)) {
        try {
            sockBytes( ((ByteBufHolder) msg).content() );
        } catch (IOException e) {
            ctx.fireExceptionCaught(e);
        }
        onMsgFinal(ctx);
    }
  }

  private void onRes(ChannelHandlerContext ctx, HttpResponseStatus rc, HttpResponse msg) {
    onResIniz(ctx,msg);
    if (! HttpHeaders.isTransferEncodingChunked(msg)) {
        try {
            sockBytes( ((ByteBufHolder) msg).content() );
        } catch (IOException e) {
            ctx.fireExceptionCaught(e);
        }
        onMsgFinal(ctx);
    }
  }

  protected void onReqIniz(ChannelHandlerContext ctx, HttpRequest msg) {
    onReqPreamble( _props );
  }

  protected void onResIniz(ChannelHandlerContext ctx, HttpResponse msg) {
    onResPreamble( _props);
  }

  protected void onReqPreamble(JSONObject msgInfo) {
    tlog().debug("BasicChannelHandler: onReqIniz: Method {}, Uri {}",
            nsb(msgInfo.optString("method")),
            nsb(msgInfo.optString("uri")),
            "");
  }

  protected void onResPreamble(JSONObject msgInfo) {}

  protected void doReqFinal(JSONObject msgInfo, XData out) {}
  protected void doResFinal(JSONObject msgInfo, XData out) {}
  protected void onResError(int code, String r) {}

  private void handleResError(ChannelHandlerContext ctx, Throwable err) {
    onResError( _props.optInt("code"), _props.optString("reason"));
    Channel cc= ctx.channel();
    if ( !isKeepAlive() && cc != null) {
      cc.close();
    }
  }

  private void sockBytes(ByteBuf cb) throws IOException {
    int c= 1;
    if (cb != null) while (c > 0) {
      c= cb.readableBytes();
      if (c > 0) {
        sockit(cb,c);
      }
    }
  }

  private void sockit(ByteBuf cb, int count) throws IOException {

    byte[] bits= new byte[4096];
    int len, total=count;

    while (total > 0) {
      len = Math.min(4096, total);
      cb.readBytes(bits, 0, len);
      _os.write(bits, 0, len);
      total -= len;
    }

    _os.flush();

    if (_clen >= 0L) { _clen += count; }
    if (_clen > 0L && _clen > _thold) {
      swap();
    }
  }

  private void swap() throws IOException {
    if (_os instanceof ByteArrayOutputStream) {
      Object[] fos= newTempFile(true);
      OutputStream os = (OutputStream) fos[1];
      os.write(((ByteArrayOutputStream) _os).toByteArray());
      os.flush();
      _fOut= (File) fos[0];
      _os= os;
      _clen= -1L;
    }
  }

  protected void doReplyError(ChannelHandlerContext ctx, HttpResponseStatus err) {
    doReplyXXX(ctx, err);
  }

  private void doReplyXXX(ChannelHandlerContext ctx,  HttpResponseStatus s) {
    HttpResponse res= new DefaultHttpResponse(HttpVersion.HTTP_1_1, s);
    Channel c= ctx.channel();
    //HttpHeaders.setTransferEncodingChunked(res,false)
    HttpHeaders.setHeader(res, "content-length", "0");
    c.write(res);
    if ( ! isKeepAlive() && c != null ) {
      c.close();
    }
  }

  protected void replyRequest(ChannelHandlerContext ctx,  XData data) {
    doReplyXXX(ctx,HttpResponseStatus.OK);
  }

  protected void replyResponse(ChannelHandlerContext ctx, XData data) {
    Channel c= ctx.channel();
    if ( ! isKeepAlive() && c != null ) {
      c.close();
    }
  }

  private void onMsgFinal(ChannelHandlerContext ctx) {
    int dir = _props.optInt("dir");
    XData out= on_msg_final(ctx);
    if ( dir > 0) {
      replyRequest(ctx,out);
      doReqFinal( _props, out);
    } else if (dir < 0) {
      doResFinal( _props, out);
    }
  }

  private XData on_msg_final(ChannelHandlerContext ctx) {
    XData data= new XData();
    if (_fOut != null) {
      data.resetContent(_fOut);
    } else {
      data.resetContent(_os);
    }
    IOUtils.closeQuietly(_os);
    _fOut=null;
    _os=null;
    return data;
  }

  private void msg_recv_0(HttpMessage msg) {
    String s= HttpHeaders.getHeader(msg, HttpHeaders.Names.COOKIE);
    if ( ! StringUtils.isEmpty(s)) {
      Set<Cookie> cs = CookieDecoder.decode(s);
      if (cs.size() > 0) {
        _cookies= ServerCookieEncoder.encode(cs);
      }
    }
  }

  private void onChunk(ChannelHandlerContext ctx, HttpContent msg)  {
      try {
          sockBytes(msg.content() );
      } catch (IOException e) {
          ctx.fireExceptionCaught(e);
      }
      if (msg instanceof LastHttpContent) {
      onMsgFinal(ctx);
    }
  }

  protected JSONObject iterHeaders(HttpMessage msg) {
    JSONObject hdrs= new JSONObject();
    HttpHeaders h= msg.headers();
    for (String n : h.names()) {
      List<String> values= h.getAll(n);
      JSONArray arr= new JSONArray();
      for (String s : values) {
        arr.put(s);
      }
      hdrs.put(n, arr);
    }
    tlog().debug("BasicChannelHandler: headers\n{}", hdrs.toString(2));
    return hdrs;
  }

}

