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

  comzotohlabscljc.netty.comms )

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

(declare handle-chunk)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol NettyServiceIO
  ""
  (onreq [_ ch req msginfo xdata] )
  (presend [_ ch msg] )
  (onerror [_ ch msginfo err] )
  (onres [_ ch rsp msginfo xdata] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeNilServiceIO "Reify a no-op service-io object."

  ^comzotohlabscljc.netty.comms.NettyServiceIO
  []

  (reify NettyServiceIO
    (presend [_ ch msg] (debug "empty pre-send." ))
    (onerror [_ ch msginfo err] (debug "empty onerror." ))
    (onreq [_ ch req msginfo xdata] (debug "empty onreq." ))
    (onres [_ ch rsp msginfo xdata] (debug "empty onres." ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseCF "Maybe close the channel."

  [doit ^ChannelFuture cf]

  (if (and doit (notnil? cf))
    (.addListener cf ChannelFutureListener/CLOSE)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WWrite "Write object but no flush."

  ^ChannelFuture
  [^Channel ch obj]

  ;; had to do this to work-around reflection warnings :(
  (NetUtils/writeOnly ch obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WFlush "Write object and then flush."

  ^ChannelFuture
  [^Channel ch obj]

  ;; had to do this to work-around reflection warnings :(
  (NetUtils/wrtFlush ch obj))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeSSL ""

  [^ChannelHandlerContext ctx]

  (notnil? (-> (NetUtils/getPipeline ctx)
               (.get (class SslHandler)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply "Make a netty http-response object."

  (^HttpResponse [status ^ByteBuf obj]
    (DefaultFullHttpResponse. HttpVersion/HTTP_1_1 (get HTTP-CODES status) obj))

  (^HttpResponse [] (MakeFullHttpReply 200))

  (^HttpResponse [status]
    (DefaultFullHttpResponse. HttpVersion/HTTP_1_1 (get HTTP-CODES status))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpReply "Make a netty http-response object."

  (^HttpResponse [] (MakeHttpReply 200))

  (^HttpResponse [status] 
     (DefaultHttpResponse. HttpVersion/HTTP_1_1 (get HTTP-CODES status))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SendRedirect "Redirect a request."

  [^Channel ch perm ^String targetUrl]

  (let [ rsp (MakeFullHttpReply (if perm 301 307)) ]
    (debug "redirecting to -> " targetUrl)
    (HttpHeaders/setHeader rsp  "location" targetUrl)
    (CloseCF true (WFlush ch rsp))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Continue100 "Send back 100-continue."

  [^ChannelHandlerContext ctx]

  (-> (.channel ctx) (WFlush (MakeFullHttpReply 100))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sa-map! "Set attachment object from the channel."

  [^Channel ch akey obj]

  (-> (.attr ch akey) (.set obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ga-map "Get attachment object from the channel."

  [^Channel ch akey]

  (-> (.attr ch akey) (.get)))







;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private comms-eof nil)



