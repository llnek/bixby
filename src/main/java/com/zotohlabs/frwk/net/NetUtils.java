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

package com.zotohlabs.frwk.net;

import org.apache.http.protocol.HttpContext;
import org.apache.http.impl.client.*;
import org.apache.http.*;
import com.zotohlabs.frwk.io.*;
import io.netty.channel.*;
import io.netty.buffer.ByteBuf;
import java.io.*;
import org.slf4j.*;

/**
 * @author kenl
 */
public class NetUtils {

  private static Logger _log=LoggerFactory.getLogger(NetUtils.class);

  public static void cfgForRedirect(AbstractHttpClient cli)  {
    cli.setRedirectStrategy(new DefaultRedirectStrategy() {
      public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
        boolean isRedirect=false;
        try {
          isRedirect = super.isRedirected(request, response, context);
        } catch (ProtocolException  e) {
          _log.warn("",e);
        }
        if (!isRedirect) {
          int responseCode = response.getStatusLine().getStatusCode();
          if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
            isRedirect= true;
          }
        }
        return isRedirect;
      }
    });
  }

  public static ChannelFutureListener dbgNettyDone(final String msg) {
    return new ChannelFutureListener() {
      public void operationComplete(ChannelFuture fff) {
        _log.debug("netty-op-complete: {}", msg);
      }
    };
  }

  public static ChannelPipeline getPipeline(ChannelHandlerContext ctx) {
    return ctx.pipeline();
  }

  public static ChannelPipeline getPipeline(Channel ch) {
    return ch.pipeline();
  }

  public static ChannelFuture wrtFlush(Channel ch, Object obj) {
    return ch.writeAndFlush(obj);
  }

  public  static Channel flush(Channel ch) {
    return ch.flush();
  }

  public static ChannelFuture writeOnly(Channel ch, Object obj) {
    return ch.write(obj);
  }

  public static ChannelFuture closeChannel(Channel ch) {
    return ch.close();
  }

  public static long sockItDown(ByteBuf cbuf, OutputStream out, long lastSum) throws IOException {
    int cnt= (cbuf==null) ? 0 : cbuf.readableBytes();
    if (cnt > 0) {
      byte[] bits= new byte[4096];
      int total=cnt;
      while (total > 0) {
        int len = Math.min(4096, total);
        cbuf.readBytes(bits, 0, len);
        out.write(bits, 0, len);
        total -= len;
      }
      out.flush();
    }
    return lastSum + cnt;
  }

  public static OutputStream swapFileBacked(XData x, OutputStream out, long lastSum) throws IOException {
    if (lastSum > IOUtils.streamLimit() ) {
      Object[] fos = IOUtils.newTempFile(true);
      x.resetContent(fos[0] );
      return (OutputStream) fos[1];
    } else {
      return out;
    }
  }


}

