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

  czlabclj.xlib.netty.snooper

  (:require [czlabclj.xlib.util.core :refer [Try! notnil? ]]
            [czlabclj.xlib.util.str :refer [strim nsb hgl?]])

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.netty.filters]
        [czlabclj.xlib.netty.io])

  (:import  [io.netty.util Attribute AttributeKey CharsetUtil]
            [java.util Map$Entry]
            [io.netty.channel ChannelHandlerContext
             Channel ChannelPipeline ChannelHandler]
            [io.netty.handler.codec.http HttpHeaders
             HttpHeaders$Names HttpHeaders$Values
             HttpVersion FullHttpResponse
             HttpContent
             HttpResponseStatus CookieDecoder
             ServerCookieEncoder Cookie
             HttpRequest QueryStringDecoder
             LastHttpContent]
            [com.zotohlab.frwk.netty
             AuxHttpFilter ErrorSinkFilter
             PipelineConfigurator]
            [com.zotohlab.frwk.io XData]
            [io.netty.bootstrap ServerBootstrap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(def ^:private KALIVE (AttributeKey. "keepalive"))
(def ^:private CBUF (AttributeKey. "cookies"))
(def ^:private MBUF (AttributeKey. "msg"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeReply "Reply back a string"

  [^ChannelHandlerContext ctx
   ^Channel ch
   ^HttpContent curObj ]

  (let [^StringBuilder cookieBuf (GetAKey ch CBUF)
        ^StringBuilder buf (GetAKey ch MBUF)
        res (MakeFullHttpReply 200 (nsb buf))
        hds (.headers res)
        clen (-> (.content res)(.readableBytes)) ]
    (-> hds (.set HttpHeaders$Names/CONTENT_LENGTH (str clen)))
    (-> hds (.set HttpHeaders$Names/CONTENT_TYPE
                            "text/plain; charset=UTF-8"))
    (-> hds
        (.set HttpHeaders$Names/CONNECTION
              (if (GetAKey ch KALIVE)
                  HttpHeaders$Values/KEEP_ALIVE
                  HttpHeaders$Values/CLOSE)))
    (let [cs (CookieDecoder/decode (nsb cookieBuf)) ]
      (if (.isEmpty cs)
        (doto hds
          (.add HttpHeaders$Names/SET_COOKIE
                (ServerCookieEncoder/encode "key1" "value1"))
          (.add HttpHeaders$Names/SET_COOKIE
                (ServerCookieEncoder/encode "key2" "value2")))
        (doseq [^Cookie v (seq cs) ]
          (-> hds (.add HttpHeaders$Names/SET_COOKIE
                        (ServerCookieEncoder/encode v))))))
    (.write ctx res)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleReq "Introspect the inbound request"

  [^ChannelHandlerContext ctx
   ^HttpRequest req ]

  (let [dc (QueryStringDecoder. (.getUri req))
        ka (HttpHeaders/isKeepAlive req)
        ch (-> ^ChannelHandlerContext
               ctx (.channel))
        cookieBuf (StringBuilder.)
        buf (StringBuilder.)
        headers (.headers req)
        pms (.parameters dc) ]
    (SetAKey ch CBUF cookieBuf)
    (SetAKey ch KALIVE ka)
    (SetAKey ch MBUF buf)
    (doto buf
          (.append "WELCOME TO THE WILD WILD WEB SERVER\r\n")
          (.append "===================================\r\n")
          (.append "VERSION: ")
          (.append (.getProtocolVersion req))
          (.append "\r\n")
          (.append "HOSTNAME: ")
          (.append (HttpHeaders/getHost req "???"))
          (.append "\r\n")
          (.append "REQUEST_URI: ")
          (.append (.getUri req))
          (.append "\r\n\r\n"))
    (reduce (fn [memo ^String n]
              (doto ^StringBuilder
                memo
                (.append "HEADER: ")
                (.append n)
                (.append " = ")
                (.append (cstr/join "," (.getAll headers n)))
                (.append "\r\n")))
            buf
            (.names headers))
    (.append buf "\r\n")
    (reduce (fn [memo ^Map$Entry en]
              (doto ^StringBuilder
                memo
                (.append "PARAM: ")
                (.append (.getKey en))
                (.append " = ")
                (.append (cstr/join "," (.getValue en)))
                (.append "\r\n")))
            buf
            pms)
    (.append buf "\r\n")
    (.append cookieBuf (nsb (.get headers "cookie")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleCnt "Handle the request content"

  [^ChannelHandlerContext ctx
   ^HttpContent msg]

  (let [ch (-> ^ChannelHandlerContext
                       ctx (.channel))
        ^StringBuilder
        cookieBuf (GetAKey ch CBUF)
        ^StringBuilder
        buf (GetAKey ch MBUF)
        content (.content msg) ]
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
                    (doto ^StringBuilder
                      memo
                      (.append "TRAILING HEADER: ")
                      (.append n)
                      (.append " = ")
                      (.append (cstr/join "," (.getAll thds n)))
                      (.append "\r\n")))
                  buf
                  (.names thds))
          (.append buf "\r\n")))
      (writeReply ctx ch msg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooper ""

  ^PipelineConfigurator
  []

  (ReifyPipeCfgtor
    (fn [^ChannelPipeline pipe options]
      (.addBefore pipe
                  (ErrorSinkFilter/getName)
                  "snooper"
                  (proxy [AuxHttpFilter][]
                    (channelRead0 [c msg]
                      (cond
                        (instance? HttpRequest msg)
                        (handleReq c msg)
                        (instance? HttpContent msg)
                        (handleCnt c msg)
                        :else nil)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSnoopHTTPD "Sample Snooper HTTPD"

  [host port options]

  (let [bs (InitTCPServer (snooper) options)
        ch (StartServer bs host  port) ]
    {:bootstrap bs :channel ch}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

