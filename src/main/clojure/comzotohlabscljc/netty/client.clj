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
;;
(defn BootstrapNettyClient

  ;; map
  [initor options]

  (let [ g (NioEventLoopGroup.)
         bs (doto (Bootstrap.)
                  (.group g)
                  (.channel io.netty.channel.socket.nio.NioSocketChannel)
                  (.option ChannelOption/TCP_NODELAY true)
                  (.option ChannelOption/SO_KEEPALIVE true))
         opts (:netty options) ]
    (doseq [ [k v] (seq opts) ]
      (.option bs k v))
    (.handler bs (initor options))

    { :bootstrap bs }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConnectNettyClient "Start Netty 4.x client."

  [^URL targetUrl netty]

  (let [ ssl (= "https" (.getProtocol targetUrl))
         pnum (.getPort targetUrl)
         port (if (< pnum 0) (if ssl 443 80) pnum)
         host (.getHost targetUrl)
         sock (InetSocketAddress. host (int port))
         ^ChannelFuture cf (-> (:bootstrap netty)
                               (.connect sock)
                               (.sync)) ]
    (when-not (.isSuccess cf)
      (throw (if-let [ eee (.cause cf) ]
                     eee
                     (IOException. (str "Connect failed: " targetUrl)))))
    (merge netty { :channel (.channel cf) })
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sendHttpClient ""

  [netty ^String verb ^XData xdata options]

  (let [ clen (if (nil? xdata) 0 (.size xdata))
         targetUrl (:targetUrl options)
         mo (:override options)
         md (if (> clen 0)
              (if (hgl? mo) "POST")
              (if (hgl? mo) mo "GET"))
         mt (if-let [mo mo] mo md)
         req (DefaultHttpRequest. HttpVersion/HTTP_1_1
                                  (HttpMethod/valueOf mt)
                                  (nsb targetUrl))
         presend (:presend options) ]

    (HttpHeaders/setHeader req "Connection" (if (:keep-alive options) "keep-alive" "close"))
    (HttpHeaders/setHeader req "host" (.getHost targetUrl))
    (when (fn? presend) (presend (:channel netty) req))

    (let [ ct (HttpHeaders/getHeader req "content-type") ]
      (when (and (StringUtils/isEmpty ct)
                 (> clen 0))
        (HttpHeaders/setHeader req "content-type" "application/octet-stream")))

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
      (CloseCF @wf (:keep-alive options) ))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async HTTP Post
(defn AsyncPost "Async HTTP Post"

  ([ netty ^XData xdata options ]
   (sendHttpClient "POST" netty xdata options))

  ([ netty ^XData xdata ]
   (AsyncPost netty xdata {})) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async HTTP Get
(defn AsyncGet "Async HTTP GET"

  ([ netty ] (AsyncGet netty {}))

  ([ netty options ]
   (sendHttpClient "GET" netty nil options)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private client-eof nil)


