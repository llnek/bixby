;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.netty.filesvr

  (:gen-class)

  (:require [czlabclj.xlib.util.files :refer [SaveFile GetFile]]
            [czlabclj.xlib.util.core
             :refer
             [SafeGetJsonBool SafeGetJsonString juid notnil? ]]
            [czlabclj.xlib.util.str :refer [strim nsb hgl?]]
            [czlabclj.xlib.netty.filters :refer [ReifyHTTPPipe]])

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.netty.io])

  (:import  [io.netty.handler.codec.http
             HttpHeaders$Names
             HttpHeaders$Values
             HttpHeaders LastHttpContent]
            [com.zotohlab.frwk.netty PipelineConfigurator DemuxedMsg]
            [java.io IOException File]
            [io.netty.channel ChannelHandlerContext
             Channel ChannelPipeline
             SimpleChannelInboundHandler ChannelHandler]
            [io.netty.bootstrap ServerBootstrap]
            [io.netty.handler.stream ChunkedStream]
            [com.zotohlab.frwk.netty NettyFW]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; file handlers
(defn- replyGetVFile ""

  [^Channel ch info ^XData xdata]

  (let [res (NettyFW/makeHttpReply 200)
        kalive (:keepAlive info)
        clen (.size xdata) ]
    (doto res
      (SetHdr HttpHeaders$Names/CONTENT_TYPE "application/octet-stream")
      (SetHdr HttpHeaders$Names/CONNECTION
                 (if kalive
                   HttpHeaders$Values/KEEP_ALIVE
                   HttpHeaders$Values/CLOSE))
      (HttpHeaders/setTransferEncodingChunked )
      (HttpHeaders/setContentLength clen))
    (log/debug "Flushing file of " clen " bytes. to client.")
    (doto ch
      (NettyFW/writeOnly res)
      (NettyFW/writeOnly (ChunkedStream. (.stream xdata))))
    (-> (NettyFW/writeFlush ch LastHttpContent/EMPTY_LAST_CONTENT)
        (NettyFW/closeCF kalive))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- filePutter ""

  [^File vdir ^Channel ch
   info
   fname xdata]

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

  [^File vdir ^Channel ch
   info fname]

  (let [xdata (GetFile vdir fname) ]
    (if (.hasContent xdata)
      (replyGetVFile ch info xdata)
      (NettyFW/replyXXX ch 204))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileHandler ""

  ^ChannelHandler
  [options]

  (proxy [SimpleChannelInboundHandler][]
    (channelRead0 [c m]
      (let [vdir (File. ^String (:vdir options))
            ch (-> ^ChannelHandlerContext c
                   (.channel))
            ^DemuxedMsg msg m
            xs (.payload msg)
            info (.info msg)
            ^String mtd (:method info)
            ^String uri (:uri info)
            pos (.lastIndexOf uri (int \/))
            p (if (< pos 0) uri (.substring uri (inc pos)))
            nm (if (cstr/blank? p) (str (juid) ".dat") p) ]
        (log/debug "Method = " mtd ", Uri = " uri ", File = " nm)
        (cond
          (or (= mtd "POST")
              (= mtd "PUT"))
          (filePutter vdir ch info nm xs)

          (or (= mtd "HEAD")
              (= mtd "GET"))
          (fileGetter vdir ch info nm)

          :else
          (NettyFW/replyXXX ch 405))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileCfgtor ""

  ^PipelineConfigurator
  []

  (ReifyHTTPPipe "FileServer" fileHandler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MakeMemFileServer "A file server which can get/put files."

  ;; returns netty objects if you want to do clean up
  [^String host port options]

  (let [bs (InitTCPServer (fileCfgtor) options)
        ch (StartServer bs host (int port)) ]
    {:bootstrap bs :channel ch}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; filesvr host port vdir
(defn -main "Start a basic file server."

  [& args]

  (with-local-vars [opts (transient {})]
    (cond
      (< (count args) 3)
      (println "usage: filesvr host port <rootdir>")

      :else
      (var-set opts (assoc! @opts :vdir (str (nth args 2)))))
    (MakeMemFileServer (nth args 0)
                       (Integer/parseInt (nth args 1))
                       (persistent! @opts))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

