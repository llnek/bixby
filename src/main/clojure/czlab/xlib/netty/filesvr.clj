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

  czlab.xlib.netty.filesvr

  (:gen-class)

  (:require
    [czlab.xlib.util.files :refer [SaveFile GetFile]]
    [czlab.xlib.util.core
     :refer [SafeGetJsonBool ConvInt trycr
             SafeGetJsonString juid notnil? ]]
    [czlab.xlib.util.str :refer [strim nsb hgl?]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs])

  (:use [czlab.xlib.netty.filters]
        [czlab.xlib.netty.io])

  (:import
    [io.netty.handler.codec.http
     HttpResponse
     HttpHeaders$Names
     HttpHeaders$Values
     HttpHeaders LastHttpContent]
    [com.zotohlab.frwk.netty
     AuxHttpFilter ErrorSinkFilter
     PipelineConfigurator]
    [java.io IOException File]
    [io.netty.channel ChannelHandlerContext
     Channel ChannelPipeline
     SimpleChannelInboundHandler ChannelHandler]
    [io.netty.bootstrap ServerBootstrap]
    [io.netty.handler.stream ChunkedStream]
    [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyGetVFile ""

  [^Channel ch info ^XData xdata]

  (let [res (MakeHttpReply 200)
        kalive (:keepAlive info)
        clen (.size xdata) ]
    (doto res
      (SetHeader HttpHeaders$Names/CONTENT_TYPE "application/octet-stream")
      (SetHeader HttpHeaders$Names/CONNECTION
                 (if kalive
                   HttpHeaders$Values/KEEP_ALIVE
                   HttpHeaders$Values/CLOSE))
      (HttpHeaders/setTransferEncodingChunked )
      (HttpHeaders/setContentLength clen))
    (log/debug "Flushing file of %s bytes to client" clen)
    (doto ch
      (.write res)
      (.write (ChunkedStream. (.stream xdata))))
    (-> (.writeAndFlush ch LastHttpContent/EMPTY_LAST_CONTENT)
        (CloseCF kalive))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fPutter ""

  [^File vdir ^Channel ch
   info
   fname xdata]

  (->> (trycr 500
              (do (SaveFile vdir fname xdata) 200))
       (ReplyXXX ch )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fGetter ""

  [^File vdir ^Channel ch info fname]

  (let [xdata (GetFile vdir fname) ]
    (if (.hasContent xdata)
      (replyGetVFile ch info xdata)
      (ReplyXXX ch 204))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fHandler ""

  ^ChannelHandler
  [options]

  (proxy [AuxHttpFilter][]
    (channelRead0 [c msg]
      (let [ch (-> ^ChannelHandlerContext c
                   (.channel))
            vdir (io/file (:vdir options))
            xs (:payload msg)
            info (:info msg)
            ^String mtd (:method info)
            ^String uri (:uri info)
            pos (.lastIndexOf uri (int \/))
            p (if (< pos 0)
                uri
                (.substring uri
                            (inc pos)))
            nm (if (empty? p) (str (juid) ".dat") p) ]
        (log/debug "method = %s, uri = %s, file = %s" mtd uri nm)
        (cond
          (or (= mtd "POST")
              (= mtd "PUT"))
          (fPutter vdir ch info nm xs)

          (or (= mtd "HEAD")
              (= mtd "GET"))
          (fGetter vdir ch info nm)

          :else
          (ReplyXXX ch 405))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MemFileServer*

  "A file server which can get/put files"

  [host port vdir options]

  (let [bs (InitTCPServer
             (ReifyPipeCfgtor
               #(.addBefore ^ChannelPipeline %1
                            (ErrorSinkFilter/getName)
                            "memfsvr"
                            (fHandler %2)))
             (merge {} options {:vdir vdir}))
        ch (StartServer bs host port) ]
    {:bootstrap bs :channel ch}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; filesvr host port vdir
(defn -main "Start a basic file server"

  [ & args ]

  (when (< (count args) 3)
    (println "usage: filesvr host port <rootdir>"))

  ;; 64meg max file size
  (MemFileServer* (nth args 0)
                     (ConvInt (nth args 1) 8080)
                     (nth args 2)
                     {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

