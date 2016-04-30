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

