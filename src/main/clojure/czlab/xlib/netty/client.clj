;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.netty.client

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:require
    [czlab.xlib.util.io :refer [StreamLimit]])

  (:import
    [com.zotohlab.frwk.netty PipelineConfigurator]
    [io.netty.bootstrap Bootstrap]
    [io.netty.buffer Unpooled]
    [io.netty.channel Channel
     ChannelFuture ChannelOption]
    [io.netty.channel.nio NioEventLoopGroup]
    [io.netty.channel.socket.nio NioSocketChannel]
    [io.netty.handler.codec.http DefaultHttpRequest
     HttpHeaders HttpMethod HttpRequest HttpVersion]
    [io.netty.handler.stream ChunkedStream]
    [io.netty.util AttributeKey]
    [java.io IOException]
    [java.net InetSocketAddress URL]
    [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^AttributeKey URL_KEY  (AttributeKey/valueOf "targetUrl"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitClientSide ""

  ^Bootstrap
  [^PipelineConfigurator cfg  options]

  (doto (Bootstrap.)
    (.group (NioEventLoopGroup.))
    (.channel NioSocketChannel)
    (.option  ChannelOption/SO_KEEPALIVE true)
    (.option ChannelOption/TCP_NODELAY true)
    (.handler  (.configure cfg options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Connect ""

  ^Channel
  [^Bootstrap bs ^URL targetUrl]

  (let [ssl (= "https" (.getProtocol targetUrl))
        host (.getHost targetUrl)
        pnum  (.getPort targetUrl)
        port  (if (< pnum 0) (if ssl 443 80) pnum)
        sock (InetSocketAddress. host (int port))
        ^ChannelFuture
        cf  (-> (.connect bs sock)
                  (.sync))
        ch (.channel cf)]

    (when-not (.isSuccess cf)
      (throw (IOException. "Connect error: " (.cause cf))))

    (-> ch
        (.attr URL_KEY)
        (.set targetUrl))
    ch
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sendit ""

  ^ChannelFuture
  [^Channel ch ^String op ^XData xs options]

  (let [mo (or (:override options) "")
        clen (if (some? xs) (.size xs) 0)
        ^URL url (-> (.attr ch URL_KEY)
                     (.get))
        req (DefaultHttpRequest. HttpVersion/HTTP_1_1
                                 (HttpMethod/valueOf op)
                                 (.toString url)) ]
    (HttpHeaders/setHeader req
                           "Connection"
                           (if (:keep-alive options) "keep-alive" "close"))

    (HttpHeaders/setHeader req "host" (.getHost url))

    (if (> clen  0)
      (do
        (HttpHeaders/setHeader req "content-type"  "application/octet-stream")
        (HttpHeaders/setContentLength req clen))
      ;else
      (HttpHeaders/setContentLength req 0))

    (when-not (empty? mo)
      (HttpHeaders/setHeader req "X-HTTP-Method-Override"  mo))

    (log/debug "Netty client: about to flush out request (headers)")
    (log/debug "Netty client: content has length %s" clen)

    (with-local-vars [cf (.write ch req)]
      (when (> clen 0)
        (->> (if (> clen  (StreamLimit))
               (.writeAndFlush ch (ChunkedStream. (.stream xs)))
               (.writeAndFlush ch (Unpooled/wrappedBuffer (.javaBytes xs))))
             (var-set cf)))
      @cf)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ClntPost ""

  ^ChannelFuture
  [^Channel ch  ^XData data & [options]]

  (sendit ch "POST"  data  options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ClntGet ""

  ^ChannelFuture
  [^Channel ch  & [options]]

  (sendit ch "GET"  nil  options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

