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

import com.google.gson.JsonObject;
import com.zotohlabs.frwk.net.SSLTrustMgrFactory;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * @author kenl
 */
public class  SSLClientHShake {

  private static Logger _log = LoggerFactory.getLogger(SSLClientHShake.class);
  public static Logger tlog() { return _log; }

  public static ChannelHandler getInstance(JsonObject options) {
    SslHandler hh= null;
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, SSLTrustMgrFactory.getTrustManagers(),
                     SecureRandom.getInstanceStrong());
      SSLEngine eg = ctx.createSSLEngine();
      eg.setUseClientMode(true);
      hh= new SslHandler(eg);
    }
    catch (Throwable e) {
      tlog().error("", e);
    }
    return hh;
  }

}


