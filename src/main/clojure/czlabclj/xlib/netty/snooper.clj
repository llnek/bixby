;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.netty.snooper

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [notnil? ]]
        [czlabclj.xlib.util.str :only [strim nsb hgl?]]
        [czlabclj.xlib.netty.io])

  (:import  [io.netty.util Attribute AttributeKey CharsetUtil]
            [io.netty.buffer Unpooled]
            [java.util Map$Entry]
            [io.netty.channel ChannelHandlerContext
             Channel ChannelPipeline
             SimpleChannelInboundHandler
             ChannelHandler]
            [io.netty.handler.codec.http HttpHeaders
             HttpHeaders$Names HttpHeaders$Values
             HttpVersion
             HttpContent DefaultFullHttpResponse
             HttpResponseStatus CookieDecoder
             ServerCookieEncoder Cookie
             HttpRequest QueryStringDecoder
             LastHttpContent]
            [com.zotohlab.frwk.netty PipelineConfigurator]
            [io.netty.bootstrap ServerBootstrap]
            [com.zotohlab.frwk.netty NettyFW]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(def ^:private KALIVE (AttributeKey. "keepalive"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeReply ""

  [^ChannelHandlerContext ctx
   ^StringBuilder cookieBuf
   ^StringBuilder buf
   ^HttpContent curObj ]

  (let [res (DftFullRsp (nsb buf))
        clen (-> (.content res)(.readableBytes)) ]
    (-> (.headers res)(.set HttpHeaders$Names/CONTENT_TYPE "text/plain; charset=UTF-8"))
    (-> (.headers res)(.set HttpHeaders$Names/CONTENT_LENGTH (str clen)))
    (-> (.headers res)
        (.set HttpHeaders$Names/CONNECTION
              (if (-> (.attr ctx KALIVE)(.get))
                  HttpHeaders$Values/KEEP_ALIVE
                  HttpHeaders$Values/CLOSE)))
    (let [cs (CookieDecoder/decode (nsb cookieBuf)) ]
      (if (.isEmpty cs)
        (doto (.headers res)
          (.add HttpHeaders$Names/SET_COOKIE
                (ServerCookieEncoder/encode "key1" "value1"))
          (.add HttpHeaders$Names/SET_COOKIE
                (ServerCookieEncoder/encode "key2" "value2")))
        (doseq [^Cookie v (seq cs) ]
          (-> (.headers res)(.add HttpHeaders$Names/SET_COOKIE
                                  (ServerCookieEncoder/encode v))))))
    ;;(.setLength cookieBuf 0)
    ;;(.setLength buf 0)
    (.write ctx res)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleReq ""

  [^ChannelHandlerContext ctx
   ^StringBuilder cookieBuf
   ^StringBuilder buf
   ^HttpRequest req ]

  (let [dc (QueryStringDecoder. (.getUri req))
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

  (let [content (.content msg) ]
    (when (.isReadable content)
      (doto buf
        (.append "CONTENT: ")
        (.append (.toString content CharsetUtil/UTF_8))
        (.append "\r\n")))
    (when (instance? LastHttpContent msg)
      (.append buf "END OF CONTENT\r\n")
      (let [^LastHttpContent trailer msg
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
  [options]

  (let [cookies (StringBuilder.)
        buf (StringBuilder.) ]
    (proxy [SimpleChannelInboundHandler][]
      (channelRead0 [ ctx msg ]
        (cond
          (instance? HttpRequest msg)
          (handleReq ctx cookies buf msg)

          (instance? HttpContent msg)
          (handlec ctx cookies buf msg)

          :else
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooper ""

  ^PipelineConfigurator
  []

  (ReifyHTTPPipe snooperHandler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSnoopHTTPD "Sample Snooper HTTPD."

  [^String host port options]

  (let [^ServerBootstrap bs (InitTCPServer (snooper) options)
        ch (StartServer bs host (int port)) ]
    {:bootstrap bs :channel ch}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private snooper-eof nil)

