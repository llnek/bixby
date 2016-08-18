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
      :author "Kenneth Leung" }

  czlab.skaro.mvc.filters

  (:require
    [czlab.skaro.io.http :refer [maybeLoadRoutes httpBasicConfig]]
    [czlab.skaro.mvc.assets
     :refer [setCacheAssetsFlag
             getLocalFile
             replyFileAsset]]
    [czlab.xlib.core :refer [spos? try! fpath]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.meta :refer [new<>]])

  (:use [czlab.skaro.io.netty]
        [czlab.netty.core]
        [czlab.skaro.io.core]
        [czlab.skaro.sys.core]
        [czlab.skaro.mvc.comms]
        [czlab.net.routes])

  (:import
    [org.apache.commons.lang3 StringUtils]
    [czlab.net RouteInfo RouteCracker]
    [io.netty.util ReferenceCountUtil]
    [java.util Date]
    [java.io File]
    [czlab.server Emitter]
    [czlab.xlib XData
     Muble
     Hierarchial
     Identifiable]
    [czlab.skaro.io HttpEvent]
    [czlab.skaro.mvc
     HttpErrorHandler
     MVCUtils
     WebAsset
     WebContent]
    [io.netty.handler.codec.http
     HttpRequest
     HttpResponse
     DefaultHttpResponse
     HttpVersion
     HttpResponseEncoder
     HttpRequestDecoder
     HttpHeaders
     LastHttpContent
     HttpHeaders
     Cookie
     QueryStringDecoder]
    [io.netty.bootstrap ServerBootstrap]
    [io.netty.channel
     SimpleChannelInboundHandler
     Channel
     ChannelHandler
     ChannelDuplexHandler
     ChannelPipeline
     ChannelHandlerContext]
    [io.netty.handler.stream ChunkedWriteHandler]
    [io.netty.util AttributeKey]
    [io.netty.handler.timeout
     IdleState
     IdleStateEvent
     IdleStateHandler]
    [czlab.netty
     CPDecorator
     InboundFilter
     PipelineCfgtor ]
    [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- routeFilter

  ""
  ^ChannelHandler
  [^Service co]

  (proxy [InboundFilter] []
    (channelRead0 [c msg]
      (log/debug "mvc route filter called with message = %s" (type msg))
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx)
            old (getAKey ch TOBJ_KEY) ]
        (cond
          (instance? HttpRequest msg)
          (let [^HttpRequest req msg
                ^RouteCracker
                ck (.getv (.getx co) :cracker)
                cfg {:method (getMethod req)
                     :uri (getUriPath req)}
                [r1 r2 r3 r4]
                (.crack ck cfg) ]
            (->> (merge old {:matched false})
                 (setAKey ch TOBJ_KEY))
            (cond
              (and r1 (hgl? r4))
              (sendRedirect ch false r4)
              (true? r1)
              (do
                (log/debug "mvc route filter MATCHED with uri = %s" (.getUri req))
                (->> (merge old {:matched true})
                     (setAKey ch TOBJ_KEY))
                (ReferenceCountUtil/retain msg)
                (.fireChannelRead ctx msg))
              :else
              (do
                (log/debug "failed to match uri: %s" (:uri cfg))
                (replyXXX ch 404 false))))

          (instance? HttpResponse msg)
          (do
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ^ChannelHandlerContext c msg))

          :else
          (if (true? (:matched (getAKey ch TOBJ_KEY)))
            (do
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))
            (log/debug "routeFilter: skipping unwanted msg: %s" (type msg))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcDisp

  ""
  ^ChannelHandler
  [^Service co]

  (proxy [InboundFilter] []
    (channelRead0 [c msg]
      (log/debug "mvc netty handler called with message = %s" (type msg))
      (let [^RouteCracker
            rcc (.getv (.getx co) :cracker)
            ^ChannelHandlerContext ctx c
            ch (.channel ctx)
            gist (:gist msg)
            [r1 r2 r3 r4] (.crack rcc gist)
            ^RouteInfo ri r2]
        (if
          (true? r1)
          (let [^HttpEvent evt (ioevent<> co ch msg ri) ]
            (log/debug "matched one route: %s, %s%s"
                       (.getPath ri)
                       "and static = " (.isStatic ri))
            (if (.isStatic ri)
              (serveStatic ri co r3 ch gist evt)
              (serveRoute ri co r3 ch evt)))
          ;;else
          (do
            (log/debug "failed to match uri: %s" (:uri gist))
            (serveError co ch 404)) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockDispatcher

  ""
  ^ChannelHandler
  [^Service co
   options]

  (let [handlerFn (get-in options [:wsock :handler])]
    (log/debug "wsockDispatcher has user function: %s" handlerFn)
    (proxy [InboundFilter] []
      (channelRead0 [ctx msg]
        (let [ch (.channel ^ChannelHandlerContext ctx)
              opts {:router handlerFn}
              ^WebSockEvent
              evt (ioevent<> co ch msg nil) ]
          (log/debug "reified one websocket event")
          (.dispatchEx co evt opts))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockJiggler

  "Jiggle the pipeline upon a websocket request"
  ^ChannelHandler
  [co options]

  (let [disp (wsockDispatcher co co options)]
    (proxy [InboundAdapter][]
      (channelRead [c msg]
        (let [^ChannelHandlerContext ctx c
              pipe (.pipeline ctx)
              ch (.channel ctx)
              tmp (getAKey ch TOBJ_KEY)]
          (when (some? (:wsreq tmp))
            (try! (.remove pipe "ChunkedWriteHandler"))
            (try! (.remove pipe "MVCDispatcher"))
            (try! (.remove pipe "RouteFilter"))
            (try! (.remove pipe "HttpFilter"))
            (.addBefore pipe ErrorSinkFilter/NAME "WSOCKDispatcher" disp)
            (setAKey ch MSGTYPE_KEY "wsock"))
        (fireAndQuit pipe ctx this msg))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitor

  ""
  ^PipelineCfgtor
  [^Service co options]

  (let [wsock (wsockJiggler co options)
        router (routeFilter co)
        disp (mvcDisp co)]
    (log/debug "netty pipeline initor, emitter = %s" (type co))
    nil))
(comment
    (reifyPipeCfgtor
      (fn [p _]
        (let [^ChannelPipeline pipe p]
          (.addAfter pipe "HttpRequestDecoder" "RouteFilter" router)
          (.addAfter pipe "WSockFilter" "WSockJiggler" wsock)
          (.addBefore pipe ErrorSinkFilter/NAME "MVCDispatcher" disp)
          (when-some [h (cond
                         (.get pipe "ssl")
                         "ssl"
                         (.get pipe "HttpRequestDecoder")
                         "HttpRequestDecoder"
                         :else nil)]
      (.addBefore pipe ^String h FlashFilter/NAME FlashFilter/shared))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty ""

  [^Service co]

  (let [^Container ctr (.parent ^Hierarchial co)
        rts (maybeLoadRoutes co)
        options (.config co)
        bs (httpServer<> (mvcInitor co options) options)]
    (doto (.getx co)
      (.setv :cracker (routeCracker rts))
      (.setv :netty  { :bootstrap bs }))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :czc.skaro.io/NettyMVC
  [^Service co & [cfg0]]

  (log/info "comp->initialize: NetttyMVC: %s" (.id co))
  (.setv (.getx co) :emcfg (httpBasicConfig co cfg0))
  (initNetty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


