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

  comzotohlabscljc.netty.snooper )

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeReply ""

  [^ChannelHandlerContext ctx
   ^HttpContent curObj
   ^StringBuilder cookieBuf
   ^StringBuilder buf]

  (let [ ka (-> (.attr ctx (AttributeKey. "keepalive"))(.get))
         response (DefaultFullHttpResponse.
                    (HttpVersion/HTTP_1_1)
                    (HttpResponseStatus/OK)
                    (Unpooled/copiedBuffer (.toString buf) (CharsetUtil/UTF_8)))
         clen (-> response (.content)(.readableBytes)) ]

    (-> response (.headers)(.set "content-type" "text/plain; charset=UTF-8"))
    (-> response (.headers)(.set "content-length" clen))

    (when ka (-> response (.headers)(.set "connection" "keep-alive")))

    (let [ cs (CookieDecoder/decode (.toString cookieBuf)) ]
      (if (.isEmpty cs)
        (do
          (-> response (.headers)(.add "set-cookie" (ServerCookieEncoder/encode "key1" "value1")))
          (-> response (.headers)(.add "set-cookie" (ServerCookieEncoder/encode "key2" "value2"))))
        (doseq [ v (seq cs) ]
          (-> response (.headers)(.add "set-cookie" (ServerCookieEncoder/encode v))))))

    (.write ctx response)
    (.setLength cookieBuf 0)
    (.setLength buf 0)

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleReq ""

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^StringBuilder cookieBuf
   ^StringBuilder buf]

  (let [ dc (QueryStringDecoder. (.getUri req))
         ka (HttpHeaders/isKeepAlive req)
         headers (.headers req)
         pms (.parameters dc) ]
    (-> (.attr ctx (AttributeKey. "keepalive"))(.set ka))
    (doto buf
      (.append "WELCOME TO THE WILD WILD WEB SERVER\r\n")
      (.append "===================================\r\n")
      (.append "VERSION: ")
      (.append (.getProtocolVersion req))
      (.append "\r\n")
      (.append "HOSTNAME: ")
      (.append (HttpHeaders/getHost req "unknown"))
      (.append "\r\n")
      (.append "REQUEST_URI: ")
      (.append (.getUri req))
      (.append "\r\n\r\n"))
    (reduce (fn [memo ^String n]
              (doto buf
                (.append "HEADER: ")
                (.append n)
                (.append " = ")
                (.append (clojure.string/join "," (.getAll headers n)))
                (.append "\r\n")))
            nil
            (.names headers))
    (.append buf "\r\n")
    (reduce (fn [memo ^Map$Entry en]
              (doto buf
                (.append "PARAM: ")
                (.append (.getKey en))
                (.append " = ")
                (.append (clojure.string/join "," (.getValue en)))
                (.append "\r\n")))
            nil
            pms)
    (.append buf "\r\n")
    (.append cookieBuf (nsb (.get headers "cookie")))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handlec ""

  [^ChannelHandlerContext ctx
   ^HttpContent msg
   ^StringBuilder cookieBuf
   ^StringBuilder buf]

  (let [ content (.content msg) ]
    (when (.isReadable content)
      (doto buf
        (.append "CONTENT: ")
        (.append (.toString content (CharsetUtil/UTF_8)))
        (.append "\r\n")))
    (when (instance? LastHttpContent msg)
      (.append buf "END OF CONTENT\r\n")
      (let [ ^LastHttpContent trailer msg ]
        (when-not (-> trailer (.trailingHeaders)(.isEmpty))
          (.append buf "\r\n")
          (reduce (fn [hdrs ^String n]
                    (doto buf
                      (.append "TRAILING HEADER: ")
                      (.append n)
                      (.append " = ")
                      (.append (clojure.string/join "," (.getAll hdrs n)))
                      (.append "\r\n"))
                    hdrs)
                  (.trailingHeaders trailer)
                  (-> (.trailingHeaders trailer)(.names)))
          (.append buf "\r\n")))
      (writeReply ctx msg cookieBuf buf))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooper ""

  []

  (let [ cookies (StringBuilder.)
         buf (StringBuilder.) ]

    (proxy [SimpleChannelInboundHandler][]
      (channelRead0 [ctx msg]
        (cond
          (instance? HttpRequest msg) (handleReq ctx msg cookies buf)
          (instance? HttpContent msg) (handlec ctx msg cookies buf)
          :else nil)
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; In memory HTTPD
(defn MakeMemHTTPD "Make an in-memory http server."

  [^String host port options]

  (let []

    (StartNetty host port (BootstrapNetty
      (fn []
        (proxy [ChannelInitializer] []
          (initChannel [^SocketChannel ch]
            (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
              (AddEnableSSL pl options)
              (AddExpect100 pl options)
              (.addLast pl "codec" (HttpServerCodec.))
              (.addLast pl "chunker" (ChunkedWriteHandler.))
              (.addLast pl "hhh" (snooper))
              pl))
          ))
      options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private snooper-eof nil)


