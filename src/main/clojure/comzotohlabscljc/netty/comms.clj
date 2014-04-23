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

  comzotohlabscljc.netty.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:import (java.lang.reflect Field))
  (:import (java.io IOException ByteArrayOutputStream File OutputStream InputStream))
  (:import (java.util HashMap Properties ArrayList))
  (:import (java.net URI URL InetSocketAddress))
  (:import (org.apache.commons.io IOUtils))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.nio.charset Charset))
  (:import (io.netty.channel.socket SocketChannel))
  (:import (io.netty.channel ChannelHandlerContext Channel
                             ChannelInboundHandlerAdapter
                             SimpleChannelInboundHandler
                             ChannelInitializer ChannelFuture
                             ChannelFutureListener
                             ChannelPipeline ChannelHandler))
  (:import (io.netty.handler.codec.http DefaultHttpResponse HttpResponseStatus
                                        DefaultFullHttpResponse HttpServerCodec
                                        HttpResponse
                                        HttpHeaders HttpVersion))
  (:import (io.netty.handler.stream ChunkedWriteHandler))
  (:import (io.netty.handler.ssl SslHandler))
  (:import (io.netty.buffer ByteBuf))
  (:import (com.zotohlabs.frwk.net NetUtils))
  (:use [comzotohlabscljc.util.core :only [notnil? Try! TryC] ])
  (:use [comzotohlabscljc.util.str :only [strim nsb hgl?] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; map of { int (code) -> HttpResponseStatus }
(def HTTP-CODES
  (let [ to-key (fn [^Field f] (.code ^HttpResponseStatus (.get f nil)))
         fields (:fields (bean HttpResponseStatus))
         kkeys (map to-key fields)
         vvals (map (fn [^Field f] (.get f nil)) fields) ]
    (into {} (map vec (partition 2 (interleave kkeys vvals))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NettyInboundAdaptor ""

  ^ChannelHandler
  [handler options]

  (let []
    (proxy [ChannelInboundHandlerAdapter][]
      (channelRead [c msg]
        (handler c msg options)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NettyInboundHandler ""

  ^ChannelHandler
  [handler options]

  (let []
    (proxy [SimpleChannelInboundHandler][]
      (channelRead0 [c msg]
        (handler c msg options)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NettyChannelInitor ""

  [wrapper options]

  (let []
    (proxy [ChannelInitializer] []
      (initChannel [^SocketChannel ch]
        (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
          (apply wrapper pl options))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseCF "Maybe close the channel."

  [^ChannelFuture cf keepAlive?]

  (when (and (not keepAlive?) (notnil? cf))
    (.addListener cf ChannelFutureListener/CLOSE)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpReply "Make a netty http-response object."

  (^HttpResponse [] (MakeHttpReply 200))

  (^HttpResponse [status]
    (DefaultHttpResponse. HttpVersion/HTTP_1_1
                          (get HTTP-CODES status))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReplyXXX ""

  ([^Channel ch status] (ReplyXXX ch status false))

  ([^Channel ch status keepAlive?]
    (let [ rsp (MakeHttpReply status) ]
      (HttpHeaders/setContentLength rsp 0)
      (-> (.write ch rsp)
          (CloseCF keepAlive?))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WWrite "Write object but no flush."

  ^ChannelFuture
  [ch obj]

  ;; had to do this to work-around reflection warnings :(
  (NetUtils/writeOnly ^Channel ch obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WFlush "Write object and then flush."

  ^ChannelFuture
  [ch obj]

  ;; had to do this to work-around reflection warnings :(
  (NetUtils/wrtFlush ^Channel ch obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeSSL ""

  [^ChannelHandlerContext ctx]

  (notnil? (-> (NetUtils/getPipeline ctx)
               (.get (class SslHandler)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply "Make a netty http-response object."

  (^HttpResponse [status ^ByteBuf obj]
    (DefaultFullHttpResponse. HttpVersion/HTTP_1_1 (get HTTP-CODES status) obj))

  (^HttpResponse [] (MakeFullHttpReply 200))

  (^HttpResponse [status]
    (DefaultFullHttpResponse. HttpVersion/HTTP_1_1 (get HTTP-CODES status))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SendRedirect "Redirect a request."

  [^Channel ch perm ^String targetUrl]

  (let [ rsp (MakeFullHttpReply (if perm 301 307)) ]
    (log/debug "redirecting to -> " targetUrl)
    (HttpHeaders/setHeader rsp  "location" targetUrl)
    (CloseCF (WFlush ch rsp) false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Continue100 "Send back 100-continue."

  [^ChannelHandlerContext ctx]

  (-> (.channel ctx) (WFlush (MakeFullHttpReply 100))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddServerCodec ""

  ^ChannelPipeline
  [^ChannelPipeline pipe]

  (let []
    (.addLast pipe "codec" (HttpServerCodec.))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddWriteChunker ""

  ^ChannelPipeline
  [^ChannelPipeline pipe]

  (let []
    (.addLast pipe "chunker" (ChunkedWriteHandler.))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)

