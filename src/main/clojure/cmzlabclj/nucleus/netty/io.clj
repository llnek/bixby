;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabclj.nucleus.netty.io

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabclj.nucleus.util.core :only [ThrowIOE MakeMMap notnil? Try!] ]
        [cmzlabclj.nucleus.util.str :only [strim nsb hgl?] ]
        [cmzlabclj.nucleus.netty.request]
        [cmzlabclj.nucleus.netty.form])
  (:import [io.netty.channel ChannelHandlerContext ChannelPipeline
                             ChannelInboundHandlerAdapter ChannelFuture
                             ChannelOption ChannelFutureListener
                             Channel ChannelHandler]
           [io.netty.handler.ssl SslHandler]
           [io.netty.channel.socket.nio NioDatagramChannel NioServerSocketChannel]
           [io.netty.channel.nio NioEventLoopGroup]
           [org.apache.commons.lang3 StringUtils]
           [java.net URL InetAddress InetSocketAddress]
           [java.io InputStream IOException]
           [io.netty.handler.codec.http HttpHeaders HttpMessage
                                        HttpContent
                                        HttpRequest
                                        HttpRequestDecoder
                                        HttpResponseEncoder]
          [io.netty.bootstrap Bootstrap ServerBootstrap]
          [io.netty.util ReferenceCountUtil]
          [io.netty.handler.codec.http.websocketx WebSocketServerProtocolHandler]
          [io.netty.handler.stream ChunkedWriteHandler]
          [javax.net.ssl KeyManagerFactory SSLContext SSLEngine TrustManagerFactory]
          [java.security KeyStore SecureRandom]
          [com.zotohlab.frwk.netty PipelineConfigurator
                                     RequestDecoder
                                     Expect100 AuxHttpDecoder
                                     ErrorCatcher]
          [com.zotohlab.frwk.netty NettyFW]
          [com.zotohlab.frwk.io XData]
          [com.zotohlab.frwk.net SSLTrustMgrFactory]
          [com.google.gson JsonObject JsonPrimitive] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(def ^:private ^String SERVERKEY "serverKey")
(def ^:private ^String PWD "pwd")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SSLServerHShake ""

  ^ChannelHandler
  [^JsonObject options]

  (let [ keyUrlStr (if (.has options SERVERKEY)
                     (-> (.get options SERVERKEY)
                         (.getAsString))
                     nil)
         pwdStr (if (.has options PWD)
                  (-> (.get options PWD)
                      (.getAsString))
                  nil) ]
    (when (hgl? keyUrlStr)
      (try
        (let [pwd (if (nil? pwdStr) nil (.toCharArray pwdStr))
              x (SSLContext/getInstance "TLS")
              ks (KeyStore/getInstance ^String (if (.endsWith keyUrlStr ".jks")
                                                 "JKS"
                                                 "PKCS12"))
              t (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
              k (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm)) ]
          (with-open [ inp (-> (URL. keyUrlStr) (.openStream)) ]
            (.load ks inp pwd)
            (.init t ks)
            (.init k ks pwd)
            (.init x
                   (.getKeyManagers k)
                   (.getTrustManagers t)
                   (SecureRandom/getInstance "SHA1PRNG"))
            (SslHandler. (doto (.createSSLEngine x)
                           (.setUseClientMode false)))))
        (catch Throwable e#
          (log/error e# ""))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SSLClientHShake ""

  ^ChannelHandler
  [^JsonObject options]

  (try
    (let [ ctx (doto (SSLContext/getInstance "TLS")
                 (.init nil (SSLTrustMgrFactory/getTrustManagers) nil)) ]
      (SslHandler. (doto (.createSSLEngine ctx)
                     (.setUseClientMode true))))
    (catch Throwable e#
      (log/error e# ""))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- demux-server-type ""
  [a & args]
  (cond
    (instance? ServerBootstrap a) :tcp-server
    (instance? Bootstrap a) :udp-server
    :else (ThrowIOE "Unknown server type")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Channel StartServer "" demux-server-type)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Channel StopServer "" demux-server-type)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isFormPost ""

  [^HttpMessage msg ^String method]

  (let [ct (-> (HttpHeaders/getHeader msg "content-type")
               nsb
               strim
               cstr/lower-case) ]
    ;; multipart form
    (and (or (= "POST" method)(= "PUT" method)(= "PATCH" method))
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doDemux ""

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^cmzlabclj.nucleus.util.core.MutableMap impl]

  (let [info (NettyFW/extractMsgInfo req)
        ch (.channel ctx)
        mt (-> info (.get "method")(.getAsString))
        uri (-> info (.get "uri")(.getAsString)) ]
    (log/debug "first level demux of message\n{}" req)
    (log/debug info)
    (NettyFW/setAttr ctx NettyFW/MSGINFO_KEY info)
    (NettyFW/setAttr ch NettyFW/MSGINFO_KEY info)
    (.setf! impl :delegate nil)
    (if (.startsWith uri "/favicon.") ;; ignore this crap
      (do
        (NettyFW/replyXXX ch 404)
        (.setf! impl :ignore true))
      (do
        (Expect100/handle100 ctx req)
        (if (isFormPost req mt)
          (do
            (.setf! impl :delegate (ReifyFormPostDecoderSingleton))
            (.addProperty info "formpost" true))
          (.setf! impl :delegate (ReifyRequestDecoderSingleton)))))
    (when-let [ ^AuxHttpDecoder d (.getf impl :delegate) ]
      (.channelReadXXX d ctx req))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyHttpHandler ""

  ^ChannelHandler
  []

  (let [impl (MakeMMap)]
    (.setf! impl :delegate nil)
    (.setf! impl :ignore false)
    (proxy [AuxHttpDecoder][]
      (channelRead0 [ctx msg]
        (let [ ^AuxHttpDecoder d (.getf impl :delegate)
               e (.getf impl :ignore) ]
          (log/debug "HttpDemuxer got msg = " (type msg))
          (cond
            (instance? HttpRequest msg)
            (doDemux ctx msg impl)

            (notnil? d)
            (.channelReadXXX d ctx msg)

            (true? e)
            nil ;; ignore

            :else
            (ThrowIOE (str "Fatal error while reading http message. " (type msg)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isWEBSock ""

  [^HttpRequest req]

  (let [ws (-> (HttpHeaders/getHeader req "upgrade")
               nsb
               strim
               cstr/lower-case)
        mo (-> (HttpHeaders/getHeader req "X-HTTP-Method-Override")
               nsb
               strim) ]
    (and (= "websocket" ws)
         (= "GET" (if (StringUtils/isNotEmpty mo)
                    mo
                    (-> req (.getMethod)(.name)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpDemuxer ""

  ^ChannelHandler
  [^JsonObject options hack]

  (let [ws (if (.has options "wsock")
             (.getAsJsonObject options "wsock")
             nil)
        uri (if-not (nil? ws)
              (-> ws
                  (.getAsJsonPrimitive "uri")
                  (.getAsString))
              "") ]
    (proxy [ChannelInboundHandlerAdapter][]
      (channelRead [ c obj]
        (log/debug "HttpDemuxer got this msg " (type obj))
        (let [^ChannelHandlerContext ctx c
              ^Object msg obj
              pipe (.pipeline ctx)
              ch (.channel ctx) ]
          (cond
            (and (instance? HttpRequest msg)
                 (isWEBSock msg))
            (do
              (.addAfter pipe
                         "HttpResponseEncoder"
                         "WebSocketServerProtocolHandler"
                         (WebSocketServerProtocolHandler. uri))
              (when-let [fc (:onwsock hack) ] (fc ctx hack options)))

            :else
            (do
              (.addAfter pipe
                         "HttpDemuxer"
                         "ReifyHttpHandler"
                         (reifyHttpHandler))
              (log/debug "Added new handler - reifyHttpHandler to the chain")
              (when-let [fc (:onhttp hack) ] (fc ctx hack options)) ))
          (.fireChannelRead ctx msg)
          (.remove pipe "HttpDemuxer"))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyHTTPPipe ""

  ^PipelineConfigurator
  [^String yourHandlerName yourHandlerFn]

  (proxy [PipelineConfigurator][]
    (assemble [pl options]
      (let [ssl (SSLServerHShake ^JsonObject options)
            ^ChannelPipeline pipe pl]
        (when-not (nil? ssl) (.addLast pipe "ssl" ssl))
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "HttpDemuxer" (MakeHttpDemuxer options {}))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
          (.addLast yourHandlerName
                    ^ChannelHandler (yourHandlerFn options))
          (ErrorCatcher/addLast))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer :tcp-server

  ^Channel
  [^ServerBootstrap bs
   ^String host
   port]

  (let [ ip (if (hgl? host) (InetAddress/getByName host)
              (InetAddress/getLocalHost)) ]
    (log/debug "netty-TCP-server: running on host " ip ", port " port)
    (try
      (-> (.bind bs ip (int port))
          (.sync)
          (.channel))
      (catch InterruptedException e#
        (throw (IOException. e#))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer :udp-server

  ^Channel
  [^Bootstrap bs
   ^String host
   port]

  (let [ ip (if (hgl? host) (InetAddress/getByName host)
              (InetAddress/getLocalHost)) ]
    (log/debug "netty-UDP-server: running on host " ip ", port " port)
    (-> (.bind bs ip (int port))
        (.channel))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StopServer :tcp-server

  [^ServerBootstrap bs ^Channel ch]

  (-> (.close ch)
      (.addListener (reify ChannelFutureListener
                      (operationComplete [_ cff]
                        (let [ gc (.childGroup bs)
                               gp (.group bs) ]
                          (when-not (nil? gp) (Try! (.shutdownGracefully gp)))
                          (when-not (nil? gc) (Try! (.shutdownGracefully gc)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StopServer :udp-server

  [^Bootstrap bs ^Channel ch]

  (-> (.close ch)
      (.addListener (reify ChannelFutureListener
                      (operationComplete [_ cff]
                        (let [ gp (.group bs) ]
                          (when-not (nil? gp) (Try! (.shutdownGracefully gp)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getEventGroup ""

  ^NioEventLoopGroup
  [^String group ^JsonObject options]

  (if (.has options group)
    (NioEventLoopGroup. (-> options
                            (.getAsJsonPrimitive group)
                            (.getAsInt)))
    (NioEventLoopGroup.)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitTCPServer ""

  ^ServerBootstrap
  [^PipelineConfigurator cfg
   ^JsonObject options]

  (doto (ServerBootstrap.)
    (.group (getEventGroup "bossThreads" options) (getEventGroup "workerThreads" options))
    (.channel NioServerSocketChannel)
    (.option ChannelOption/SO_REUSEADDR true)
    (.option ChannelOption/SO_BACKLOG (int 100))
    (.childOption ChannelOption/SO_RCVBUF (int (* 2 1024 1024)))
    (.childOption ChannelOption/TCP_NODELAY true)
    (.childHandler (.configure cfg options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitUDPServer ""

  ^Bootstrap
  [^PipelineConfigurator cfg
   ^JsonObject options]

  (doto (Bootstrap.)
    (.group (getEventGroup "bossThreads" options))
    (.channel NioDatagramChannel)
    (.option ChannelOption/TCP_NODELAY true)
    (.option ChannelOption/SO_RCVBUF (int (* 2  1024 1024)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private io-eof nil)

