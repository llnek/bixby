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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeReply ""

  [^ChannelHandlerContext ctx
   ^StringBuilder buf]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleReq ""

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^StringBuilder buf]

  (let [ dc (QueryStringDecoder. (.getUri req))
         headers (.headers req)
         pms (.parameters dc) ]
    (doto buf
      (.append "WELCOME TO THE WILD WILD WEB SERVER\r\n")
      (.append "===================================\r\n")
      (.append "VERSION: ")
      (.append (.getProtocolVersion req))
      (.append "\r\n")
      (.append "HOSTNAME: ")
      (.append (.getHost req "unknown"))
      (.append "\r\n")
      (.append "REQUEST_URI: ")
      (.append (.getUri req))
      (.append "\r\n\r\n"))
    (reduce (fn [memo ^String n]
              (doto buf
                (.append "HEADER: ")
                (.append n)
                (.append " = ")
                (.append (clojure.string/join "," (.getAll hdrs n)))
                (.append "\r\n")))
            buf
            (.names hdrs))
    (.append buf "\r\n")
    (reduce (fn [memo ^Map$Entry en]
              (doto buf
                (.append "PARAM: ")
                (.append (.getKey en))
                (.append " = ")
                (.append (clojure.string/join "," (.getValue en)))
                (.append "\r\n")))
            buf
            pms)
    (.append buf "\r\n")

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleData ""

  [^ChannelHandlerContext ctx
   ^HttpContent msg
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
          (reduce (fn [memo ^String n]
                    (doto buf
                      (.append "TRAILING HEADER: ")
                      (.append n)
                      (.append " = ")
                      (.append (clojure.string/join "," (.getAll hdrs n)))
                      (.append "\r\n")))
                  buf
                  (-> trailer (.trailingHeaders)(.names)))
          (.append buf "\r\n")))
      (writeReply ctx msg buf))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- snooper ""

  []

  (let [ buf (StringBuilder.) ]
    (proxy [SimpleChannelInboundHandler][]
      (channelRead0 [c msg]
        (cond
          (instance? HttpRequest msg)
          (handleReq c msg buf)

          (instance? HttpContent msg)
          (handleData c msg buf)

          :else nil)
  ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initor ""

  ^ChannelHandler
  [options]

  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel ch]
      (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
        (EnableSSL pl options)
        (.addLast pl "expect" (Expect100))
        (.addLast pl "codec" (HttpServerCodec.))
        (.addLast pl "h" (snooper))
        pl))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeMemHTTPD ""

  [^String host port options]

  (StartNetty host port (BootstrapNetty initor options)))



















`
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Netty NIO Server
(defn BootstrapNetty ""

  ( [options] (BootstrapNetty (:initor options) options))

  ( [initor options]
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
        { :bootstrap bs })
  ))

;;CM20140416_86767954
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; start netty on host/port
(defn StartNetty ""

  [^String host port netty]

  (let [ ch (-> (:bootstrap netty)
                (.bind (InetSocketAddress. host (int port)))
                (.sync)
                (.channel)) ]
    (debug "netty-xxx-server: running on host " host ", port " port)
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
;; add SSL
(defn EnableSSL ""

  ^ChannelPipeline
  [pipe options]

  (let [ kf (:serverkey options)
         pw (:passwd options)
         ssl (if (nil? kf)
                 nil
                 (make-sslContext kf pw))
         eg (if (nil? ssl)
                nil
                (doto (.createSSLEngine ssl)
                      (.setUseClientMode false))) ]
    (when-not (nil? eg) (.addFirst pipe "ssl" (SslHandler. eg)))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; default initializer
(defn MakeChannelInitializer ""

  ^ChannelHandler
  [options]

  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel ch]
      (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
        (EnableSSL pl options)
        (.addLast pl "codec" (HttpServerCodec.))
        (.addLast pl "chunker" (ChunkedWriteHandler.))
        pl))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; In memory HTTPD
;;
(defn MakeMemHTTPD "Make an in-memory http server."

  ;; map with netty objects
  [^String host port options]

  (StartNetty host port (BootstrapNetty options)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; file handlers
(defn- replyGetVFile ""

  [^Channel ch ^XData xdata]

  (let [ info (ga-map ch attr-info)
         res (MakeHttpReply 200)
         clen (.size xdata)
         kalive (and (notnil? info) (:keep-alive info)) ]
    (HttpHeaders/setHeader res "content-type" "application/octet-stream")
    (HttpHeaders/setContentLength res clen)
    (HttpHeaders/setTransferEncodingChunked res)
    (WWrite ch res)
    (CloseCF (WFlush ch (ChunkedStream. (.stream xdata))) kalive)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- filePutter ""

  [^File vdir ^Channel ch ^String fname ^XData xdata]

  (try
    (save-file vdir fname xdata)
    (ReplyXXX ch 200)
    (catch Throwable e#
      (ReplyXXX ch 500))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileGetter ""

  [^File vdir ^Channel ch ^String fname]

  (let [ xdata (get-file vdir fname) ]
    (if (.hasContent xdata)
      (replyGetVFile ch xdata)
      (ReplyXXX ch 204))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileHandler ""

  [vdir]

  (let []
    (proxy [ChannelInboundHandlerAdapter][]
      (channelRead [c msg]
        (let [ ^ChannelHandlerContext ctx c
               ^XData xs (:payload msg)
               info (:info msg)
               mtd (:method info)
               uri (:uri info)
               pos (.lastIndexOf uri (int \/))
               p (if (< pos 0) uri (.substring uri (inc pos))) ]
          (debug "Method = " mtd ", Uri = " uri ", File = " p)
          (cond
            (or (= mtd "POST")(= mtd "PUT")) (filePutter vdir ch p xs)
            (or (= mtd "GET")(= mtd "HEAD")) (fileGetter vdir ch p)
            :else (ReplyXXX ch 405)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MakeMemFileSvr "A file server which can get/put files."

  [^String host port options]

  (let [ initor (fn []
                  (proxy [ChannelInitializer] []
                    (initChannel [^SocketChannel ch]
                      (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
                        (EnableSSL pl options)
                        (.addLast pl "codec" (HttpServerCodec.))
                        (.addLast pl "aux" (MakeAuxDecoder))
                        (.addLast pl "chunker" (ChunkedWriteHandler.))
                        (.addLast pl "filer" (fileHandler (:vdir options)))
                        pl)))) ]
    (StartNetty host port (BootstrapNetty initor options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private server-eof nil)

