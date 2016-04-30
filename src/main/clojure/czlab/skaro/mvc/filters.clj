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

  czlab.skaro.mvc.filters

  (:require
    [czlab.skaro.io.http :refer [MaybeLoadRoutes HttpBasicConfig]]
    [czlab.skaro.mvc.assets
    :refer [SetCacheAssetsFlag GetLocalFile ReplyFileAsset]]
    [czlab.xlib.util.core
    :refer [spos? ToJavaInt try! FPath]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [hgl? strim]]
    [czlab.xlib.util.meta :refer [NewObj*]])

  (:use [czlab.xlib.netty.filters]
        [czlab.xlib.netty.io]
        [czlab.skaro.io.netty]
        [czlab.skaro.io.core]
        [czlab.skaro.core.sys]
        [czlab.skaro.core.consts]
        [czlab.skaro.mvc.comms]
        [czlab.xlib.net.routes])

  (:import
    [com.zotohlab.skaro.runtime RouteInfo RouteCracker]
    [org.apache.commons.lang3 StringUtils]
    [io.netty.util ReferenceCountUtil]
    [java.util Date]
    [java.io File]
    [com.zotohlab.frwk.server Emitter]
    [com.zotohlab.frwk.io XData]
    [com.zotohlab.frwk.core Hierarchial Identifiable]
    [com.zotohlab.skaro.io HTTPEvent]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.skaro.mvc HTTPErrorHandler
    MVCUtils WebAsset WebContent]
    [io.netty.handler.codec.http HttpRequest
    HttpResponse
    CookieDecoder ServerCookieEncoder
    DefaultHttpResponse HttpVersion
    HttpResponseEncoder HttpRequestDecoder
    HttpHeaders LastHttpContent
    HttpHeaders Cookie QueryStringDecoder]
    [io.netty.bootstrap ServerBootstrap]
    [io.netty.channel Channel ChannelHandler
    ChannelDuplexHandler
    SimpleChannelInboundHandler
    ChannelPipeline ChannelHandlerContext]
    [io.netty.handler.stream ChunkedWriteHandler]
    [io.netty.util AttributeKey]
    [io.netty.handler.timeout IdleState
    IdleStateEvent
    IdleStateHandler]
    [com.zotohlab.frwk.netty ErrorSinkFilter
    SimpleInboundFilter InboundAdapter
    MessageFilter PipelineConfigurator
    FlashFilter]
    [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- routeFilter ""

  ^ChannelHandler
  [^Muble co]

  (proxy [MessageFilter] []
    (channelRead0 [c msg]
      (log/debug "mvc route filter called with message = %s" (type msg))
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx)
            old (GetAKey ch TOBJ_KEY) ]
        (cond
          (instance? HttpRequest msg)
          (let [^HttpRequest req msg
                ^RouteCracker
                ck (.getv co :cracker)
                cfg {:method (GetMethod req)
                     :uri (GetUriPath req)}
                [r1 r2 r3 r4]
                (.crack ck cfg) ]
            (->> (merge old {:matched false})
                 (SetAKey ch TOBJ_KEY))
            (cond
              (and r1 (hgl? r4))
              (SendRedirect ch false r4)
              (= r1 true)
              (do
                (log/debug "mvc route filter MATCHED with uri = %s" (.getUri req))
                (->> (merge old {:matched true})
                     (SetAKey ch TOBJ_KEY))
                (ReferenceCountUtil/retain msg)
                (.fireChannelRead ctx msg))
              :else
              (do
                (log/debug "failed to match uri: %s" (:uri cfg))
                (ReplyXXX ch 404 false))))

          (instance? HttpResponse msg)
          (do
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ^ChannelHandlerContext c msg))

          :else
          (if (true? (:matched (GetAKey ch TOBJ_KEY)))
            (do
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))
            (log/debug "routeFilter: skipping unwanted msg: %s" (type msg))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcDisp ""

  ^ChannelHandler
  [^Muble co]

  (proxy [MessageFilter] []
    (channelRead0 [c msg]
      (log/debug "mvc netty handler called with message = %s" (type msg))
      (let [^RouteCracker
            rcc (.getv co :cracker)
            ^ChannelHandlerContext ctx c
            ch (.channel ctx)
            info (:info msg)
            [r1 r2 r3 r4] (.crack rcc info)
            ^RouteInfo ri r2]
        (if
          (= r1 true)
          (let [^HTTPEvent evt (IOESReifyEvent co ch msg ri) ]
            (log/debug "matched one route: %s, %s%s"
                       (.getPath ri)
                       "and static = " (.isStatic ri))
            (if (.isStatic ri)
              (ServeStatic ri co r3 ch info evt)
              (ServeRoute ri co r3 ch evt)))
          ;;else
          (do
            (log/debug "failed to match uri: %s" (:uri info))
            (ServeError co ch 404)) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockDispatcher ""

  ^ChannelHandler
  [^Emitter em
   ^Muble co
   options]

  (let [handlerFn (get-in options [:wsock :handler])]
    (log/debug "wsockDispatcher has user function: %s" handlerFn)
    (proxy [MessageFilter] []
      (channelRead0 [ctx msg]
        (let [ch (.channel ^ChannelHandlerContext ctx)
              opts {:router handlerFn}
              ^WebSockEvent
              evt (IOESReifyEvent co ch msg nil) ]
          (log/debug "reified one websocket event")
          (.dispatch em evt opts))))))

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
              tmp (GetAKey ch TOBJ_KEY)]
          (when (some? (:wsreq tmp))
            (try! (.remove pipe "ChunkedWriteHandler"))
            (try! (.remove pipe "MVCDispatcher"))
            (try! (.remove pipe "RouteFilter"))
            (try! (.remove pipe "HttpFilter"))
            (.addBefore pipe ErrorSinkFilter/NAME "WSOCKDispatcher" disp)
            (SetAKey ch MSGTYPE_KEY "wsock"))
        (FireAndQuit pipe ctx this msg))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitor ""

  ^PipelineConfigurator
  [^Muble co options]

  (let [wsock (wsockJiggler co options)
        router (routeFilter co)
        disp (mvcDisp co)]
    (log/debug "netty pipeline initor, emitter = %s" (type co))
    (ReifyPipeCfgtor
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
            (.addBefore pipe ^String h FlashFilter/NAME FlashFilter/shared)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty ""

  [^Muble co]

  (let [^Muble ctr (.parent ^Hierarchial co)
        rts (MaybeLoadRoutes co)
        options (.getv co :emcfg)
        bs (InitTCPServer (mvcInitor co options) options) ]
    (.setv co :cracker (RouteCracker* rts))
    (.setv co :netty  { :bootstrap bs })
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/NettyMVC

  [^Muble co cfg]

  (log/info "compConfigure: NetttyMVC: %s" (.id ^Identifiable co))
  (.setv co :emcfg (HttpBasicConfig co cfg))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.io/NettyMVC

  [^Muble co]

  (log/info "compInitialize: NetttyMVC: %s" (.id ^Identifiable co))
  (initNetty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

