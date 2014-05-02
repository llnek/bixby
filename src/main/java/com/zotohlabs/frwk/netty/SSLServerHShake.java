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

import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandler;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * @author kenl
 */
public class SSLServerHShake {

  private static Logger _log = LoggerFactory.getLogger(SSLServerHShake.class);
  public static Logger tlog() { return _log; }

  private static final String SERVERKEY= "serverKey";
  private static final String PWD= "pwd";

  public static ChannelHandler getInstance(JsonObject options) {

    String keyUrlStr =  options.has(SERVERKEY) ? options.get(SERVERKEY).getAsString() : null;
    String pwdStr = options.has(PWD) ? options.get(PWD).getAsString() : null;
    InputStream inp = null;
    SslHandler hh = null;

    if (keyUrlStr != null) try {
      char[] pwd = pwdStr==null ? null : pwdStr.toCharArray();
      SSLContext x = SSLContext.getInstance("TLS");
      boolean jks = keyUrlStr.endsWith(".jks");
      KeyStore ks;
      if (!jks) {
        ks = KeyStore.getInstance("PKCS12");
      } else {
        ks = KeyStore.getInstance("JKS");
      }
      TrustManagerFactory t = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      KeyManagerFactory k = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      inp = new URL(keyUrlStr).openStream();
      ks.load(inp, pwd);
      t.init(ks);
      k.init(ks, pwd);
      x.init(k.getKeyManagers(), t.getTrustManagers(), SecureRandom.getInstanceStrong());
      SSLEngine se = x.createSSLEngine();
      se.setUseClientMode(false);
      hh = new SslHandler(se);
    }
    catch (Throwable e) {
      tlog().error("", e);
    }
    finally {
      if (inp != null) try {
        inp.close();
      } catch (Throwable e) {}
    }
    return hh;
  }

}


