;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.netty.filesvr

  (:gen-class)

  (:require
    [czlab.xlib.util.files :refer [SaveFile GetFile]]
    [czlab.xlib.util.str :refer [strim hgl?]]
    [czlab.xlib.util.core
    :refer [SafeGetJsonBool ConvInt trycr
    SafeGetJsonString juid ]]
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

  (let [keep? (:keepAlive info)
        res (HttpReply* 200)
        clen (.size xdata) ]
    (doto res
      (SetHeader "Content-Type" "application/octet-stream")
      (SetHeader "Connection"
                 (if keep? "keep-alive" "close"))
      (HttpHeaders/setTransferEncodingChunked )
      (HttpHeaders/setContentLength clen))
    (log/debug "Flushing file of %s bytes to client" clen)
    (doto ch
      (.write res)
      (.write (ChunkedStream. (.stream xdata))))
    (-> (.writeAndFlush ch LastHttpContent/EMPTY_LAST_CONTENT)
        (CloseCF keep?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fPutter ""

  [^File vdir ^Channel ch
   info
   fname xdata]

  (->> (trycr 500
              (do (SaveFile vdir fname xdata) 200))
       (ReplyXXX ch )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fGetter ""

  [^File vdir ^Channel ch info fname]

  (let [xdata (GetFile vdir fname) ]
    (if (.hasContent xdata)
      (replyGetVFile ch info xdata)
      (ReplyXXX ch 204))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fHandler ""

  ^ChannelHandler
  [options]

  (proxy [AuxHttpFilter][]
    (channelRead0 [c msg]
      (let [ch (-> ^ChannelHandlerContext
                   c (.channel))
            vdir (io/file (:vdir options))
            xs (:payload msg)
            info (:info msg)
            {:keys [method uri]} info
            uri (str uri)
            pos (.lastIndexOf uri (int \/))
            p (if (< pos 0)
                uri
                (.substring uri
                            (inc pos)))
            nm (if (empty? p) (str (juid) ".dat") p) ]
        (log/debug "method = %s, uri = %s, file = %s" method uri nm)
        (cond
          (or (= method "POST")
              (= method "PUT"))
          (fPutter vdir ch info nm xs)

          (or (= method "HEAD")
              (= method "GET"))
          (fGetter vdir ch info nm)

          :else
          (ReplyXXX ch 405))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MemFileServer*

  "A file server which can get/put files"

  [host port vdir options]

  (let [bs (InitTCPServer
             (ReifyPipeCfgtor
               #(.addBefore ^ChannelPipeline %1
                            ErrorSinkFilter/NAME
                            "memfsvr"
                            (fHandler %2)))
             (merge {} options {:vdir vdir}))
        ch (StartServer bs host port) ]
    {:bootstrap bs :channel ch}))

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

