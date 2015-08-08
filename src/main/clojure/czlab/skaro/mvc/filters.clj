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

  czlab.skaro.mvc.filters

  (:require
    [czlab.skaro.io.http :refer [MaybeLoadRoutes HttpBasicConfig]]
    [czlab.skaro.mvc.assets
    :refer [SetCacheAssetsFlag GetLocalFile ReplyFileAsset]]
    [czlab.xlib.util.core
    :refer [notnil? spos? ToJavaInt try! FPath]]
    [czlab.xlib.util.str :refer [hgl? nsb strim]]
    [czlab.xlib.util.meta :refer [MakeObj]])

  (:require
    [czlab.xlib.util.logging :as log])

  (:use [czlab.xlib.netty.filters]
        [czlab.xlib.netty.io]
        [czlab.skaro.io.triggers]
        [czlab.skaro.io.netty]
        [czlab.skaro.io.core]
        [czlab.skaro.core.sys]
        [czlab.skaro.core.consts]
        [czlab.skaro.mvc.comms]
        [czlab.xlib.net.routes])

  (:import
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
      (cond
        (instance? HttpRequest msg)
        (let [^czlab.xlib.net.routes.RouteCracker
              ck (.getv co :cracker)
              ^ChannelHandlerContext ctx c
              ^HttpRequest req msg
              ch (.channel ctx)
              old (GetAKey ch TOBJ_KEY)
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
        (let [^ChannelHandlerContext ctx c
              ch (.channel ctx)
              flag (:matched (GetAKey ch TOBJ_KEY))]
          (if (true? flag)
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
      (let [^czlab.xlib.net.routes.RouteCracker
            rcc (.getv co :cracker)
            ^ChannelHandlerContext ctx c
            ch (.channel ctx)
            info (:info msg)
            [r1 r2 r3 r4] (.crack rcc info)
            ^czlab.xlib.net.routes.RouteInfo ri r2]
        (if
          (= r1 true)
          (let [^HTTPEvent evt (IOESReifyEvent co ch msg ri) ]
            (log/debug "matched one route: %s, %s%s"
                       (.getPath ri)
                       "and static = " (.isStatic? ri))
            (if (.isStatic? ri)
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
  [^czlab.skaro.io.core.EmitAPI em
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
            (.addBefore pipe (ErrorSinkFilter/getName) "WSOCKDispatcher" disp)
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
          (.addBefore pipe (ErrorSinkFilter/getName) "MVCDispatcher" disp)
          (when-some [h (cond
                         (.get pipe "ssl")
                         "ssl"
                         (.get pipe "HttpRequestDecoder")
                         "HttpRequestDecoder"
                         :else nil)]
            (.addBefore pipe ^String h (FlashFilter/getName) FlashFilter/shared)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty ""

  [^Muble co]

  (let [^Muble ctr (.parent ^Hierarchial co)
        rts (MaybeLoadRoutes co)
        options (.getv co :emcfg)
        bs (InitTCPServer (mvcInitor co options) options) ]
    (.setv co :cracker (MakeRouteCracker rts))
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

