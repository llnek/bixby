
package com.zotohlabs.frwk.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import org.json.JSONObject;
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

  public static ChannelHandler makeServerHandler(JSONObject options) {

    URL keyUrl = (URL) options.opt("serverkey");
    String pwd = options.optString("passwd");
    InputStream inp = null;
    SslHandler hh = null;

    if (keyUrl != null) try {
      boolean jks = keyUrl.getFile().endsWith(".jks");
      SSLContext x = SSLContext.getInstance("TLS");
      KeyStore ks;
      if (!jks) {
        ks = KeyStore.getInstance("PKCS12");
      } else {
        ks = KeyStore.getInstance("JKS");
      }
      TrustManagerFactory t = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      KeyManagerFactory k = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      inp = keyUrl.openStream();
      ks.load(inp, pwd.toCharArray());
      t.init(ks);
      k.init(ks, pwd.toCharArray());
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

  public static ChannelHandler makeClientHandler(JSONObject options) {
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

  public static ChannelPipeline mountAsServer(ChannelPipeline pipe, JSONObject options) {
    ChannelHandler ch = makeServerHandler(options);
    if (ch != null) {
      pipe.addFirst("ssl-hs", ch);
    }
    return pipe;
  }

  public static ChannelPipeline mountAsClient(ChannelPipeline pipe, JSONObject options) {
    URL url = (URL) options.opt("targetUrl");
    if (url != null &&  url.getProtocol().equals("https")) {
      pipe.addFirst("ssl-hs", makeClientHandler(options));
    }
    return pipe;
  }

}


