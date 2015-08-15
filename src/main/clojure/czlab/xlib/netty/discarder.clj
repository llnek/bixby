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

  czlab.xlib.netty.discarder

  (:require
    [czlab.xlib.util.str :refer [strim hgl?]]
    [czlab.xlib.util.core :refer [try!]]
    [czlab.xlib.util.logging :as log])

  (:use [czlab.xlib.netty.filters]
        [czlab.xlib.netty.io])

  (:import
    [io.netty.handler.codec.http LastHttpContent ]
    [com.zotohlab.frwk.netty
    AuxHttpFilter
    PipelineConfigurator ErrorSinkFilter]
    [io.netty.channel ChannelHandlerContext Channel
    ChannelPipeline ChannelHandler]
    [io.netty.bootstrap ServerBootstrap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- discarder ""

  ^PipelineConfigurator
  [callback]

  (ReifyPipeCfgtor
    #(->> (proxy [AuxHttpFilter][]
            (channelRead0 [c msg]
              (when (instance? LastHttpContent msg)
                (-> (-> ^ChannelHandlerContext
                        c (.channel))
                    (ReplyXXX  200))
                (try! (callback)))))
          (.addBefore ^ChannelPipeline %1
                      ErrorSinkFilter/NAME "discarder"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DiscardHTTPD*

  "Discards the request, just returns 200 OK"

  ([host port callback & [options]]
   {:pre [(fn? callback)]}
   (let [bs (InitTCPServer (discarder callback) options)
         ch (StartServer bs host port) ]
     {:bootstrap bs :channel ch}))

  ([port callback]
   (DiscardHTTPD* "127.0.0.1" port callback )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

