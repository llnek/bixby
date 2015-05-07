;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.netty.io

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core
         :only
         [ThrowIOE MakeMMap notnil? spos?
          TryC Try! SafeGetJsonObject
          SafeGetJsonInt SafeGetJsonString]]
        [czlabclj.xlib.util.str
         :only
         [lcase ucase strim nsb hgl?]]
        [czlabclj.xlib.netty.request]
        [czlabclj.xlib.netty.form])

  (:import  [io.netty.channel ChannelHandlerContext ChannelPipeline
             ChannelInboundHandlerAdapter ChannelFuture
             ChannelOption ChannelFutureListener
             Channel ChannelHandler]
            [io.netty.handler.ssl SslHandler]
            [io.netty.buffer Unpooled]
            [io.netty.channel.socket.nio NioDatagramChannel
             NioServerSocketChannel]
            [io.netty.channel.nio NioEventLoopGroup]
            [org.apache.commons.lang3 StringUtils]
            [java.net URL InetAddress InetSocketAddress]
            [java.io InputStream IOException]
            [java.util Map Map$Entry]
            [io.netty.handler.codec.http HttpHeaders HttpMessage
             HttpHeaders$Values
             HttpHeaders$Names
             LastHttpContent DefaultFullHttpResponse
             DefaultFullHttpRequest HttpContent
             HttpRequest HttpResponse FullHttpRequest
             QueryStringDecoder HttpResponseStatus
             HttpRequestDecoder HttpVersion
             HttpResponseEncoder]
           [io.netty.bootstrap Bootstrap ServerBootstrap]
           [io.netty.util CharsetUtil ReferenceCountUtil]
           [io.netty.handler.codec.http.websocketx
            WebSocketServerProtocolHandler]
           [io.netty.handler.stream ChunkedWriteHandler]
           [javax.net.ssl KeyManagerFactory SSLContext
            SSLEngine TrustManagerFactory]
           [java.security KeyStore SecureRandom]
           [com.zotohlab.frwk.netty PipelineConfigurator
            RequestFilter
            Expect100Filter AuxHttpFilter
            ErrorSinkFilter]
           [com.zotohlab.frwk.netty NettyFW]
           [com.zotohlab.frwk.core CallableWithArgs]
           [com.zotohlab.frwk.io XData]
           [com.zotohlab.frwk.net SSLTrustMgrFactory]
           [com.google.gson JsonObject JsonPrimitive] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FutureCB "Register a callback upon operation completion."

  [^ChannelFuture cf func]

  (.addListener cf (reify ChannelFutureListener
                     (operationComplete [_ ff]
                       (Try! (apply func (.isSuccess ^ChannelFuture ff) []))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseFuture "Close the channel."

  [^ChannelFuture cf]

  (.addListener cf ChannelFutureListener/CLOSE))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WriteLastContent "Write out the last content flag."

  ^ChannelFuture
  [^Channel ch flush?]

  (log/debug "Writing last http-content out to client.")
  (if flush?
    (.writeAndFlush ch LastHttpContent/EMPTY_LAST_CONTENT)
    (.write ch LastHttpContent/EMPTY_LAST_CONTENT)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExtractHeaders "Return the headers in a map."

  [^HttpHeaders hdrs]

  (with-local-vars [rc (transient {})]
    (doseq [^String n (.names hdrs) ]
      (var-set rc (assoc! @rc
                          (lcase n)
                          (vec (.getAll hdrs n)))))
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetHeader "Set the header value."

  [^HttpMessage msg ^String nm
   ^String value]

  (HttpHeaders/setHeader msg nm value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHeader "Get the header value."

  ^String
  [^HttpMessage msg ^String nm]

  (HttpHeaders/getHeader msg nm))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExtractParams "Return the parameters in a map."

  [^QueryStringDecoder decr]

  (with-local-vars [rc (transient {})]
    (doseq [^Map$Entry en
            (-> decr (.parameters)(.entrySet))]
      (var-set rc (assoc! @rc
                          (.getKey en)
                          (vec (.getValue en)))))
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExtractMsgInfo "Pick out pertinent information from the message,
                      can be request or response."

  [^HttpMessage msg]

  (with-local-vars [rc (transient {})]
    (var-set rc (assoc! @rc :isChunked (HttpHeaders/isTransferEncodingChunked msg)))
    (var-set rc (assoc! @rc :keepAlive (HttpHeaders/isKeepAlive msg)))
    (var-set rc (assoc! @rc :host (HttpHeaders/getHeader msg "Host" "")))
    (var-set rc (assoc! @rc :protocol (nsb (.getProtocolVersion msg))))
    (var-set rc (assoc! @rc :clen (HttpHeaders/getContentLength msg  0)))
    (var-set rc (assoc! @rc :uri2 ""))
    (var-set rc (assoc! @rc :uri ""))
    (var-set rc (assoc! @rc :status ""))
    (var-set rc (assoc! @rc :method ""))
    (var-set rc (assoc! @rc :query ""))
    (var-set rc (assoc! @rc :wsock false))
    (var-set rc (assoc! @rc :code 0))
    (var-set rc (assoc! @rc :params {}))
    (var-set rc (assoc! @rc :headers (ExtractHeaders (.headers msg))))
    (cond
      (instance? HttpResponse msg)
      (let [s (.getStatus ^HttpResponse msg) ]
        (var-set rc (assoc! @rc :status (nsb (.reasonPhrase s))))
        (var-set rc (assoc! @rc :code (.code s))))

      (instance? HttpRequest msg)
      (let [mo (HttpHeaders/getHeader msg "X-HTTP-Method-Override")
            ^HttpRequest req  msg
            uriStr (nsb (.getUri req))
            pos (.indexOf uriStr (int \?))
            md (-> req (.getMethod)(.name))
            mt (if (hgl? mo) mo md)
            dc (QueryStringDecoder. uriStr) ]
        (var-set rc (assoc! @rc :method (ucase mt)))
        (var-set rc (assoc! @rc :params (ExtractParams dc)))
        (var-set rc (assoc! @rc :uri (.path dc)))
        (var-set rc (assoc! @rc :uri2 uriStr))
        (when (>= pos 0)
          (var-set rc (assoc! @rc :query (.substring uriStr pos)))))

      :else nil)
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setContentLength ""

  [^ChannelHandlerContext ctx clen]

  (let [info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY)
        olen (:clen info) ]
    (when-not (== olen clen)
      (log/warn "Content-length read from headers = " olen ", new clen = " clen)
      (NettyFW/setAttr ctx NettyFW/MSGINFO_KEY (assoc info :clen clen)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- appendHeaders ""

  [^ChannelHandlerContext ctx
   ^HttpHeaders hds]

  (let [info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY)
        old (:headers info)
        nnw (ExtractHeaders hds) ]
    (NettyFW/setAttr ctx
                     NettyFW/MSGINFO_KEY
                     (assoc info
                            :headers
                            (merge {} old nnw)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyMsgFunc ""

  ^CallableWithArgs
  []

  (reify CallableWithArgs
    (run [_ args]
      ;; args === array of objects
      (let [^ChannelHandlerContext ctx (aget args 0)
            ^String op (aget args 1) ]
        (condp = op
          "setContentLength" (setContentLength ctx (aget args 2))
          "appendHeaders" (appendHeaders ctx (aget args 2))
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fakeFullHttpRequest ""

  ^FullHttpRequest
  [^HttpRequest req]

  (let [rc (DefaultFullHttpRequest. (.getProtocolVersion req)
                                    (.getMethod req)
                                    (.getUri req)) ]
    (-> (.headers rc)
        (.set (.headers req)))
    ;;(-> (.trailingHeaders rc) (.set (.trailingHeaders req)))
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SSLServerHShake "Create a server-side handler for SSL."

  ^ChannelHandler
  [options]

  (let [^String keyUrlStr (:serverKey options)
        ^String pwdStr (:passwd options) ]
    (when (hgl? keyUrlStr)
      (TryC
        (let [pwd (when-not (nil? pwdStr) (.toCharArray pwdStr))
              x (SSLContext/getInstance "TLS")
              ks (KeyStore/getInstance ^String
                                       (if (.endsWith keyUrlStr ".jks")
                                         "JKS"
                                         "PKCS12"))
              t (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
              k (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm)) ]
          (with-open [inp (-> (URL. keyUrlStr)
                              (.openStream)) ]
            (.load ks inp pwd)
            (.init t ks)
            (.init k ks pwd)
            (.init x
                   (.getKeyManagers k)
                   (.getTrustManagers t)
                   (SecureRandom/getInstance "SHA1PRNG"))
            (SslHandler. (doto (.createSSLEngine x)
                           (.setUseClientMode false)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SSLClientHShake "Create a client side handler for SSL."

  ^ChannelHandler
  [options]

  (TryC
    (let [ctx (doto (SSLContext/getInstance "TLS")
                    (.init nil (SSLTrustMgrFactory/getTrustManagers) nil)) ]
      (SslHandler. (doto (.createSSLEngine ctx)
                         (.setUseClientMode true))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- demux-server-type ""

  [a & args]

  (cond
    (instance? ServerBootstrap a)
    :tcp-server
    (instance? Bootstrap a)
    :udp-server
    :else
    (ThrowIOE "Unknown server type")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Channel StartServer "Start a Netty server." demux-server-type)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Channel StopServer "Stop a Netty server." demux-server-type)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isFormPost "Determine if this request is a http form post."

  [^HttpMessage msg ^String method]

  (let [ct (-> (GetHeader msg HttpHeaders$Names/CONTENT_TYPE)
               nsb strim lcase) ]
    (and (or (= "POST" method)(= "PUT" method)(= "PATCH" method))
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onFormPost "" [info]
  (NettyFW/setAttr ctx
                   NettyFW/MSGINFO_KEY (assoc info :formpost true))
  (ReifyFormPostFilterSingleton))

(defn- doDemux "Detect and handle a FORM post or a normal request."

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^czlabclj.xlib.util.core.Muble impl]

  (let [info (ExtractMsgInfo req)
        {:keys [method uri]}
        info
        ch (.channel ctx)]
    (log/debug "Demux of message\n{}\n\n{}" req info)
    (doto ctx
      (NettyFW/setAttr NettyFW/MSGFUNC_KEY (reifyMsgFunc))
      (NettyFW/setAttr NettyFW/MSGINFO_KEY info))
    (.setf! impl :delegate nil)
    (if (.startsWith (nsb uri) "/favicon.")
      (do
        ;; ignore this crap
        (NettyFW/replyXXX ch 404)
        (.setf! impl :ignore true))
      (do
        (Expect100Filter/handle100 ctx req)
        (.setf! impl
                :delegate
                (if (isFormPost req method)
                  (onFormPost info)
                  (ReifyRequestFilterSingleton)))))
    (when-let [d (.getf impl :delegate) ]
      (-> ^AuxHttpFilter d
          (.channelReadXXX ctx req)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyHttpFilter "Filter to sort out standard request or formpost."

  ^ChannelHandler
  []

  (let [impl (MakeMMap {:delegate nil
                        :ignore false}) ]
    (proxy [AuxHttpFilter][]
      (channelRead0 [ctx msg]
        (let [d (.getf impl :delegate)
              e (.getf impl :ignore) ]
          (log/debug "HttpHandler got msg = " (type msg))
          (log/debug "HttpHandler delegate = " d)
          (cond
            (instance? HttpRequest msg)
            (doDemux ctx msg impl)

            (notnil? d)
            (-> ^AuxHttpFilter d
                (.channelReadXXX ctx msg))

            (true? e)
            nil ;; ignore

            :else
            (ThrowIOE (str "Fatal error while reading http message. " (type msg)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isWEBSock "Detect if request is a websocket request."

  [^HttpRequest req]

  (let [^String cn (-> (GetHeader req HttpHeaders$Names/CONNECTION)
                       nsb strim lcase)
        ^String ws (-> (GetHeader req HttpHeaders$Names/UPGRADE)
                       nsb strim lcase)
        ^String mo (-> (GetHeader req "X-HTTP-Method-Override")
                       nsb strim) ]
    (and (>= (.indexOf ws "websocket") 0)
         (>= (.indexOf cn "upgrade") 0)
         (= "GET" (if-not (hgl? mo)
                    (-> (.getMethod req)
                        (.name))
                    mo)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- exitHttpDemuxFilter "Send msg upstream and remove the filter."

  [^ChannelHandlerContext ctx msg]

  (do
    (.fireChannelRead ctx msg)
    (.remove (.pipeline ctx) "HttpDemuxFilter")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpDemuxFilter "First level filter, detects websock or normal http."

  (^ChannelHandler [options ] (MakeHttpDemuxFilter options {}))

  (^ChannelHandler [options hack]

    (let [ws (:wsock options)
          uri (:uri ws)
          tmp (MakeMMap) ]
      (proxy [ChannelInboundHandlerAdapter][]
        (channelRead [c obj]
          (log/debug "HttpDemuxFilter got this msg " (type obj))
          (let [^ChannelHandlerContext ctx c
                ^Object msg obj
                pipe (.pipeline ctx)
                ch (.channel ctx) ]
            (cond
              (and (instance? HttpRequest msg)
                   (isWEBSock msg))
              (do
                ;; wait for full request
                (log/debug "Got a websock req - let's wait for full msg.")
                (.setf! tmp :wsreq (fakeFullHttpRequest msg))
                (.setf! tmp :wait4wsock true)
                (ReferenceCountUtil/release msg))

              (true? (.getf tmp :wait4wsock))
              (try
                (when (instance? LastHttpContent msg)
                  (log/debug "Got a wsock upgrade request for uri "
                             uri
                             ", swapping to netty's websock handler.")
                  (.addAfter pipe
                             "HttpResponseEncoder"
                             "WebSocketServerProtocolHandler"
                             (WebSocketServerProtocolHandler. uri))
                  ;; maybe do something extra when wsock? caller decide...
                  (-> (or (:onwsock hack) (constantly nil))
                      (apply ctx hack options []))
                  (exitHttpDemuxFilter ctx (.getf tmp :wsreq)))
                (finally
                  (ReferenceCountUtil/release msg)))

              :else
              (do
                (log/debug "Standard http request - swap in our own http handler.")
                (.addAfter pipe
                           "HttpDemuxFilter"
                           "ReifyHttpFilter"
                           (reifyHttpFilter))
                ;; maybe do something extra? caller decide...
                (-> (or (:onhttp hack) (constantly nil))
                    (apply ctx hack options []))
                (log/debug "Added new handler - reifyHttpFilter to the chain")
                (exitHttpDemuxFilter ctx msg))))))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyHTTPPipe "Create a standard request pipeline."

  ^PipelineConfigurator
  [^String yourHandlerName yourHandlerFn]

  (proxy [PipelineConfigurator][]
    (assemble [pl options]
      (let [ssl (SSLServerHShake options)
            ^ChannelPipeline pipe pl]
        (when-not (nil? ssl) (.addLast pipe "ssl" ssl))
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "HttpDemuxFilter" (MakeHttpDemuxFilter options))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
          (.addLast yourHandlerName
                    ^ChannelHandler (yourHandlerFn options))
          (ErrorSinkFilter/addLast))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer :tcp-server

  ^Channel
  [^ServerBootstrap bs
   ^String host
   port]

  (let [ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost)) ]
    (log/debug "Netty-TCP-server: running on host " ip ", port " port)
    (try
      (-> (.bind bs ip (int port))
          (.sync)
          (.channel))
      (catch InterruptedException e#
        (ThrowIOE e#)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer :udp-server

  ^Channel
  [^Bootstrap bs
   ^String host
   port]

  (let [ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost)) ]
    (log/debug "Netty-UDP-server: running on host " ip ", port " port)
    (-> (.bind bs ip (int port))
        (.channel))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StopServer :tcp-server

  [^ServerBootstrap bs
   ^Channel ch]

  (FutureCB (.close ch)
            (fn [_]
              (let [gc (.childGroup bs)
                    gp (.group bs) ]
                (when-not (nil? gc) (Try! (.shutdownGracefully gc)))
                (when-not (nil? gp) (Try! (.shutdownGracefully gp)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StopServer :udp-server

  [^Bootstrap bs
   ^Channel ch]

  (FutureCB (.close ch)
            (fn [_]
              (let [gp (.group bs) ]
                (when-not (nil? gp) (Try! (.shutdownGracefully gp)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getEventGroup ""

  ^NioEventLoopGroup
  [thds]

  (if (spos? thds)
    (NioEventLoopGroup. thds)
    (NioEventLoopGroup.)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitTCPServer "Create and configure a TCP Netty Server."

  ^ServerBootstrap
  [^PipelineConfigurator cfg
   options]

  (doto (ServerBootstrap.)
    (.group (getEventGroup (:bossThreads options))
            (getEventGroup (:workerThreads options)))
    (.channel NioServerSocketChannel)
    (.option ChannelOption/SO_REUSEADDR true)
    (.option ChannelOption/SO_BACKLOG
             (int (or (:backlog options) 100)))
    (.childOption ChannelOption/SO_RCVBUF
                  (int (or (:rcvBuf options)
                           (* 2 1024 1024))))
    (.childOption ChannelOption/TCP_NODELAY true)
    (.childHandler (.configure cfg options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitUDPServer "Create and configure a UDP Netty Server."

  ^Bootstrap
  [^PipelineConfigurator cfg
   options]

  (doto (Bootstrap.)
    (.group (getEventGroup (:bossThreads options)))
    (.channel NioDatagramChannel)
    (.option ChannelOption/TCP_NODELAY true)
    (.option ChannelOption/SO_RCVBUF
             (int (or (:rcvBuf options)
                      (* 2 1024 1024))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private io-eof nil)

