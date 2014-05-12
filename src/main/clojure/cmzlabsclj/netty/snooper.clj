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

  cmzlabsclj.netty.snooper

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.util.core :only [notnil? Try! TryC] ])
  (:use [cmzlabsclj.util.str :only [strim nsb hgl?] ])
  (:import (java.io IOException File))
  (:import (io.netty.buffer Unpooled))
  (:import (io.netty.util Attribute AttributeKey CharsetUtil))
  (:import (java.util Map$Entry))
  (:import (io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             SimpleChannelInboundHandler
                             ChannelFuture ChannelHandler ))
  (:import (io.netty.handler.codec.http HttpHeaders HttpMessage  HttpVersion
                                        HttpContent DefaultFullHttpResponse
                                        HttpResponseStatus CookieDecoder
                                        ServerCookieEncoder Cookie
                                        HttpRequest QueryStringDecoder
                                        LastHttpContent
                                        HttpResponse HttpServerCodec))
  (:import (io.netty.handler.stream ChunkedStream ChunkedWriteHandler ))
  (:import (com.zotohlabs.frwk.netty ServerSide PipelineConfigurator
                                     SSLServerHShake DemuxedMsg
                                     Expect100
                                     HttpDemux ErrorCatcher))
  (:import (com.zotohlabs.frwk.netty NettyFW))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.google.gson JsonObject JsonElement)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeReply ""

  [^ChannelHandlerContext ctx
   ^StringBuilder cookieBuf
   ^StringBuilder buf
   ^HttpContent curObj ]

  (let [ ka (-> (.attr ctx (AttributeKey. "keepalive"))(.get))
         response (DefaultFullHttpResponse.
                    (HttpVersion/HTTP_1_1)
                    (HttpResponseStatus/OK)
                    (Unpooled/copiedBuffer (nsb buf) (CharsetUtil/UTF_8)))
         clen (-> response (.content)(.readableBytes)) ]

    (-> response (.headers)(.set "content-type" "text/plain; charset=UTF-8"))
    (-> response (.headers)(.set "content-length" (str clen)))

    (when ka (-> response (.headers)(.set "connection" "keep-alive")))

    (let [ cs (CookieDecoder/decode (nsb cookieBuf)) ]
      (if (.isEmpty cs)
        (do
          (-> response (.headers)(.add "set-cookie" (ServerCookieEncoder/encode "key1" "value1")))
          (-> response (.headers)(.add "set-cookie" (ServerCookieEncoder/encode "key2" "value2"))))
        (doseq [ v (seq cs) ]
          (-> response (.headers)(.add "set-cookie" (ServerCookieEncoder/encode ^Cookie v))))))

    (.setLength cookieBuf 0)
    (.setLength buf 0)
    (.write ctx response)

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleReq ""

  [^ChannelHandlerContext ctx
   ^StringBuilder cookieBuf
   ^StringBuilder buf
   ^HttpRequest req ]

  (let [ dc (QueryStringDecoder. (.getUri req))
         ka (HttpHeaders/isKeepAlive req)
         headers (.headers req)
         pms (.parameters dc) ]
    (-> (.attr ctx (AttributeKey. "keepalive"))(.set ka))
    (doto buf
          (.append "WELCOME TO THE WILD WILD WEB SERVER\r\n")
          (.append "===================================\r\n")
          (.append "VERSION: ")
          (.append (.getProtocolVersion req))
          (.append "\r\n")
          (.append "HOSTNAME: ")
          (.append (HttpHeaders/getHost req "unknown"))
          (.append "\r\n")
          (.append "REQUEST_URI: ")
          (.append (.getUri req))
          (.append "\r\n\r\n"))
    (reduce (fn [memo ^String n]
              (doto buf
                (.append "HEADER: ")
                (.append n)
                (.append " = ")
                (.append (cstr/join "," (.getAll headers n)))
                (.append "\r\n")))
            nil
            (.names headers))
    (.append buf "\r\n")
    (reduce (fn [memo ^Map$Entry en]
              (doto buf
                (.append "PARAM: ")
                (.append (.getKey en))
                (.append " = ")
                (.append (cstr/join "," (.getValue en)))
                (.append "\r\n")))
            nil
            pms)
    (.append buf "\r\n")
    (.append cookieBuf (nsb (.get headers "cookie")))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handlec ""

  [^ChannelHandlerContext ctx
   ^StringBuilder cookieBuf
   ^StringBuilder buf
   ^HttpContent msg]

  (let [ content (.content msg) ]
    (when (.isReadable content)
      (doto buf
        (.append "CONTENT: ")
        (.append (.toString content (CharsetUtil/UTF_8)))
        (.append "\r\n")))
    (when (instance? LastHttpContent msg)
      (.append buf "END OF CONTENT\r\n")
      (let [ ^LastHttpContent trailer msg
             thds (.trailingHeaders trailer) ]
        (when-not (.isEmpty thds)
          (.append buf "\r\n")
          (reduce (fn [memo ^String n]
                    (doto buf
                      (.append "TRAILING HEADER: ")
                      (.append n)
                      (.append " = ")
                      (.append (cstr/join "," (.getAll thds n)))
                      (.append "\r\n")))
                  nil
                  (.names thds))
          (.append buf "\r\n")))
      (writeReply ctx cookieBuf buf msg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooperHandler ""

  ^ChannelHandler
  []

  (let [ cookies (StringBuilder.)
         buf (StringBuilder.) ]
    (proxy [SimpleChannelInboundHandler][]
      (channelRead0 [ ctx msg ]
        (cond
          (instance? HttpRequest msg) (handleReq ctx cookies buf msg)
          (instance? HttpContent msg) (handlec ctx cookies buf msg)
          :else nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooper ""

  ^PipelineConfigurator
  []

  (proxy [PipelineConfigurator][]
    (assemble [p o]
      (let [ ^ChannelPipeline pipe p
             ^JsonObject options o
             ssl (SSLServerHShake/getInstance options) ]
        (when-not (nil? ssl)(.addLast pipe "ssl" ssl))
        (doto pipe
          (.addLast "codec" (HttpServerCodec.))
          (Expect100/addLast )
          (.addLast "chunker" (ChunkedWriteHandler.))
          (.addLast "snooper" (snooperHandler))
          (ErrorCatcher/addLast))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sample Snooper HTTPD
(defn MakeSnoopHTTPD ""

  [^String host port ^JsonObject options]

  (let [ ^ServerBootstrap bs (ServerSide/initServerSide (snooper) options)
         ch (ServerSide/start bs host port) ]
    { :bootstrap bs :channel ch }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private snooper-eof nil)

