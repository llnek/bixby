
package com.zotohlabs.frwk.netty;

import com.google.gson.JsonObject;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public enum  SSLFirst {
;

  private static Logger _log = LoggerFactory.getLogger(SSLFirst.class);
  public static Logger tlog() { return _log; }

  public static ChannelHandler makeServerHandler(JsonObject options) {

    String keyUrlStr =  options.has("serverkey") ? options.get("serverkey").getAsString() : null;
    String pwdStr = options.has("passwd") ? options.get("passwd").getAsString() : null;
    InputStream inp = null;
    SslHandler hh = null;

    if (keyUrlStr != null) try {
      SSLContext x = SSLContext.getInstance("TLS");
      boolean jks = keyUrlStr.trim().endsWith(".jks");
      KeyStore ks;
      if (!jks) {
        ks = KeyStore.getInstance("PKCS12");
      } else {
        ks = KeyStore.getInstance("JKS");
      }
      TrustManagerFactory t = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      KeyManagerFactory k = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      inp = new URL(keyUrlStr).openStream();
      ks.load(inp, pwdStr.toCharArray());
      t.init(ks);
      k.init(ks, pwdStr.toCharArray());
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

  public static ChannelHandler makeClientHandler(JsonObject options) {
    SSLContext ctx = null;
    SSLEngine eg = null;
    SslHandler hh= null;
    try {
      TrustManager m = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] c, String a) {
        }

        public void checkServerTrusted(X509Certificate[] c, String a) {
        }
      };
      ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[]{m}, SecureRandom.getInstanceStrong());
      eg = ctx.createSSLEngine();
      eg.setUseClientMode(true);
      hh= new SslHandler(eg);
    }
    catch (Throwable e) {
      tlog().error("", e);
    }
    return hh;
  }

  public static ChannelPipeline mountAsServer(ChannelPipeline pipe, JsonObject options) {
    ChannelHandler ch = makeServerHandler(options);
    if (ch != null) {
      pipe.addFirst("ssl-hs", ch);
    }
    return pipe;
  }

  public static ChannelPipeline mountAsClient(ChannelPipeline pipe, JsonObject options) {
    String urlStr = options.has("targetUrl") ? options.get("targetUrl").getAsString() : null;
    if (urlStr != null &&  urlStr.trim().startsWith("https://")) {
      pipe.addFirst("ssl-hs", makeClientHandler(options));
    }
    return pipe;
  }

}


