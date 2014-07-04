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

  cmzlabsclj.nucleus.netty.request

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

;;(defn reifyRequestDecoder
(defn- reifyRequestDecoder

  ^AuxHttpDecoder
  []

  (let []
    (proxy [RequestDecoder][]
      (handleInboundMsg [c obj]
        (let [ ^ChannelHandlerContext ctx c
               ^HttpMessage msg obj
               ch (.channel ctx)
               ^JsonObject info (NettyFW/getAttr ch (NettyFW/MSGINFO_KEY))
               isc (-> info (.get "is-chunked")(.getAsBoolean))
               mtd (-> info (.get "method")(.getAsString))
               clen (-> info (.get "clen")(.getAsInt)) ]
          (NettyFW/setAttr  ctx  (NettyFW/CBUF_KEY (Unpooled/compositeBuffer 1024)))
          (NettyFW/setAttr ctx (NettyFW/XDATA_KEY (XData.)))
          (proxy-super handleMsgChunk ctx msg)))

      (channelRead0 [c obj]
        (let [ ^ChannelHandlerContext ctx c
               ^Object msg obj ]
          (log/debug "channel-read0 called with msg " (type msg))
          (cond
            (instance? HttpRequest msg)
            (.handleInboundMsg this ctx ^HttpMessage msg)

            (instance? HttpContent msg)
            (proxy-super handleMsgChunk ctx ^HttpContent msg)

            :else
            (do
              (log/error "unexpected message type " (type msg))
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))))))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the decoder is annotated as sharable.  this acts like the singleton.
(def *HTTP-REQ-DECODER* (reifyRequestDecoder))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private request-eof nil)

