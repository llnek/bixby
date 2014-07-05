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

  cmzlabsclj.nucleus.netty.io

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [ThrowIOE MakeMMap notnil? ] ]
        [cmzlabsclj.nucleus.util.str :only [strim nsb hgl?] ]
        [cmzlabsclj.nucleus.netty.request]
        [cmzlabsclj.nucleus.netty.form])
  (:import [io.netty.channel ChannelHandlerContext ChannelPipeline
                             ChannelInboundHandlerAdapter
                             Channel ChannelHandler]
           [org.apache.commons.lang3 StringUtils]
           [io.netty.handler.codec.http HttpHeaders HttpMessage  
                                        HttpContent 
                                        HttpRequest 
                                        HttpRequestDecoder
                                        HttpResponseEncoder]
          [io.netty.bootstrap ServerBootstrap]
          [io.netty.util ReferenceCountUtil]
          [io.netty.handler.codec.http.websocketx WebSocketServerProtocolHandler]
          [io.netty.handler.stream ChunkedWriteHandler]
          [com.zotohlab.frwk.netty ServerSide PipelineConfigurator
                                     SSLServerHShake RequestDecoder
                                     Expect100 AuxHttpDecoder
                                     ErrorCatcher]
          [com.zotohlab.frwk.netty NettyFW]
          [com.zotohlab.frwk.io XData]
          [com.google.gson JsonObject ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isFormPost ""

  [^HttpMessage msg ^String method]

  (let [ct (-> (HttpHeaders/getHeader msg "content-type")
               nsb
               strim
               cstr/lower-case) ]
    ;; multipart form
    (and (or (= "POST" method)(= "PUT" method)(= "PATCH" method))
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doDemux ""

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^cmzlabsclj.nucleus.util.core.MutableMap impl]

  (let [info (NettyFW/extractMsgInfo req)
        ch (.channel ctx)
        mt (-> info (.get "method")(.getAsString))
        uri (-> info (.get "uri")(.getAsString)) ]
    (log/debug "first level demux of message\n{}" req)
    (log/debug info)
    (NettyFW/setAttr ctx NettyFW/MSGINFO_KEY info)
    (NettyFW/setAttr ch NettyFW/MSGINFO_KEY info)
    (.setf! impl :delegate nil)
    (if (.startsWith uri "/favicon.") ;; ignore this crap
      (do
        (NettyFW/replyXXX ch 404)
        (.setf! impl :ignore true))
      (do
        (Expect100/handle100 ctx req)
        (if (isFormPost ctx mt)
          (do
            (.setf! impl :delegate (ReifyFormPostDecoderSingleton))
            (.addProperty info "formpost" true))
          (.setf! impl :delegate (ReifyRequestDecoderSingleton)))))
    (when-let [ ^AuxHttpDecoder d (.getf impl :delegate) ]
      (.channelReadXXX d ctx req))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyHttpHandler ""

  ^ChannelHandler
  []

  (let [impl (MakeMMap)]
    (.setf! impl :delegate nil)
    (.setf! impl :ignore false)
    (proxy [AuxHttpDecoder][]
      (channelRead0 [ctx msg]
        (let [ ^AuxHttpDecoder d (.getf impl :delegate)
               e (.getf impl :ignore) ]
          (cond
            (instance? HttpRequest msg)
            (doDemux ctx msg impl)

            (notnil? d)
            (.channelReadXXX d ctx msg)

            (true? e)
            nil ;; ignore

            :else
            (ThrowIOE "Fatal error while reading http message.")))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isWEBSock ""

  [^HttpRequest req]

  (let [ws (-> (HttpHeaders/getHeader req "upgrade")
               nsb
               strim
               cstr/lower-case)
        mo (-> (HttpHeaders/getHeader req "X-HTTP-Method-Override")
               nsb
               strim) ]
    (and (= "websocket" ws)
         (= "GET" (if (StringUtils/isNotEmpty mo)
                    mo
                    (-> req (.getMethod)(.name)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeDemuxer ""

  ^ChannelHandler
  [^String uri]

  (proxy [ChannelInboundHandlerAdapter][]
    (channelRead [ c obj]
      (let [^ChannelHandlerContext ctx c
            ^Object msg obj
            pipe (.pipeline ctx)
            ch (.channel ctx) ]
        (cond
          (and (instance? HttpRequest msg)
               (isWEBSock msg))
          (do
            (.addAfter pipe
                       "HttpResponseEncoder"
                       "WebSocketServerProtocolHandler"
                       (WebSocketServerProtocolHandler. uri)))

          :else
          (do
            (.addAfter pipe
                       "HttpDemuxer"
                       "ReifyHttpHandler"
                       (reifyHttpHandler))))
        (.fireChannelRead pipe msg)
        (.remove pipe ^ChannelHandler this)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpPipline ""

  ^PipelineConfigurator
  [cfg]

  (proxy [PipelineConfigurator][]
    (assemble [pl options]
      (let [ssl (SSLServerHShake/getInstance ^JsonObject options)
            ^ChannelPipeline pipe pl]
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "HttpDemuxer" (makeDemuxer (:uri cfg)))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
          (.addLast ^String (:name cfg) 
                    ^ChannelHandler (apply (:handler cfg) options))
          (ErrorCatcher/addLast))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyWEBSockPipe ""

  ^PipelineConfigurator
  [^String websockUri ^String yourHandlerName yourHandlerFn]

  (makeHttpPipline { :uri websockUri :name yourHandlerName :handler yourHandlerFn }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyHTTPPipe ""

  ^PipelineConfigurator
  [^String yourHandlerName yourHandlerFn]

  (makeHttpPipline { :uri "" :name yourHandlerName :handler yourHandlerFn }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private io-eof nil)

