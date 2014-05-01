;;
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
;;
;; This library is distributed in the hope that it will be useful
;; but without any warranty; without even the implied warranty of
;; merchantability or fitness for a particular purpose.
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;;
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.
;;

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.io.netty

  (:use [comzotohlabscljc.util.core :only [ThrowIOE MubleAPI MakeMMap notnil? ConvLong] ])
  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.io.http])
  (:use [comzotohlabscljc.tardis.io.triggers])
  (:use [comzotohlabscljc.util.str :only [hgl? nsb strim nichts?] ])
  (:use [comzotohlabscljc.net.routes :only [MakeRouteCracker RouteCracker] ])
  (:use [comzotohlabscljc.util.seqnum :only [NextLong] ])
  (:use [comzotohlabscljc.util.mime :only [GetCharset] ])
  (:import (java.net HttpCookie URI URL InetSocketAddress))
  (:import (java.net SocketAddress InetAddress))
  (:import (java.util ArrayList List))
  (:import (com.google.gson JsonObject))
  (:import (java.io IOException))
  (:import (com.zotohlabs.gallifrey.io Emitter HTTPEvent WebSockEvent WebSockResult))
  (:import (javax.net.ssl SSLContext))
  (:import (io.netty.handler.codec.http HttpRequest HttpResponse
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpServerCodec
                                        HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder))
  (:import (io.netty.bootstrap ServerBootstrap))
  (:import (io.netty.channel Channel ChannelHandler
                             SimpleChannelInboundHandler
                             ChannelPipeline ChannelHandlerContext))
  (:import (io.netty.handler.stream ChunkedWriteHandler))
  (:import (com.zotohlabs.frwk.netty NettyFW
                                     SSLServerHShake
                                     ServerSide
                                     HttpDemux ErrorCatcher
                                     PipelineConfigurator))

  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.frwk.io XData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-wsock-result ""

  []

  (let [ impl (MakeMMap) ]
    (.setf! impl :binary false)
    (.setf! impl :text false)
    (.setf! impl :data nil)
    (reify
      MubleAPI
      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl))

      WebSockResult
      (isBinary [_] (.getf impl :binary))
      (isText [_] (.getf impl :text))
      (getData [_] (XData. (.getf impl :data)))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToJava ""

  [^Cookie c]

  (doto (HttpCookie. (.getName c)(.getValue c))
        (.setComment (.getComment c))
        (.setDomain (.getDomain c))
        (.setMaxAge (.getMaxAge c))
        (.setPath (.getPath c))
        (.setVersion (.getVersion c))
        (.setHttpOnly (.isHttpOnly c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-wsock-event ""

  [^comzotohlabscljc.tardis.io.core.EmitterAPI co
   ^Channel ch
   ^XData xdata]

  (let [ ssl (notnil? (.get (NettyFW/getPipeline ch) "ssl"))
         ^InetSocketAddress laddr (.localAddress ch)
         ^WebSockResult res (make-wsock-result)
         impl (MakeMMap)
         eeid (NextLong) ]
    (with-meta
      (reify
        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        WebSockEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (isSSL [_] ssl)
        (isText [_] (instance? String (.content xdata)))
        (isBinary [this] (not (.isText this)))
        (getData [_] xdata)
        (getResultObj [_] res)
        (replyResult [this]
          (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
                 wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
        (emitter [_] co))

      { :typeid :czc.hhh.io/WebSockEvent }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.hhh.io/NettyIO

  [^comzotohlabscljc.tardis.io.core.EmitterAPI co & args]

  (let [ ^HTTPResult res (MakeHttpResult)
         ^HttpRequest req (nth args 1)
         ^XData xdata (nth args 2)
         ^Channel ch (nth args 0)
         ssl (notnil? (.get (NettyFW/getPipeline ch) "ssl"))
         ^InetSocketAddress laddr (.localAddress ch)
         impl (MakeMMap)
         eeid (NextLong) ]
    (with-meta
      (reify

        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        HTTPEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (emitter [_] co)
        (getCookies [_]
          (let [ v (nsb (HttpHeaders/getHeader req "Cookie"))
                 rc (ArrayList.)
                 cks (if (hgl? v) (CookieDecoder/decode v) []) ]
            (doseq [ ^Cookie c (seq cks) ]
              (.add rc (cookieToJava c)))
            rc))
        (getCookie [_ nm]
          (let [ v (nsb (HttpHeaders/getHeader req "Cookie"))
                 lnm (cstr/lower-case nm)
                 cks (if (hgl? v) (CookieDecoder/decode v) []) ]
            (some (fn [^Cookie c]
                    (if (= (cstr/lower-case (.getName c)) lnm)
                      (cookieToJava c)
                      nil))
                  (seq cks))))

        (isKeepAlive [_] (HttpHeaders/isKeepAlive req))

        (hasData [_] (notnil? xdata))
        (data [_] xdata)

        (contentType [_] (HttpHeaders/getHeader req "content-type"))
        (contentLength [_] (HttpHeaders/getContentLength req 0))

        (encoding [this]  (GetCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaderValues [_ nm] (-> (.headers req) (.getAll nm)))
        (getHeaders [_] (-> (.headers req) (.names)))
        (getHeaderValue [_ nm] (HttpHeaders/getHeader req nm))

        (getParameterValues [_ nm]
          (let [ dc (QueryStringDecoder. (.getUri req))
                 rc (.get (.parameters dc) nm) ]
            (if (nil? rc) (ArrayList.) rc)))

        (getParameters [_]
          (let [ dc (QueryStringDecoder. (.getUri req))
                 m (.parameters dc) ]
            (.keySet m)))

        (getParameterValue [_ nm]
          (let [ dc (QueryStringDecoder. (.getUri req))
                 ^List rc (.get (.parameters dc) nm) ]
            (if (and (notnil? rc) (> (.size rc) 0))
              (.get rc 0)
              nil)))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (.toString (.getProtocolVersion req)))
        (method [_] (.toString (.getMethod req)))

        (host [_] (HttpHeaders/getHost req))

        (queryString [_]
          (let [ s (nsb (.getUri req))
                 pos (.indexOf s "?") ]
            (if (>= pos 0)
              (.substring s pos)
              "")))

        (remotePort [_] (ConvLong (HttpHeaders/getHeader req "REMOTE_PORT") 0))
        (remoteAddr [_] (nsb (HttpHeaders/getHeader req "REMOTE_ADDR")))
        (remoteHost [_] (nsb (HttpHeaders/getHeader req "")))

        (scheme [_] (if ssl "https" "http"))

        (serverPort [_] (ConvLong (HttpHeaders/getHeader req "SERVER_PORT") 0))
        (serverName [_] (nsb (HttpHeaders/getHeader req "SERVER_NAME")))

        (isSSL [_] ssl)

        (getUri [_]
          (let [ dc (QueryStringDecoder. (.getUri req)) ]
            (.path dc)))

        (getRequestURL [_] (throw (IOException. "not implemented")))

        (getResultObj [_] res)
        (replyResult [this]
          (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
                 wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
      )

      { :typeid :czc.hhh.io/HTTPEvent }

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.hhh.io/NettyIO

  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ c (nsb (:context cfg)) ]
    (.setAttr! co :contextPath (strim c))
    (HttpBasicConfig co cfg)
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RouteFilter ""

  ^ChannelHandler
  ;;[^comzotohlabscljc.hhh.io.core.EmitterAPI co]
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [c msg]
      (let [ ^comzotohlabscljc.net.routes.RouteCracker c (.getAttr co :cracker)
             ^ChannelHandlerContext ctx c
             ch (.channel ctx) ]
        (if (instance? HttpRequest msg)
          (let [ ^HttpRequest req msg
                 qqq (QueryStringDecoder. (.getUri req)) ]
            ))
        (.fireChannelRead ctx msg)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MsgDispatcher ""

  ^ChannelHandler
  ;;[^comzotohlabscljc.hhh.core.sys.Element co]
  [^comzotohlabscljc.tardis.io.core.EmitterAPI co]

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [ctx msg]
      (let [ ch (.channel ^ChannelHandlerContext ctx)
             evt (IOESReifyEvent co ch msg)
             ^comzotohlabscljc.tardis.io.core.WaitEventHolder
             w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
        (.timeoutMillis w (.getAttr ^comzotohlabscljc.tardis.core.sys.Element co :waitMillis))
        (.hold co w)
        (.dispatch co evt {})))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyInitor ""

  ^PipelineConfigurator
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (proxy [PipelineConfigurator] []
    (assemble [p o]
      (let [ ^ChannelPipeline pipe p
             ^JsonObject options o ]
        (-> pipe
            (.addLast "ssl" (SSLServerHShake/getInstance options))
            (.addLast "codec" (HttpServerCodec.))
            (.addLast "filter" (RouteFilter co))
            (.addLast "demux" (HttpDemux/getInstance))
            (.addLast "chunker" (ChunkedWriteHandler.))
            (.addLast "disp" (MsgDispatcher co))
            (.addLast "error" (ErrorCatcher/getInstance)))
        pipe))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^comzotohlabscljc.tardis.core.sys.Element ctr (.parent ^Hierarchial co)
         cracker (MakeRouteCracker (.getAttr ctr :routes))
         options (doto (JsonObject.)
                   (.addProperty "serverkey" (nsb (.getAttr co :serverKey)))
                   (.addProperty "passwd" (nsb (.getAttr co :passwd))))
         bs (ServerSide/initServerSide (nettyInitor co) options) ]
    (.setAttr! co :netty  { :bootstrap bs })
    (.setAttr! co :cracker cracker)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.hhh.io/NettyIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ host (nsb (.getAttr co :host))
         port (.getAttr co :port)
         nes (.getAttr co :netty)
         ^ServerBootstrap bs (:bootstrap nes)
         ch (ServerSide/start bs host (int port)) ]
    (.setAttr! co :netty (assoc nes :channel ch))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.hhh.io/NettyIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ nes (.getAttr co :netty)
         ^ServerBootstrap bs (:bootstrap nes)
         ^Channel ch (:channel nes) ]
    (ServerSide/stop  bs ch)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.hhh.io/NettyIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private netty-eof nil)

