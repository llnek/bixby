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

  comzotohlabscljc.netty.server )

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
;; Netty NIO Server
(defn BootstrapNetty ""

  ;; map
  [initor options]

  (let [ gp (NioEventLoopGroup.) gc (NioEventLoopGroup.)
         bs (doto (ServerBootstrap.)
                  (.group gp gc)
                  (.channel io.netty.channel.socket.nio.NioServerSocketChannel)
                  (.option ChannelOption/SO_REUSEADDR true)
                  (.option ChannelOption/SO_BACKLOG 100)
                  (.childOption ChannelOption/SO_RCVBUF (int (* 2 1024 1024)))
                  (.childOption ChannelOption/TCP_NODELAY true))
         opts (if-let [ x (:netty options) ] x {} ) ]
    (doseq [ [k v] (seq opts) ]
      (if (= :child k)
        (doseq [ [x y] (seq v) ]
          (.childOption bs x y))
        (.option bs k v)))
    (.childHandler bs (initor options))
    { :bootstrap bs }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; start netty on host/port
(defn StartNetty ""

  [^String host port netty]

  (let [ ^InetAddress ip (if (nichts? host)
              (InetAddress/getLocalHost)
              (InetAddress/getByName host))
         ch (-> (:bootstrap netty)
                (.bind (InetSocketAddress. ip (int port)))
                (.sync)
                (.channel)) ]
    (debug "netty-xxx-server: running on host " ip ", port " port)
    (merge netty { :channel ch })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StopNetty "Clean up resources used by a netty server."

  [netty]

  (let [ ^ServerBootstrap bs (:bootstrap netty)
         gc (.childGroup bs)
         gp (.group bs)
         ^Channel ch (:channel netty) ]
    (-> (.close ch)
        (.addListener (reify ChannelFutureListener
                        (operationComplete [_ cff]
                          (when-not (nil? gp) (Try! (.shutdownGracefully gp)))
                          (when-not (nil? gc) (Try! (.shutdownGracefully gc))) )) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private server-eof nil)

