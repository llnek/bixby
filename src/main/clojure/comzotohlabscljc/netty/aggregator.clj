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

  comzotohlabscljc.netty.adder )

(use '[clojure.tools.logging :only [info warn error debug] ])

(import '(io.netty.buffer Unpooled ByteBuf))
(import '(io.netty.channel ChannelFuture
ChannelFutureListener
ChannelHandler
ChannelHandlerContext
ChannelPipeline))
(import '(io.netty.handler.codec DecoderResult
MessageToMessageDecoder
ooLongFrameException))
(import '(java.util List))
(import '(io.netty.handler.codec.http HttpHeaders
DefaultFullHttpResponse  HttpResponseStatus
HttpVersion))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PayloadAggregator ""

  ^ChannelHandler
  [options]

  (proxy [MessageToMessageDecoder] []
    (decode [ ctx msg out ]
      (cond
        (instance? HttpMessage msg)
        ()

        (instance? HttpContent msg)
        ()

        (:else nil))




