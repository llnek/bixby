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

  czlab.xlib.netty.snooper

  (:require
    [czlab.xlib.util.str :refer [strim hgl?]]
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.xlib.netty.filters]
        [czlab.xlib.netty.io])

  (:import
    [io.netty.util Attribute AttributeKey CharsetUtil]
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
(defn- writeReply

  "Reply back a string"

  [^ChannelHandlerContext ctx
   ^Channel ch
   ^HttpContent curObj ]

  (let [^StringBuilder cookieBuf (GetAKey ch CBUF)
        ^StringBuilder buf (GetAKey ch MBUF)
        res (FullHttpReply* 200 (str buf))
        cs (CookieDecoder/decode (str cookieBuf))
        hds (.headers res)
        clen (-> (.content res)
                 (.readableBytes)) ]
    (.set hds "Content-Length" (str clen))
    (.set hds "Content-Type"
              "text/plain; charset=UTF-8")
    (.set hds "Connection"
          (if (GetAKey ch KALIVE) "keep-alive" "close"))
    (if (.isEmpty cs)
      (doto hds
        (.add "Set-Cookie"
              (ServerCookieEncoder/encode "key1" "value1"))
        (.add "Set-Cookie"
              (ServerCookieEncoder/encode "key2" "value2")))
      (doseq [^Cookie v (seq cs) ]
        (.add hds "Set-Cookie"
                  (ServerCookieEncoder/encode v))))
    (.write ctx res)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleReq

  "Introspect the inbound request"

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
                (.append (cs/join "," (.getAll headers n)))
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
                (.append (cs/join "," (.getValue en)))
                (.append "\r\n")))
            buf
            pms)
    (.append buf "\r\n")
    (.append cookieBuf (str (.get headers "cookie")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleCnt

  "Handle the request content"

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
                      (.append (cs/join "," (.getAll thds n)))
                      (.append "\r\n")))
                  buf
                  (.names thds))
          (.append buf "\r\n")))
      (writeReply ctx ch msg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooper ""

  ^PipelineConfigurator
  []

  (ReifyPipeCfgtor
    #(->>
       (proxy [AuxHttpFilter][]
         (channelRead0 [c msg]
           (cond
             (instance? HttpRequest msg)
             (handleReq c msg)
             (instance? HttpContent msg)
             (handleCnt c msg)
             :else nil)))
       (.addBefore
         ^ChannelPipeline
         %1
         ErrorSinkFilter/NAME "snooper"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SnoopHTTPD*

  "Sample Snooper HTTPD"

  [host port options]

  (let [bs (InitTCPServer (snooper) options)
        ch (StartServer bs host  port) ]
    {:bootstrap bs :channel ch}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

