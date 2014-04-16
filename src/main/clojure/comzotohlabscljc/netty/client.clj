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

  comzotohlabscljc.netty.client )

(use '[clojure.tools.logging :only [info warn error debug] ])

(import '(java.nio.charset Charset))
(import '(java.lang.reflect Field))
(import '(org.apache.commons.io IOUtils FileUtils))
(import '(org.apache.commons.lang3 StringUtils))
(import '(java.io IOException ByteArrayOutputStream
  File OutputStream InputStream))
(import '(java.util Map$Entry HashMap Properties ArrayList))
(import '(java.net URI URL InetSocketAddress))
(import '(java.util.concurrent Executors))
(import '(javax.net.ssl SSLEngine SSLContext))
(import '(javax.net.ssl X509TrustManager TrustManager))
(import '( com.zotoh.frwk.net ULFileItem))

(import '(io.netty.bootstrap Bootstrap ServerBootstrap))
(import '(io.netty.util AttributeKey Attribute))
(import '(io.netty.util.concurrent GlobalEventExecutor))
(import '(io.netty.channel.nio NioEventLoopGroup))
(import '(io.netty.buffer ByteBuf ByteBufHolder Unpooled))
(import '(io.netty.handler.codec.http.multipart
  DefaultHttpDataFactory FileUpload HttpPostRequestDecoder
  HttpPostRequestDecoder$EndOfDataDecoderException
  InterfaceHttpData InterfaceHttpData$HttpDataType))
(import '(io.netty.handler.stream
  ChunkedWriteHandler ChunkedStream))
(import '(io.netty.channel.socket.nio
  NioServerSocketChannel NioSocketChannel))
(import '(io.netty.channel
  ChannelHandlerContext Channel SimpleChannelInboundHandler
  ChannelFutureListener ChannelFuture ChannelInitializer
  ChannelPipeline ChannelHandler ChannelOption))
(import '(io.netty.channel.socket SocketChannel))
(import '(io.netty.channel.group
  ChannelGroupFuture ChannelGroup ChannelGroupFutureListener
  DefaultChannelGroup))
(import '(io.netty.handler.codec.http
  HttpHeaders HttpVersion HttpContent LastHttpContent
  HttpHeaders$Values HttpHeaders$Names
  HttpMessage HttpRequest HttpResponse HttpResponseStatus
  DefaultFullHttpResponse DefaultHttpResponse QueryStringDecoder
  HttpMethod HttpObject
  DefaultHttpRequest HttpServerCodec HttpClientCodec
  HttpResponseEncoder))
(import '(io.netty.handler.ssl SslHandler))
(import '(io.netty.handler.codec.http.websocketx
  WebSocketFrame
  WebSocketServerHandshaker
  WebSocketServerHandshakerFactory
  ContinuationWebSocketFrame
  CloseWebSocketFrame
  BinaryWebSocketFrame
  TextWebSocketFrame
  PingWebSocketFrame
  PongWebSocketFrame))
(import '(com.zotoh.frwk.net NetUtils))
(import '(com.zotoh.frwk.io XData))

(use '[comzotohlabscljc.crypto.ssl :only [make-sslContext make-sslClientCtx] ])
(use '[comzotohlabscljc.net.rts])
(use '[comzotohlabscljc.util.files :only [save-file get-file] ])
(use '[comzotohlabscljc.util.core :only [uid notnil? Try! TryC] ])
(use '[comzotohlabscljc.util.str :only [strim nsb hgl?] ])
(use '[comzotohlabscljc.util.io :only [make-baos] ])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; main netty classes

(defrecord NettyClient [^Bootstrap client ^ChannelGroup cgroup ] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- generic-handler "Make a generic Netty 4.x Pipeline Handler."

  ^ChannelHandler
  [options]

  (proxy [SimpleChannelInboundHandler] []

    (exceptionCaught [ctx err]
      (let [ ch (.channel ^ChannelHandlerContext ctx)
             ucb (:usercb options) ]
        (error err "")
        (when-not (nil? ucb)
          (.onerror ^comzotohlabscljc.netty.comms.NettyServiceIO
                    ucb ch nil err))
        (Try! (NetUtils/closeChannel ch))))

    (channelRead0 [ctx msg]
      ;;(debug "pipeline-entering with msg: " (type msg))
      (cond
        (instance? LastHttpContent msg)
        (handle-chunk ctx msg)

        (instance? HttpResponse msg)
        (handle-response ctx msg options)

        (instance? HttpContent msg)
        (handle-chunk ctx msg)

        (nil? msg)
        (throw (IOException. "Got null object."))

        :else
        (throw (IOException. (str "Got object: " (class msg))) )))

    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizClient ""

  ^ChannelHandler
  [options]

  (proxy [ChannelInitializer][]
    (initChannel [^SocketChannel ch]
      (let [ ssl (= (.getProtocol ^URL (:targetUrl options)) "https")
             ^ChannelPipeline pl (NetUtils/getPipeline ch)
             ^SSLContext ctx (make-sslClientCtx ssl)
             eg (if (notnil? ctx)
                    (doto (.createSSLEngine ctx)
                          (.setUseClientMode true))) ]
        (when-not (nil? eg) (.addLast pl "ssl" (SslHandler. eg)))
          ;;(.addLast "decoder" (HttpRequestDecoder.))
          ;;(.addLast "encoder" (HttpResponseEncoder.))
        (.addLast pl "codec" (HttpClientCodec.))
        (.addLast pl "chunker" (ChunkedWriteHandler.))
        (.addLast pl "handler" (generic-handler options))
        pl))))

(defn- makeClientNetty ""

  ([] (makeClientNetty {}))

  ([options]
   (let [ g (NioEventLoopGroup.)
          opts (:netty options)
          bs (doto (Bootstrap.)
                   (.group g)
                   (.channel io.netty.channel.socket.nio.NioSocketChannel)
                   (.option ChannelOption/TCP_NODELAY true)
                   (.option ChannelOption/SO_KEEPALIVE true))
          cg (DefaultChannelGroup. (uid) GlobalEventExecutor/INSTANCE) ]
     (doseq [ [k v] (seq opts) ]
       (.option bs k v))
     (.handler bs (inizClient options))
     (comzotohlabscljc.netty.client.NettyClient bs cg)
     )))

(defn- connectClient "Make a Netty 4.x client."

  ([^URL targetUrl] (connectClient {}))

  ([^URL targetUrl options]
   (let [ opts (merge { :usercb (MakeNilServiceIO)
                        :netty {}
                        :targetUrl targetUrl }
                      options)
          ssl (= "https" (.getProtocol targetUrl))
          pnum (.getPort targetUrl)
          port (if (< pnum 0) (if ssl 443 80) pnum)
          host (.getHost targetUrl)
          sock (InetSocketAddress. host (int port))
          ucb (:usercb opts)
          nc (makeClientNetty opts)
          ^Bootstrap bs (:client nc)
          ^ChannelFuture cf (-> bs
                                (.connect sock)
                                (.sync))
          ok (.isSuccess cf)
          ch (if ok (.channel cf))
          e (if (not ok) (.cause cf)) ]
     (when-not ok
       (if (nil? e)
           (throw (IOException. "Failed to connect to URL: " targetUrl))
           (throw e)))
     (.add ^ChannelGroup (:cgroup nc) (.channel cf))
     (debug "Netty client connected to " host ":" port " - OK.")
     [nc ch opts] )))

(defn- send-httpClient ""

  [^URL targetUrl ^XData xdata options]

  (let [ clen (if (nil? xdata) 0 (.size xdata))
         mo (:override options)
         md (if (> clen 0)
              (if (hgl? mo) mo "POST")
              (if (hgl? mo) mo "GET"))
         req (DefaultHttpRequest. HttpVersion/HTTP_1_1
                                  (HttpMethod/valueOf md)
                                  (nsb targetUrl))
         [nc ^Channel ch opts] (connectClient targetUrl options)
         ka (:keepAlive opts)
         ^comzotohlabscljc.netty.comms.NettyServiceIO
         ucb (:usercb opts) ]
    (HttpHeaders/setHeader req "Connection" (if ka "keep-alive" "close"))
    (HttpHeaders/setHeader req "host" (.getHost targetUrl))
    (.presend ucb ch req)
    (let [ ct (HttpHeaders/getHeader req "content-type") ]
      (when (and (StringUtils/isEmpty ct)
                 (> clen 0))
        (HttpHeaders/setHeader req "content-type" "application/octet-stream")) )
    (HttpHeaders/setContentLength req clen)
    (debug "Netty client: about to flush out request (headers)")
    (debug "Netty client: content has length " clen)
    (with-local-vars [wf nil]
      (var-set wf (WWrite ch req))
      (if (> clen 0)
        (var-set wf (if (> clen (com.zotoh.frwk.io.IOUtils/streamLimit))
                      (WFlush ch (ChunkedStream. (.stream xdata)))
                      (WFlush ch (Unpooled/wrappedBuffer (.javaBytes xdata)))))
        (NetUtils/flush ch))
      (CloseCF ka @wf))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async HTTP Post
(defn AsyncPost "Async HTTP Post"

  ([^URL targetUrl ^XData xdata options]
   (send-httpClient targetUrl xdata options))

  ([^URL targetUrl ^XData xdata]
   (AsyncPost targetUrl xdata {})) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async HTTP Get
(defn AsyncGet "Async HTTP GET"

  ([^URL targetUrl] (AsyncGet targetUrl {}))

  ([^URL targetUrl options]
   (send-httpClient targetUrl nil options)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private client-eof nil)



