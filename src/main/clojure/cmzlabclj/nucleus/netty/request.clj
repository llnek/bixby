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

  cmzlabclj.nucleus.netty.request

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [notnil? Try! TryC] ]
        [cmzlabclj.nucleus.util.str :only [strim nsb hgl?] ])

  (:import [io.netty.buffer Unpooled]
           [io.netty.channel ChannelHandlerContext ChannelPipeline
                             Channel ChannelHandler]
           [io.netty.handler.codec.http HttpMessage HttpContent HttpRequest]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.util ReferenceCountUtil]
           [com.zotohlab.frwk.netty AuxHttpFilter RequestFilter
                                    PipelineConfigurator]
           [com.zotohlab.frwk.netty NettyFW]
           [com.zotohlab.frwk.io XData]
           [com.google.gson JsonObject]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;(defn reifyRequestFilter
(defn- reifyRequestFilter

  ^AuxHttpFilter
  []

  (proxy [RequestFilter][]
    (handleInboundMsg [c obj]
      (let [^ChannelHandlerContext ctx c
            ^HttpMessage msg obj
            ch (.channel ctx)
            ^JsonObject info (NettyFW/getAttr ch NettyFW/MSGINFO_KEY)
            isc (-> info (.get "is-chunked")(.getAsBoolean))
            mtd (-> info (.get "method")(.getAsString))
            clen (-> info (.get "clen")(.getAsInt)) ]
        (NettyFW/setAttr ctx NettyFW/CBUF_KEY (Unpooled/compositeBuffer 1024))
        (NettyFW/setAttr ctx NettyFW/XDATA_KEY (XData.))
        (.handleMsgChunk ^RequestFilter this ctx msg)))

    (channelRead0 [c obj]
      (let [^ChannelHandlerContext ctx c
            ^Object msg obj ]
        (log/debug "channel-read0 called with msg " (type msg))
        (cond
          (instance? HttpRequest msg)
          (.handleInboundMsg ^RequestFilter this ctx msg)

          (instance? HttpContent msg)
          (.handleMsgChunk ^RequestFilter this ctx msg)

          :else
          (do
            (log/error "unexpected message type " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the decoder is annotated as sharable.  this acts like the singleton.
(def ^:private HTTP-REQ-FILTER (reifyRequestFilter))

(defn ReifyRequestFilterSingleton ""

  ^ChannelHandler
  []

  HTTP-REQ-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private request-eof nil)

