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

  czlabclj.tardis.mvc.filters

  (:require [czlabclj.xlib.util.core
             :refer
             [notnil?
              spos?
              ToJavaInt
              Muble
              Try!
              NiceFPath]]
            [czlabclj.tardis.io.http :refer [HttpBasicConfig]]
            [czlabclj.tardis.mvc.assets
             :refer
             [SetCacheAssetsFlag GetLocalFile ReplyFileAsset]]
            [czlabclj.xlib.util.str :refer [hgl? nsb strim]]
            [czlabclj.xlib.util.meta :refer [MakeObj]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.netty.filters]
        [czlabclj.xlib.netty.io]
        [czlabclj.tardis.io.triggers]
        [czlabclj.tardis.io.netty]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.core.consts]
        [czlabclj.tardis.mvc.comms]
        [czlabclj.xlib.net.routes])

  (:import  [org.apache.commons.lang3 StringUtils]
            [io.netty.util ReferenceCountUtil]
            [java.util Date]
            [java.io File]
            [com.zotohlab.frwk.server Emitter]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]
            [com.zotohlab.frwk.core Hierarchial Identifiable]
            [com.zotohlab.skaro.io HTTPEvent]
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
            [com.zotohlab.frwk.netty NettyFW ErrorSinkFilter
             SimpleInboundFilter DemuxInboundFilter
             MessageFilter PipelineConfigurator
             FlashFilter]
            [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- routeFilter ""

  ^ChannelHandler
  [^czlabclj.tardis.core.sys.Elmt co]

  (proxy [MessageFilter] []
    (channelRead0 [c msg]
      ;;(log/debug "mvc route filter called with message = " (type msg))
      (cond
        (instance? HttpRequest msg)
        (let [^czlabclj.xlib.net.routes.RouteCracker
              ck (.getAttr co :cracker)
              ^ChannelHandlerContext ctx c
              ^HttpRequest req msg
              ch (.channel ctx)
              cfg {:method (GetMethod req)
                   :uri (GetUriPath req)}
              [r1 r2 r3 r4]
              (.crack ck cfg) ]
          (DelAKey ch TOBJ_KEY)
          (cond
            (and r1 (hgl? r4))
            (SendRedirect ch false r4)

            (= r1 true)
            (do
              ;;(log/debug "mvc route filter MATCHED with uri = " (.getUri req))
              (SetAKey ch TOBJ_KEY {:matched true})
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))

            :else
            (do
              (log/debug "Failed to match uri: " (:uri cfg))
              (ReplyXXX ch 404 false))))

        (instance? HttpResponse msg)
        (do
          (ReferenceCountUtil/retain msg)
          (.fireChannelRead ^ChannelHandlerContext c msg))

        :else
        ;;what is this doing? I forgot...
        (let [^ChannelHandlerContext ctx c
              ch (.channel ctx)
              flag (:matched (GetAKey ch TOBJ_KEY))]
          (if (true? flag)
            (do
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))
            (log/debug "routeFilter: skipping unwanted msg")))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcDisp ""

  ^ChannelHandler
  [^czlabclj.tardis.core.sys.Elmt co]

  (proxy [MessageFilter] []
    (channelRead0 [c msg]
      ;;(log/debug "mvc netty handler called with message = " (type msg))
      (let [^czlabclj.xlib.net.routes.RouteCracker
            rcc (.getAttr co :cracker)
            ^ChannelHandlerContext ctx c
            ch (.channel ctx)
            info (:info msg)
            [r1 r2 r3 r4] (.crack rcc info)
            ^czlabclj.xlib.net.routes.RouteInfo ri r2]
        (if
          (= r1 true)
          (let [^HTTPEvent evt (IOESReifyEvent co ch msg ri) ]
            (log/debug "Matched one route: " (.getPath ri)
                       " , and static = " (.isStatic? ri))
            (if (.isStatic? ri)
              (ServeStatic ri co r3 ch info evt)
              (ServeRoute ri co r3 ch evt)))
          ;;else
          (do
            (log/debug "Failed to match uri: " (:uri info))
            (ServeError co ch 404)) )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockDispatcher ""

  ^ChannelHandler
  [^czlabclj.tardis.io.core.EmitAPI em
   ^czlabclj.tardis.core.sys.Elmt co
   options]

  (let [handlerFn (get-in options [:wsock :handler])]
    (log/debug "wsockDispatcher has user function: " handlerFn)
    (proxy [MessageFilter] []
      (channelRead0 [ctx msg]
        (let [ch (.channel ^ChannelHandlerContext ctx)
              opts {:router handlerFn}
              ^WebSockEvent
              evt (IOESReifyEvent co ch msg nil) ]
          (log/debug "Reified one websocket event")
          (.dispatch em evt opts))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockJiggler "Jiggle the pipeline upon a websocket request"

  ^ChannelHandler
  [co options]

  (let [disp (wsockDispatcher co co options)]
    (proxy [DemuxInboundFilter][]
      (channelRead [c msg]
        (let [^ChannelHandlerContext ctx c
              pipe (.pipeline ctx)
              ch (.channel ctx)
              tmp (GetAKey ch TOBJ_KEY)]
          (when (some? (:wsreq tmp))
            (Try! (.remove pipe "ChunkedWriteHandler"))
            (Try! (.remove pipe "MVCDispatcher"))
            (Try! (.remove pipe "RouteFilter"))
            (.addBefore pipe (ErrorSinkFilter/getName) "WSOCKDispatcher" disp)
            (SetAKey ch MSGTYPE_KEY "wsock"))
        (FireAndQuit pipe ctx "WSockJiggler" msg))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitor ""

  ^PipelineConfigurator
  [^czlabclj.tardis.core.sys.Elmt co options]

  (let [wsock (wsockJiggler co options)
        router (routeFilter co)
        disp (mvcDisp co)]
    (log/debug "netty pipeline initor, emitter = " (type co))
    (ReifyPipeCfgtor
      (fn [p _]
        (let [^ChannelPipeline pipe p]
          (.addAfter pipe "HttpRequestDecoder" "RouteFilter" router)
          (.addAfter pipe "WSockFilter" "WSockJiggler" wsock)
          (.addBefore pipe (ErrorSinkFilter/getName) "MVCDispatcher" disp)
          (when-let [h (cond
                         (.get pipe "ssl")
                         "ssl"
                         (.get pipe "HttpRequestDecoder")
                         "HttpRequestDecoder"
                         :else nil)]
            (.addBefore pipe ^String h (FlashFilter/getName) FlashFilter/shared)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty ""

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^czlabclj.tardis.core.sys.Elmt
        ctr (.parent ^Hierarchial co)
        rts (.getAttr ctr :routes)
        options (.getAttr co :emcfg)
        bs (InitTCPServer (mvcInitor co options) options) ]
    (.setAttr! co :cracker (MakeRouteCracker rts))
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/NettyMVC

  [^czlabclj.tardis.core.sys.Elmt co cfg]

  (log/info "CompConfigure: NetttyMVC: " (.id ^Identifiable co))
  (.setAttr! co :emcfg (HttpBasicConfig co cfg))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/NettyMVC

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "CompInitialize: NetttyMVC: " (.id ^Identifiable co))
  (initNetty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

