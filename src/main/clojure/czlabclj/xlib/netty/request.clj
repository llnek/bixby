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

  czlabclj.xlib.netty.request

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core
        :only
        [notnil? Try! TryC SafeGetJsonString
         SafeGetJsonBool SafeGetJsonInt]]
        [czlabclj.xlib.util.str :only [strim nsb hgl?]])

  (:import  [io.netty.handler.codec.http HttpMessage HttpContent HttpRequest]
            [io.netty.buffer Unpooled]
            [io.netty.channel ChannelHandlerContext
             ChannelPipeline Channel ChannelHandler]
            [io.netty.bootstrap ServerBootstrap]
            [io.netty.util ReferenceCountUtil]
            [com.zotohlab.frwk.netty AuxHttpFilter
             RequestFilter PipelineConfigurator]
            [com.zotohlab.frwk.netty NettyFW]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyRequestFilter

  ^AuxHttpFilter
  []

  (proxy [RequestFilter][]
    (handleInboundMsg [c obj]
      (let [^ChannelHandlerContext ctx c
            ^HttpMessage msg obj
            ch (.channel ctx)
            info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY)
            isc (:is-chunked info)
            mtd (:method info)
            clen (:clen info) ]
        (NettyFW/setAttr ctx NettyFW/CBUF_KEY (Unpooled/compositeBuffer 1024))
        (NettyFW/setAttr ctx NettyFW/XDATA_KEY (XData.))
        (.handleMsgChunk ^RequestFilter this ctx msg)))

    (channelRead0 [c obj]
      (let [^ChannelHandlerContext ctx c
            ^Object msg obj ]
        (log/debug "Channel-read0 called with msg " (type msg))
        (cond
          (instance? HttpRequest msg)
          (.handleInboundMsg ^RequestFilter this ctx msg)

          (instance? HttpContent msg)
          (.handleMsgChunk ^RequestFilter this ctx msg)

          :else
          (do
            (log/error "Unexpected message type " (type msg))
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

