;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.netty.client

  (:require
    [czlab.xlib.util.core :refer [trap! ex*]]
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs]
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
(def ^:private ^AttributeKey URL_KEY  (AttributeKey/valueOf "url"))

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
    (.handler  (.configure cfg options))))

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
      (trap! IOException "Connect error: " (.cause cf)))

    (-> ch
        (.attr URL_KEY)
        (.set targetUrl))
    ch))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sendit ""

  ^ChannelFuture
  [^Channel ch ^String op ^XData xs options]

  (let [clen (if (some? xs) (.size xs) 0)
        options (or options {})
        mo (or (:override options) "")
        ^URL url (-> (.attr ch URL_KEY)
                     (.get))
        req (DefaultHttpRequest. HttpVersion/HTTP_1_1
                                 (HttpMethod/valueOf op)
                                 (.toString url)) ]

    (HttpHeaders/setHeader req "host" (.getHost url))

    (HttpHeaders/setHeader
      req
      "Connection"
      (if (:keep-alive options) "keep-alive" "close"))

    (if (> clen  0)
      (do
        (HttpHeaders/setHeader req
                               "content-type"
                               "application/octet-stream")
        (HttpHeaders/setContentLength req clen))
      ;else
      (HttpHeaders/setContentLength req 0))

    (when-not (empty? mo)
      (HttpHeaders/setHeader req "X-HTTP-Method-Override"  mo))

    (log/debug "Netty client: about to flush out request (headers)")
    (log/debug "Netty client: content has length %s" clen)

    (with-local-vars [cf (.write ch req)]
      (when (> clen 0)
        (->>
          (if (> clen  (StreamLimit))
            (.writeAndFlush ch (ChunkedStream. (.stream xs)))
            (.writeAndFlush ch (Unpooled/wrappedBuffer (.javaBytes xs))))
          (var-set cf)))
      @cf)))

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

