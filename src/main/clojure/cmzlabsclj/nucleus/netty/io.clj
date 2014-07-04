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

  cmzlabsclj.nucleus.netty.initzers

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [notnil? Try! TryC] ]
        [cmzlabsclj.nucleus.util.str :only [strim nsb hgl?] ])
  (:import [java.io IOException File]
           [io.netty.buffer Unpooled]
           [io.netty.util Attribute AttributeKey CharsetUtil]
           [java.util Map$Entry]
           [io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             SimpleChannelInboundHandler
                             ChannelFuture ChannelHandler]
           [io.netty.handler.codec.http HttpHeaders HttpMessage  HttpVersion
                                        HttpContent DefaultFullHttpResponse
                                        HttpResponseStatus CookieDecoder
                                        ServerCookieEncoder Cookie
                                        HttpRequest QueryStringDecoder
                                        LastHttpContent HttpRequestDecoder
                                        HttpResponse HttpResponseEncoder]
          [io.netty.bootstrap ServerBootstrap]
          [io.netty.handler.stream ChunkedStream ChunkedWriteHandler]
          [com.zotohlab.frwk.netty ServerSide PipelineConfigurator
                                     SSLServerHShake DemuxedMsg
                                     Expect100
                                     HttpDemux ErrorCatcher]
          [com.zotohlab.frwk.netty NettyFW]
          [com.zotohlab.frwk.io XData]
          [com.google.gson JsonObject JsonElement] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(defn- isFormPost ""

  [^HttpMessage msg ^String method]

  (let [ ct (cstr/lower-case (nsb (HttpHeaders/getHeader msg "content-type"))) ]
    ;; multipart form
    (and (or (= "POST" method)(= "PUT" method)(= "PATCH" method))
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  ))


(defn- doDemux ""

  [^ChannelHandlerContext ctx ^HttpRequest req impl]

  (let [ info (NettyFW/extractMsgInfo req)
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
            (.setf! impl :delegate (FormPostCodec/getInstance))
            (.addProperty info "formpost" true))
          (.setf! impl :delegate (RequestCodec/getInstance)))))
    (when-let [ ^AuxHttpDecoder d (.getf impl :delegate) ]
      (.channelReadXXX d ctx req))
  ))

(defn ReifyHttpDemux ""

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
(defn- makeHttpPipline ""

  ^PipelineConfigurator
  [cfg]

  (proxy [PipelineConfigurator][]
    (assemble [pl options]
      (let [ssl (SSLServerHShake/getInstance ^JsonObject options)
            ^ChannelPipeline pipe pl]
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "HttpDemuxer" (HttpDemuxer. (:uri cfg)))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
          (.addLast (:name cfg) (apply (:handler cfg) options))
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
(def ^:private initzers-eof nil)

