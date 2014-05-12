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

  cmzlabsclj.tardis.io.netty

  (:use [cmzlabsclj.util.core :only [ThrowIOE MubleAPI MakeMMap notnil? ConvLong] ])
  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.tardis.core.sys])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.tardis.io.http])
  (:use [cmzlabsclj.tardis.io.triggers])
  (:use [cmzlabsclj.util.str :only [hgl? nsb strim nichts?] ])
  (:use [cmzlabsclj.net.routes :only [MakeRouteCracker RouteCracker] ])
  (:use [cmzlabsclj.util.seqnum :only [NextLong] ])
  (:use [cmzlabsclj.util.mime :only [GetCharset] ])
  (:import (java.net HttpCookie URI URL InetSocketAddress))
  (:import (java.net SocketAddress InetAddress))
  (:import (java.util ArrayList List))
  (:import (com.google.gson JsonObject))
  (:import (java.io IOException))
  (:import (com.zotohlabs.gallifrey.io Emitter HTTPEvent HTTPResult WebSockEvent WebSockResult))
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
                                     DemuxedMsg
                                     HttpDemux ErrorCatcher
                                     PipelineConfigurator))

  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.frwk.io XData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


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
(defn- makeWSockEvent ""

  [^cmzlabsclj.tardis.io.core.EmitterAPI co
   ^Channel ch
   ^XData xdata
   ^JsonObject info ]

  (let [ ssl (notnil? (.get (NettyFW/getPipeline ch) "ssl"))
         ^InetSocketAddress laddr (.localAddress ch)
         ^WebSockResult res (MakeWSockResult co)
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
          (let [ ^cmzlabsclj.tardis.io.core.WaitEventHolder
                 wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
        (emitter [_] co))

      { :typeid :czc.tardis.io/WebSockEvent }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent ""

  ^HTTPEvent
  [^cmzlabsclj.tardis.io.core.EmitterAPI co
   ^Channel ch
   ^XData xdata
   ^JsonObject info ]

  (let [ ^HTTPResult res (MakeHttpResult co)
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
          (let [ v (nsb (GetHeader info "Cookie"))
                 rc (ArrayList.)
                 cks (if (hgl? v) (CookieDecoder/decode v) []) ]
            (doseq [ ^Cookie c (seq cks) ]
              (.add rc (cookieToJava c)))
            rc))
        (getCookie [_ nm]
          (let [ v (nsb (GetHeader info "Cookie"))
                 lnm (cstr/lower-case nm)
                 cks (if (hgl? v) (CookieDecoder/decode v) []) ]
            (some (fn [^Cookie c]
                    (if (= (cstr/lower-case (.getName c)) lnm)
                      (cookieToJava c)
                      nil))
                  (seq cks))))

        (isKeepAlive [_] (-> (.get info "keep-alive")(.getAsBoolean)))

        (hasData [_] (notnil? xdata))
        (data [_] xdata)

        (contentLength [_] (-> (.get info "clen")(.getAsLong)))
        (contentType [_] (GetHeader info "content-type"))

        (encoding [this]  (GetCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaderValues [_ nm] (NettyFW/getHeaderValues info nm))
        (getHeaders [_] (NettyFW/getHeaderNames info))
        (getHeaderValue [_ nm] (GetHeader info nm))
        (hasHeader [_ nm] (HasHeader? info nm))

        (getParameterValues [_ nm] (NettyFW/getParameterValues info nm))
        (getParameterValue [_ nm] (GetParameter info nm))
        (getParameters [_] (NettyFW/getParameters info))
        (hasParameter [_ nm] (HasParam? info nm))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (-> (.get info "protocol")(.getAsString)))
        (method [_] (-> (.get info "method")(.getAsString)))

        (queryString [_] (-> (.get info "query")(.getAsString)))
        (host [_] (-> (.get info "host")(.getAsString)))

        (remotePort [_] (ConvLong (GetHeader info "remote_port") 0))
        (remoteAddr [_] (nsb (GetHeader info "remote_addr")))
        (remoteHost [_] "")

        (scheme [_] (if ssl "https" "http"))

        (serverPort [_] (ConvLong (GetHeader info "server_port") 0))
        (serverName [_] (nsb (GetHeader info "server_name")))

        (isSSL [_] ssl)

        (getUri [_] (-> (.get info "uri")(.getAsString)))

        (getRequestURL [_] (throw (IOException. "not implemented")))

        (getResultObj [_] res)
        (replyResult [this]
          (let [ ^cmzlabsclj.tardis.io.core.WaitEventHolder
                 wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
      )

      { :typeid :czc.tardis.io/HTTPEvent }

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/NettyIO

  [^cmzlabsclj.tardis.io.core.EmitterAPI co & args]

  (let [ ^DemuxedMsg req (nth args 1)
         ^Channel ch (nth args 0)
         xdata (.payload req)
         info (.info req) ]
    (if (-> (.get info "wsock")(.getAsBoolean))
        (makeWSockEvent co ch xdata info)
        (makeHttpEvent co ch xdata info))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/NettyIO

  [^cmzlabsclj.tardis.core.sys.Element co cfg]

  (let [ c (nsb (:context cfg)) ]
    (.setAttr! co :contextPath (strim c))
    (HttpBasicConfig co cfg)
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher ""

  ^ChannelHandler
  ;;[^cmzlabsclj.tardis.core.sys.Element co]
  [^cmzlabsclj.tardis.io.core.EmitterAPI co]

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [ctx msg]
      (let [ ch (.channel ^ChannelHandlerContext ctx)
             ts (.getAttr ^cmzlabsclj.tardis.core.sys.Element
                                    co :waitMillis)
             evt (IOESReifyEvent co ch msg) ]
        (if (instance? HTTPEvent evt)
          (let [ ^cmzlabsclj.tardis.io.core.WaitEventHolder
                 w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
            (.timeoutMillis w ts)
            (.hold co w)))
        (.dispatch co evt {})))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyInitor ""

  ^PipelineConfigurator
  [^cmzlabsclj.tardis.core.sys.Element co]

  (log/debug "tardis netty pipeline initor called with emitter = " (type co))
  (proxy [PipelineConfigurator] []
    (assemble [p o]
      (let [ ^ChannelPipeline pipe p
             ^JsonObject options o
             ssl (SSLServerHShake/getInstance options) ]
        (if ssl (.addLast pipe "ssl" ssl))
        (-> pipe
            (.addLast "codec" (HttpServerCodec.))
            (HttpDemux/addLast )
            (.addLast "chunker" (ChunkedWriteHandler.))
            (.addLast "disp" (msgDispatcher co))
            (ErrorCatcher/addLast ))
        pipe))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ ^cmzlabsclj.tardis.core.sys.Element ctr (.parent ^Hierarchial co)
         ^JsonObject options (.getAttr co :emcfg)
         bs (ServerSide/initServerSide (nettyInitor co) options) ]
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/NettyIO

  [^cmzlabsclj.tardis.core.sys.Element co]

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
(defmethod IOESStop :czc.tardis.io/NettyIO

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ nes (.getAttr co :netty)
         ^ServerBootstrap bs (:bootstrap nes)
         ^Channel ch (:channel nes) ]
    (ServerSide/stop  bs ch)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/NettyIO

  [^cmzlabsclj.tardis.core.sys.Element co]

  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private netty-eof nil)

