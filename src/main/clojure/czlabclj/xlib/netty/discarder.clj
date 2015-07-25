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

  czlabclj.xlib.netty.discarder

  (:require [czlabclj.xlib.util.core :refer [notnil? Try!]]
            [czlabclj.xlib.util.str :refer [strim nsb hgl?]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.netty.filters]
        [czlabclj.xlib.netty.io])

  (:import  [com.zotohlab.frwk.netty
             AuxHttpFilter
             PipelineConfigurator ErrorSinkFilter]
            [io.netty.channel ChannelHandlerContext Channel
             ChannelPipeline ChannelHandler]
            [io.netty.handler.codec.http LastHttpContent ]
            [io.netty.bootstrap ServerBootstrap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- discarder "Netty pipeline with standard handlers"

  ^PipelineConfigurator
  [callback]

  (ReifyPipeCfgtor
    (fn [^ChannelPipeline pipe options]
      (.addBefore pipe
                  (ErrorSinkFilter/getName)
                  "discarder"
                  (proxy [AuxHttpFilter][]
                    (channelRead0 [c msg]
                      (let [ch (-> ^ChannelHandlerContext
                                   c (.channel))]
                        (when (instance? LastHttpContent msg)
                          (ReplyXXX ch 200)
                          (Try! (apply callback []))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeDiscardHTTPD "Discards the request, just returns 200 OK"

  [host port callback options]

  {:pre [(fn? callback)]}

  (let [bs (InitTCPServer (discarder callback) options)
        ch (StartServer bs host port) ]
    {:bootstrap bs :channel ch}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

