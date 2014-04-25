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

  comzotohlabscljc.netty.exception

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:import (io.netty.buffer Unpooled))
  (:import (io.netty.channel ChannelHandler ChannelHandlerContext
                             ChannelFutureListener ChannelFuture
                             ChannelPipeline
                             ChannelInboundHandlerAdapter))
  (:import (io.netty.handler.codec.http HttpMessage HttpResponseStatus
                                        HttpHeaders HttpVersion
                                        DefaultFullHttpResponse))
  (:use [comzotohlabscljc.netty.comms :only [ReplyXXX] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExceptionCatcher ""

  ^ChannelHandler
  []

  (proxy [ChannelInboundHandlerAdapter] []
    (exceptionCaught [c err]
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx) ]
        (ReplyXXX ch 500)
      ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddExceptionCatcher ""

  ^ChannelPipeline
  [pipe options]

  (let []
    (.addLast ^ChannelPipeline  pipe "error-handler" (ExceptionCatcher))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private exception-eof nil)

