
  (:import (javax.net.ssl SSLEngine SSLContext))
  (:import (java.net URI URL))
  (:import (io.netty.channel Channel ChannelHandler ChannelPipeline))
  (:import (io.netty.handler.ssl SslHandler))


public enum  SSLEnabler {
;

  public static ChannelHandler enableClientSSL() {
    SSLContext ctx = null;
    SSLEngine eg = null;
    if (ctx != null) {
      eg = ctx.createSSLEngine();
      eg.setUserClientMode(true);
    }
    return eg == null ? null : new SslHandler(eg);
  }

  public static Pipeline enableServerSSL() {
  }

}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddEnableUsrSSL ""

  ^ChannelPipeline
  [pipe options]

  (let [ ssl (= (.getProtocol ^URL (:targetUrl options)) "https") ]
    (when ssl
      (.addLast ^ChannelPipeline pipe "ssl" (EnableUsrSSL options)))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; add SSL
(defn EnableSvrSSL ""

  ^ChannelHandler
  [options]

  (let [ kf (:serverkey options)
         pw (:passwd options)
         ssl (if (nil? kf)
                 nil
                 (MakeSslContext kf pw))
         eg (if (nil? ssl)
                nil
                (doto (.createSSLEngine ssl)
                      (.setUseClientMode false))) ]
    (if (nil? eg) nil (SslHandler. eg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddEnableSvrSSL ""

  ^ChannelPipeline
  [pipe options]

  (let []
    (.addLast ^ChannelPipeline pipe "ssl" (EnableSvrSSL options))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ssl-eof nil)

