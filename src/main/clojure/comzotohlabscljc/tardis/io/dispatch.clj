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

  comzotohlabscljc.tardis.io.dispatch

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (io.netty.channel ChannelFutureListener ChannelFuture
                             ChannelHandler ChannelHandlerContext
                             SimpleChannelInboundHandler))
  (:import (io.netty.buffer ByteBuf ByteBufHolder Unpooled))
  (:import (io.netty.handler.codec.http HttpMessage HttpResponseStatus
                                        HttpHeaders HttpVersion
                                        DefaultFullHttpResponse)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MsgDispatcher ""

  ^ChannelHandler
  [^comzotohcljc.hhh.core.sys.Element co]

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [c m]
      (let [ ^ChannelHandlerContext ctx c
             ^DemuxedMsg msg m
             ch (.channel ctx)
             xs (.payload msg)
             info (.info msg)
             ws (-> info (.get "wsock")(.getAsBoolean))
             evt (cond
                   ws (MakeWsockEvent co ch info xs)
                   :else (IOESReifyEvent co ch info xs))
             ^comzotohcljc.hhh.io.core.WaitEventHolder
             w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
        (.timeoutMillis w (.getAttr ^comzotohcljc.hhh.core.sys.Element co :waitMillis))
        (.hold co w)
        (.dispatch co evt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddMsgDispatcher ""

  ^ChannelPipeline
  [^ChannelPipeline pipe options]

  (let []
    (.addLast pipe "msg-dispatcher" (MsgDispatcher (:emitter options)))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private dispatch-eof nil)

