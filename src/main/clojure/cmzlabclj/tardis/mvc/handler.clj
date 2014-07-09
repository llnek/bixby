;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabclj.tardis.mvc.handler

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core
         :only [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath] ]
        [cmzlabclj.nucleus.netty.io]
        [cmzlabclj.tardis.io.triggers]
        [cmzlabclj.tardis.io.http :only [HttpBasicConfig] ]
        [cmzlabclj.tardis.io.netty]
        [cmzlabclj.tardis.io.core]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.core.constants]
        [cmzlabclj.tardis.mvc.templates
         :only [SetCacheAssetsFlag GetLocalFile ReplyFileAsset] ]
        [cmzlabclj.tardis.mvc.comms]
        [cmzlabclj.nucleus.util.str :only [hgl? nsb strim] ]
        [cmzlabclj.nucleus.util.meta :only [MakeObj] ]
        [cmzlabclj.nucleus.net.routes])

  (:import [org.apache.commons.lang3 StringUtils]
           [io.netty.util ReferenceCountUtil]
           [java.util Date]
           [java.io File]
           [com.zotohlab.frwk.io XData]
           [com.google.gson JsonObject]
           [com.zotohlab.frwk.core Hierarchial Identifiable]
           [com.zotohlab.gallifrey.io HTTPEvent Emitter]
           [com.zotohlab.gallifrey.mvc HTTPErrorHandler MVCUtils WebAsset WebContent]
           [io.netty.handler.codec.http HttpRequest HttpResponse
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpResponseEncoder HttpRequestDecoder
                                        HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.channel Channel ChannelHandler
                             SimpleChannelInboundHandler
                             ChannelPipeline ChannelHandlerContext]
           [io.netty.handler.stream ChunkedWriteHandler]
           [io.netty.util AttributeKey]
           [com.zotohlab.frwk.netty NettyFW ErrorCatcher SimpleInboundHandler
                                     DemuxedMsg PipelineConfigurator
                                     FlashHandler]
           [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; leiningen compile throws errors, probably compiling twice ???
(try
(def ^:private GOOD_FLAG (AttributeKey/valueOf "good-msg"))
(catch Throwable e#))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- routeFilter ""

  ^ChannelHandler
  [^cmzlabclj.tardis.core.sys.Element co]

  (proxy [SimpleInboundHandler] []
    (channelRead0 [c msg]
      ;;(log/debug "mvc route filter called with message = " (type msg))
      (cond
        (instance? HttpRequest msg)
        (let [^cmzlabclj.nucleus.net.routes.RouteCracker
              ck (.getAttr co :cracker)
              ^ChannelHandlerContext ctx c
              ^HttpRequest req msg
              ch (.channel ctx)
              json (doto (JsonObject.)
                     (.addProperty "method" (NettyFW/getMethod req))
                     (.addProperty "uri" (NettyFW/getUriPath req)))
              [r1 r2 r3 r4]
              (.crack ck json) ]
          (-> (.attr ctx GOOD_FLAG)(.remove))
          (cond
            (and r1 (hgl? r4))
            (NettyFW/sendRedirect ch false ^String r4)

            (= r1 true)
            (do
              ;;(log/debug "mvc route filter MATCHED with uri = " (.getUri req))
              (-> (.attr ctx GOOD_FLAG)(.set "matched"))
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))

            :else
            (do
              (log/debug "failed to match uri: " (.getUri req))
              (NettyFW/replyXXX ch 404 false))))

        (instance? HttpResponse msg)
        (do
          (ReferenceCountUtil/retain msg)
          (.fireChannelRead ^ChannelHandlerContext c msg))

        :else
        (let [^ChannelHandlerContext ctx c
              flag (-> (.attr ctx GOOD_FLAG)(.get)) ]
          (if (notnil? flag)
            (do
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))
            (log/debug "skipping unwanted msg")))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher ""

  ^ChannelHandler
  [^cmzlabclj.tardis.io.core.EmitterAPI em
   ^cmzlabclj.tardis.core.sys.Element co]

  (proxy [SimpleInboundHandler] []
    (channelRead0 [ctx msg]
      ;;(log/debug "mvc netty handler called with message = " (type msg))
      (let [^cmzlabclj.nucleus.net.routes.RouteCracker
            rcc (.getAttr co :cracker)
            ch (.channel ^ChannelHandlerContext ctx)
            info (.info ^DemuxedMsg msg)
            [r1 ^cmzlabclj.nucleus.net.routes.RouteInfo r2 r3 r4]
            (.crack rcc info) ]
        (cond
          (= r1 true)
          (let [^HTTPEvent evt (IOESReifyEvent co ch msg r2) ]
            (log/debug "matched one route: " (.getPath r2)
                       " , and static = " (.isStatic? r2))
            (if (.isStatic? r2)
              (ServeStatic r2 co r3 ch info evt)
              (ServeRoute r2 co r3 ch evt)))
          :else
          (do
            (log/debug "failed to match uri: " (-> info (.getAsJsonPrimitive "uri")
                                                        (.getAsString)))
            (ServeError co ch 404)) )))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitorOnWS ""

  [^ChannelHandlerContext ctx]
  (let [^ChannelPipeline pipe (.pipeline ctx) ]
    (.remove pipe "RouteFilter")))

(defn- mvcInitor ""

  ^PipelineConfigurator
  [^cmzlabclj.tardis.core.sys.Element co options1]

  (let [cfgopts {:wsockUri (:wsock options1)
                 :onwsock mvcInitorOnWS } ]
    (log/debug "mvc netty pipeline initor called with emitter = " (type co))
    (proxy [PipelineConfigurator] []
      (assemble [p o]
        (let [^ChannelPipeline pipe p
              ^JsonObject options o
              ssl (SSLServerHShake options) ]
          (when-not (nil? ssl) (.addLast pipe "ssl" ssl))
          (doto pipe
            (FlashHandler/addLast )
            (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
            (.addLast "RouteFilter" (routeFilter co))
            (.addLast "HttpDemuxer" (MakeHttpDemuxer cfgopts))
            (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
            (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
            (.addLast "NettyDispatcher" (msgDispatcher co co))
            (ErrorCatcher/addLast ))
          pipe)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^cmzlabclj.tardis.core.sys.Element
        ctr (.parent ^Hierarchial co)
        rts (.getAttr ctr :routes)
        ^JsonObject options (.getAttr co :emcfg)
        bs (InitTCPServer (mvcInitor co options) options) ]
    (.setAttr! co :cracker (MakeRouteCracker rts))
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/NettyMVC

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  (HttpBasicConfig co cfg)
  (let [^JsonObject json (.getAttr co :emcfg)
        c (nsb (:context cfg)) ]

    ;;(.setAttr! co :welcomeFiles (:welcomeFiles cfg))

    (let [xxx (strim c) ]
      (.addProperty json "contextPath" xxx)
      (.setAttr! co :contextPath xxx))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; session/cookie stuff
    (let [n (:sessionAgeSecs cfg)
          xxx (if (nil? n) 3600 n) ]
      (.addProperty json "sessionAgeSecs" (ToJavaInt xxx))
      (.setAttr! co :sessionAgeSecs xxx))
    (let [n (:maxIdleSecs cfg)
          xxx (if (nil? n) 900 n) ]
      (.addProperty json "maxIdleSecs" (ToJavaInt xxx))
      (.setAttr! co :maxIdleSecs xxx))
    (let [xxx (nsb (:domainPath cfg)) ]
      (.addProperty json "domainPath" xxx)
      (.setAttr! co :domainPath xxx))
    (let [xxx (nsb (:domain cfg)) ]
      (.addProperty json "domain" xxx)
      (.setAttr! co :domain xxx))
    (let [n (:hidden cfg)
          xxx (if (false? n) false true) ]
      (.addProperty json "hidden" xxx)
      (.setAttr! co :hidden xxx))
    ;;
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;; caching stuff
    (let [n (:cacheMaxAgeSecs cfg)
          xxx (if (nil? n) 3600 n) ]
      (.addProperty json "cacheMaxAgeSecs" (ToJavaInt xxx))
      (.setAttr! co :cacheMaxAgeSecs xxx))
    (let [xxx (:useETags cfg) ]
      (.addProperty json "useETags" (true? xxx))
      (.setAttr! co :useETags xxx))
    (let [xxx (:cacheAssets cfg) ]
      (.addProperty json "cacheAssets" (not (false? xxx)))
      (when (false? xxx)(SetCacheAssetsFlag false))
      (.setAttr! co :cacheAssets xxx))
  ;;
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


    (let [xxx (strim (:handler cfg)) ]
      (.addProperty json "router" xxx)
      (.setAttr! co :router xxx))

    (let [xxx (strim (:errorHandler cfg)) ]
      (.addProperty json "errorRouter" xxx)
      (.setAttr! co :errorRouter xxx))

    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/NettyMVC

  [^cmzlabclj.tardis.core.sys.Element co]

  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private handler-eof nil)

