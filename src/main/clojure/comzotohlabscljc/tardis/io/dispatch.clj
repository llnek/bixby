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

  comzotohlabscljc.tardis.io.dispatch )

(use '[clojure.tools.logging :only [info warn error debug] ])

(import '(io.netty.channel ChannelFutureListener ChannelFuture
  ChannelHandler ChannelHandlerContext
  ChannelInboundHandlerAdapter))

(import '(io.netty.buffer ByteBuf ByteBufHolder Unpooled))

(import '(io.netty.handler.codec.http
  HttpMessage HttpResponseStatus
  HttpHeaders HttpVersion
  DefaultFullHttpResponse))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MsgDispatcher ""

  ^ChannelHandler
  [^comzotohcljc.hhh.core.sys.Element co]

  (proxy [ChannelInboundHandlerAdapter] []
    (channelRead0 [c msg]
      (let [ ^ChannelHandlerContext ctx c
             ch (.channel ctx)
             ^XData xs (:payload msg)
             info (:info msg)
             ^String mt (:method info)
             evt (cond
                   (= "WS" mt) (make-wsock-event co ch info xs)
                   :else (ioes-reify-event co ch info xs))
             ^comzotohcljc.hhh.io.core.WaitEventHolder
             w (make-async-wait-holder (make-netty-trigger ch evt co) evt) ]
        (.timeoutMillis w (.getAttr ^comzotohcljc.hhh.core.sys.Element co :waitMillis))
        (.hold co w)
        (.dispatch co evt)))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddMsgDispatcher ""

  ^ChannelPipeline
  [pipe options]

  (let []
    (.addLast ^ChannelPipeline  pipe "msg-dispatcher" (MsgDispatcher (:emitter options)))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private dispatch-eof nil)

