;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.netty.client

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:import [java.net URL URI InetSocketAddress])
  (:import [java.io IOException])
  (:import (io.netty.buffer Unpooled))
  (:import (io.netty.channel.nio NioEventLoopGroup))
  (:import (io.netty.bootstrap Bootstrap))
  (:import (io.netty.handler.stream ChunkedStream))
  (:import (io.netty.channel ChannelHandlerContext Channel ChannelOption
                             ChannelFutureListener ChannelFuture
                             ChannelPipeline ChannelHandler ))
  (:import (io.netty.handler.codec.http HttpMessage HttpResponseStatus
                                        HttpMethod
                                        HttpHeaders HttpVersion
                                        DefaultHttpRequest))
  (:import (com.zotohlabs.frwk.net NetUtils))
  (:import (com.zotohlabs.frwk.io XData))
  (:use [comzotohlabscljc.netty.comms])
  (:use [comzotohlabscljc.util.core :only [notnil? Try! TryC] ])
  (:use [comzotohlabscljc.util.str :only [strim nsb hgl?] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootstrapNettyClient

  ;; map
  [initor options]

  (let [ g (NioEventLoopGroup.)
         bs (doto (Bootstrap.)
                  (.group g)
                  (.channel io.netty.channel.socket.nio.NioSocketChannel)
                  (.option ChannelOption/TCP_NODELAY true)
                  (.option ChannelOption/SO_KEEPALIVE true))
         opts (:netty options) ]
    (doseq [ [k v] (seq opts) ]
      (.option bs k v))
    (.handler bs (initor options))
    { :bootstrap bs }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConnectNettyClient "Start Netty 4.x client."

  [^URL targetUrl netty]

  (let [ ssl (= "https" (.getProtocol targetUrl))
         pnum (.getPort targetUrl)
         port (if (< pnum 0) (if ssl 443 80) pnum)
         host (.getHost targetUrl)
         sock (InetSocketAddress. host (int port))
         ^Bootstrap boot (:bootstrap netty)
         ^ChannelFuture cf (-> boot
                               (.connect sock)
                               (.sync)) ]
    (when-not (.isSuccess cf)
      (throw (if-let [ eee (.cause cf) ]
                     eee
                     (IOException. (str "Connect failed: " targetUrl)))))
    (merge netty { :channel (.channel cf) })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sendHttpClient ""

  [netty ^String verb ^XData xdata options]

  (let [ clen (if (nil? xdata) 0 (.size xdata))
         ^URL targetUrl (:targetUrl options)
         ^Channel ch (:channel netty)
         mo (:override options)
         md (if (> clen 0)
              (if (hgl? mo) "POST")
              (if (hgl? mo) mo "GET"))
         mt (if-let [mo mo] mo md)
         req (DefaultHttpRequest. HttpVersion/HTTP_1_1
                                  (HttpMethod/valueOf mt)
                                  (nsb targetUrl))
         presend (:presend options) ]

    (HttpHeaders/setHeader req "Connection" (if (:keep-alive options) "keep-alive" "close"))
    (HttpHeaders/setHeader req "host" (.getHost targetUrl))
    (when (fn? presend) (presend ch req))

    (let [ ct (HttpHeaders/getHeader req "content-type") ]
      (when (and (cstr/blank? ct)
                 (> clen 0))
        (HttpHeaders/setHeader req "content-type" "application/octet-stream")))

    (HttpHeaders/setContentLength req clen)
    (log/debug "Netty client: about to flush out request (headers)")
    (log/debug "Netty client: content has length " clen)
    (with-local-vars [wf nil]
      (var-set wf (WWrite ch req))
      (if (> clen 0)
        (var-set wf (if (> clen (com.zotohlabs.frwk.io.IOUtils/streamLimit))
                      (WFlush ch (ChunkedStream. (.stream xdata)))
                      (WFlush ch (Unpooled/wrappedBuffer (.javaBytes xdata)))))
        (NetUtils/flush ch))
      (CloseCF @wf (:keep-alive options) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async HTTP Post
(defn AsyncPost "Async HTTP Post"

  ([ netty ^XData xdata options ]
   (sendHttpClient "POST" netty xdata options))

  ([ netty ^XData xdata ]
   (AsyncPost netty xdata {})) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async HTTP Get
(defn AsyncGet "Async HTTP GET"

  ([ netty ] (AsyncGet netty {}))

  ([ netty options ]
   (sendHttpClient "GET" netty nil options)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private client-eof nil)

