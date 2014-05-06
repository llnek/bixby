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

  comzotohlabscljc.netty.filesvr

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.files :only [SaveFile GetFile] ])
  (:use [comzotohlabscljc.util.core :only [notnil? Try! TryC] ])
  (:use [comzotohlabscljc.util.str :only [strim nsb hgl?] ])
  (:import (java.io IOException File))
  (:import (io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             SimpleChannelInboundHandler
                             ChannelFuture ChannelHandler ))
  (:import (io.netty.handler.codec.http HttpHeaders HttpMessage HttpResponse HttpServerCodec))
  (:import (io.netty.handler.stream ChunkedStream ChunkedWriteHandler ))
  (:import (com.zotohlabs.frwk.netty ServerSide PipelineConfigurator
                                     SSLServerHShake DemuxedMsg
                                     HttpDemux ErrorCatcher))
  (:import (com.zotohlabs.frwk.netty NettyFW))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.google.gson JsonObject JsonElement)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; file handlers
(defn- replyGetVFile ""

  [^Channel ch ^JsonObject info ^XData xdata]

  (let [ kalive (and (notnil? info)
                     (-> info (.get "keep-alive")(.getAsBoolean)))
         res (NettyFW/makeHttpReply 200)
         clen (.size xdata) ]
    (HttpHeaders/setHeader res "content-type" "application/octet-stream")
    (HttpHeaders/setContentLength res clen)
    (HttpHeaders/setTransferEncodingChunked res)
    (NettyFW/writeOnly ch res)
    (NettyFW/closeCF (NettyFW/writeFlush ch (ChunkedStream. (.stream xdata))) kalive)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- filePutter ""

  [^File vdir ^Channel ch ^JsonObject info ^String fname ^XData xdata]

  (try
    (SaveFile vdir fname xdata)
    (NettyFW/replyXXX ch 200)
    (catch Throwable e#
      (NettyFW/replyXXX ch 500))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileGetter ""

  [^File vdir ^Channel ch ^JsonObject info ^String fname]

  (let [ xdata (GetFile vdir fname) ]
    (if (.hasContent xdata)
      (replyGetVFile ch info xdata)
      (NettyFW/replyXXX ch 204))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileHandler ""

  ^ChannelHandler
  [^JsonObject options]

  (proxy [SimpleChannelInboundHandler][]
    (channelRead0 [c m]
      (let [ vdir (-> options (.get "vdir")(.getAsString))
             ^ChannelHandlerContext ctx c
             ^DemuxedMsg msg m
             xs (.payload msg)
             info (.info msg)
             ch (.channel ctx)
             mtd (-> info (.get "method")(.getAsString))
             uri (-> info (.get "uri")(.getAsString))
             pos (.lastIndexOf uri (int \/))
             p (if (< pos 0) uri (.substring uri (inc pos))) ]
        (log/debug "Method = " mtd ", Uri = " uri ", File = " p)
        (cond
          (or (= mtd "POST")(= mtd "PUT")) (filePutter vdir ch info p xs)
          (or (= mtd "GET")(= mtd "HEAD")) (fileGetter vdir ch info p)
          :else (NettyFW/replyXXX ch 405))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileCfgtor ""

  ^PipelineConfigurator
  []

  (proxy [PipelineConfigurator][]
    (assemble [p o]
      (let [ ^ChannelPipeline pipe p
             ^JsonObject options o
             ssl (SSLServerHShake/getInstance options) ]
        (when-not (nil? ssl)(.addLast pipe "ssl" ssl))
        (doto pipe
          (.addLast "codec" (HttpServerCodec.))
          (HttpDemux/addLast )
          (.addLast "chunker" (ChunkedWriteHandler.))
          (.addLast "filer" (fileHandler options))
          (ErrorCatcher/addLast ))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MakeMemFileServer "A file server which can get/put files."

  ;; returns netty objects if you want to do clean up
  [^String host port ^JsonObject options]

  (let [ ^ServerBootstrap bs (ServerSide/initServerSide  (fileCfgtor) options)
         ch (ServerSide/start bs host port) ]
    { :bootstrap bs :channel ch }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private filesvr-eof nil)

