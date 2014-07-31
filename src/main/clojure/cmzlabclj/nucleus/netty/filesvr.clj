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

  cmzlabclj.nucleus.netty.filesvr

  (:gen-class)

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.files :only [SaveFile GetFile] ]
        [cmzlabclj.nucleus.util.core :only [juid notnil? ] ]
        [cmzlabclj.nucleus.util.str :only [strim nsb hgl?] ]
        [cmzlabclj.nucleus.netty.io])

  (:import [java.io IOException File]
           [io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             SimpleChannelInboundHandler
                             ChannelHandler]
           [io.netty.handler.codec.http HttpHeaders LastHttpContent]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.handler.stream ChunkedStream]
           [com.zotohlab.frwk.netty PipelineConfigurator
                                    DemuxedMsg]
           [com.zotohlab.frwk.netty NettyFW]
           [com.zotohlab.frwk.io XData]
           [com.google.gson JsonObject]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; file handlers
(defn- replyGetVFile ""

  [^Channel ch ^JsonObject info ^XData xdata]

  (let [kalive (and (notnil? info)
                    (-> info (.get "keep-alive")(.getAsBoolean)))
        res (NettyFW/makeHttpReply 200)
        clen (.size xdata) ]
    (HttpHeaders/setHeader res "connection" (if kalive "keep-alive" "close"))
    (HttpHeaders/setHeader res "content-type" "application/octet-stream")
    (HttpHeaders/setTransferEncodingChunked res)
    (HttpHeaders/setContentLength res clen)
    (log/debug "Flushing file of " clen " bytes. to client.")
    (NettyFW/writeOnly ch res)
    (NettyFW/writeOnly ch (ChunkedStream. (.stream xdata)))
    (NettyFW/closeCF (NettyFW/writeFlush ch LastHttpContent/EMPTY_LAST_CONTENT) kalive)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- filePutter ""

  [^File vdir ^Channel ch ^JsonObject info ^String fname ^XData xdata]

  (try
    (SaveFile vdir fname xdata)
    (NettyFW/replyXXX ch 200)
    (catch Throwable e#
      (log/error e# "")
      (NettyFW/replyXXX ch 500))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileGetter ""

  [^File vdir ^Channel ch ^JsonObject info ^String fname]

  (let [xdata (GetFile vdir fname) ]
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
      (let [vdir (File. (-> options (.get "vdir")(.getAsString)))
            ^ChannelHandlerContext ctx c
            ^DemuxedMsg msg m
            xs (.payload msg)
            info (.info msg)
            ch (.channel ctx)
            mtd (-> info (.get "method")(.getAsString))
            uri (-> info (.get "uri")(.getAsString))
            pos (.lastIndexOf uri (int \/))
            p (if (< pos 0) uri (.substring uri (inc pos)))
            nm (if (cstr/blank? p) (str (juid) ".dat") p) ]
        (log/debug "Method = " mtd ", Uri = " uri ", File = " nm)
        (cond
          (or (= mtd "POST")(= mtd "PUT"))
          (filePutter vdir ch info nm xs)

          (or (= mtd "GET")(= mtd "HEAD"))
          (fileGetter vdir ch info nm)

          :else
          (NettyFW/replyXXX ch 405))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileCfgtor ""

  ^PipelineConfigurator
  []

  (ReifyHTTPPipe fileHandler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MakeMemFileServer "A file server which can get/put files."

  ;; returns netty objects if you want to do clean up
  [^String host port ^JsonObject options]

  (let [^ServerBootstrap bs (InitTCPServer  (fileCfgtor) options)
        ch (StartServer bs host (int port)) ]
    { :bootstrap bs :channel ch }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; filesvr host port vdir
(defn -main ""

  [ & args ]

  (let [opts (JsonObject.) ]
    (cond
      (< (count args) 3)
      (println "usage: filesvr host port <rootdir>")

      :else
      (.addProperty opts "vdir" (str (nth args 2))))
    (MakeMemFileServer (nth args 0)
                       (Integer/parseInt (nth args 1)) opts)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private filesvr-eof nil)

