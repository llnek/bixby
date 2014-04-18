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

(comment
(def HTTP-CODES
  (let [ to-key (comp int (fn [^Field f] (.code ^HttpResponseStatus (.get f nil))))
         fields (:fields (bean HttpResponseStatus))
         kkeys (map to-key fields)
         vvals (map (comp str (fn [^Field f] (.get f nil))) fields) ]
    (into {} (map vec (partition 2 (interleave kkeys vvals))))))
)

;; map of { int (code) -> HttpResponseStatus }
(def HTTP-CODES
  (let [ to-key (fn [^Field f] (.code ^HttpResponseStatus (.get f nil)))
         fields (:fields (bean HttpResponseStatus))
         kkeys (map to-key fields)
         vvals (map (fn [^Field f] (.get f nil)) fields) ]
    (into {} (map vec (partition 2 (interleave kkeys vvals))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; main netty classes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord NettyServer [^ServerBootstrap server ^ChannelGroup cgroup ] )

(defmulti ^:private add-listener "" (fn [a & more ] (class a)))

(def ^:private attr-trash (AttributeKey. "trash"))
(def ^:private attr-items (AttributeKey. "items"))
(def ^:private attr-info (AttributeKey. "msginfo"))
(def ^:private attr-clen (AttributeKey. "msglen"))
(def ^:private attr-dec (AttributeKey. "codec"))
(def ^:private attr-os (AttributeKey. "ostream"))
(def ^:private attr-xs (AttributeKey. "xdata"))
(def ^:private attr-ucb (AttributeKey. "ucb"))
(def ^:private attr-dir (AttributeKey. "dir"))
(def ^:private attr-wsk (AttributeKey. "wshsker"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Netty NIO Server
(defn BootstrapNetty ""

  ( [initor] (BootstrapNetty initor {} ))

  ( [initor options]
      (let [ gp (NioEventLoopGroup.) gc (NioEventLoopGroup.)
             bs (doto (ServerBootstrap.)
                      (.group gp gc)
                      (.channel io.netty.channel.socket.nio.NioServerSocketChannel)
                      (.option ChannelOption/SO_REUSEADDR true)
                      (.option ChannelOption/SO_BACKLOG 100)
                      (.childOption ChannelOption/SO_RCVBUF (int (* 2 1024 1024)))
                      (.childOption ChannelOption/TCP_NODELAY true))
             opts (:netty options) ]
        (doseq [ [k v] (seq opts) ]
          (if (= :child k)
            (doseq [ [x y] (seq v) ]
              (.childOption bs x y))
            (.option bs k v)))
        (.childHandler bs (initor options))
        bs
      ) ))

;;CM20140416_86767954
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; start netty on host/port
(defn StartNetty ""

  [^ServerBootstrap boot ^String host port]

  (let [ ch (-> boot (.bind (InetSocketAddress. host (int port)))
                     (.sync)
                     (.channel)) ]
    (debug "netty-xxx-server: running on host " host ", port " port)
    { :bootstrap boot :channel ch }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StopNettyServer "Clean up resources used by a netty server."

  [state]

  (let [ ^ServerBootstrap bs (:bootstrap state)
         gc (.childGroup bs)
         gp (.group bs)
         ^Channel ch (:channel state) ]
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
    (when-not (nil? eg) (.addFirst pl "ssl" (SslHandler. eg)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; default initializer
(defn DefaultChannelInitializer ""

  ^ChannelHandler
  [options]

  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel ch]
      (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
        (EnableSSL pl options)
        ;;(.addLast "decoder" (HttpRequestDecoder.))
        ;;(.addLast "encoder" (HttpResponseEncoder.))
        (.addLast pl "codec" (HttpServerCodec.))
        (.addLast pl "chunker" (ChunkedWriteHandler.))
        ;;(.addLast pl "handler" (generic-handler options))
        pl))
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; server side netty
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare handle-request)
(declare handle-wsframe)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- generic-handler "Make a generic Netty 4.x Pipeline Handler."

  ^ChannelHandler
  [options]

  (proxy [SimpleChannelInboundHandler] []

    (exceptionCaught [ctx err]
      (let [ ch (.channel ^ChannelHandlerContext ctx)
             msginfo (ga-map ch attr-info)
             ucb (:usercb options)
             keepAlive (if (nil? msginfo)
                         false
                         (:keep-alive msginfo)) ]
        (error err "")
        (when-not (nil? ucb)
          (.onerror ^comzotohlabscljc.netty.comms.NettyServiceIO
                    ucb ch msginfo err))
        (when-not keepAlive (Try! (NetUtils/closeChannel ch)))))

    (channelRead0 [ctx msg]
      ;;(debug "pipeline-entering with msg: " (type msg))
      (cond
        (instance? LastHttpContent msg)
        (handle-chunk ctx msg)

        (instance? HttpRequest msg)
        (handle-request ctx msg options)

        (instance? WebSocketFrame msg)
        (handle-wsframe ctx msg)

        (instance? HttpResponse msg)
        (handle-response ctx msg options)

        (instance? HttpContent msg)
        (handle-chunk ctx msg)

        (nil? msg)
        (throw (IOException. "Got null object."))

        :else
        (throw (IOException. (str "Got object: " (class msg))) )))

    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeServerNetty ""

  ([] (makeServerNetty {} ))

  ([options]
    (let [ gp (NioEventLoopGroup.) gc (NioEventLoopGroup.)
           bs (doto (ServerBootstrap.)
                    (.group gp gc)
                    (.channel io.netty.channel.socket.nio.NioServerSocketChannel)
                    (.option ChannelOption/SO_REUSEADDR true)
                    (.childOption ChannelOption/SO_RCVBUF (int (* 2 1024 1024)))
                    (.childOption ChannelOption/TCP_NODELAY true))
           opts (:netty options)
           cg (DefaultChannelGroup. (uid) GlobalEventExecutor/INSTANCE) ]
      (doseq [ [k v] (seq opts) ]
        (if (= :child k)
          (doseq [ [x y] (seq v) ]
            (.childOption bs x y))
          (.option bs k v)))
      (.childHandler bs (inizServer options))
      (comzotohlabscljc.netty.server.NettyServer. bs cg)
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; private helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- opDone ""

  [chOrGroup success error options]

  (cond
    (and (fn? (:nok options)) (not success))
    ((:nok options) chOrGroup error)

    (and (fn? (:ok options)) success)
    ((:ok options) chOrGroup)

    (fn? (:done options))
    ((:done options) chOrGroup)

    :else nil)
  nil)

(defmethod add-listener ChannelGroupFuture

  [^ChannelGroupFuture cf options]

  (-> cf (.addListener
           (reify ChannelGroupFutureListener
             (operationComplete [_ cff]
               (opDone (.group cf)
                       (.isSuccess ^ChannelGroupFuture cff)
                       nil
                       options ))))))

(defmethod add-listener ChannelFuture

  [^ChannelFuture cf options]

  (-> cf (.addListener
           (reify ChannelFutureListener
             (operationComplete [_ cff]
               (opDone (.channel cf)
                       (.isSuccess ^ChannelFuture cff)
                       (.cause ^ChannelFuture cff)
                       options ))))))

(defn- reply-xxx ""

  [ ^Channel ch status ]

  (let [ rsp (MakeFullHttpReply status)
         info (ga-map ch attr-info)
         dc (ga-map ch attr-dec)
         kalive (and (notnil? info) (:keep-alive info)) ]
    (when-not (nil? dc)
      (Try! (.destroy ^HttpPostRequestDecoder dc)))
    ;;(HttpHeaders/setTransferEncodingChunked res)
    (HttpHeaders/setContentLength rsp 0)
    (->> (WFlush ch rsp) (CloseCF kalive))))

(defn- getinfo-message ""

  ^comzotohlabscljc.net.comms.HTTPMsgInfo
  [ ^HttpMessage msg ]

  (let [ chunks (HttpHeaders/isTransferEncodingChunked msg)
         proto (nsb (.getProtocolVersion msg))
         kalive (HttpHeaders/isKeepAlive msg)
         clen (HttpHeaders/getContentLength msg 0)
         hds (.headers msg)
         headers (persistent! (reduce (fn [sum ^String n]
                           (assoc! sum (.toLowerCase n) (vec (.getAll hds n))))
                         (transient {}) (.names hds))) ]
    (cond
      (instance? ^HttpResponse msg)
      (comzotohlabscljc.net.comms.HTTPMsgInfo.
        proto
        ""
        ""
        chunks
        kalive
        clen
        headers
        {})

      (instance? ^HttpRequest msg)
      (let [ ws (.toLowerCase (strim (HttpHeaders/getHeader msg "upgrade")))
             mo (strim (HttpHeaders/getHeader msg "X-HTTP-Method-Override"))
             decr (QueryStringDecoder. (.getUri msg))
             md (-> msg (.getMethod) (.name))
             uri (.path decr)
             params (persistent! (reduce (fn [sum ^Map$Entry en]
                                     (assoc! sum (nsb (.getKey en)) (vec (.getValue en))))
                                   (transient {})
                                   (.parameters decr))) ]
        (comzotohlabscljc.net.comms.HTTPMsgInfo.
          proto
          (-> (if (= "websocket" ws) "WS" (if (hgl? mo) mo md))
              (.toUpperCase))
          uri
          chunks
          kalive
          clen
          headers
          params))

      :else nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; util funcs
;;
(defn- finz-ostream ""

  [ ^ChannelHandlerContext ctx ]

  (let [ ch (.channel ctx)
         os (ga-map ch attr-os) ]
    (IOUtils/closeQuietly ^OutputStream os) ))

(defn- process-multi-part ""

  [^Channel ch ^InterfaceHttpData data ^ArrayList items ^ArrayList out]

  (do
    (debug "multi-part data = " (type data))
    (cond
      (= (.getHttpDataType data) InterfaceHttpData$HttpDataType/Attribute)
      (let [^io.netty.handler.codec.http.multipart.Attribute attr data
             nm (nsb (.getName attr))
             ^bytes nv (.get attr) ]
        (debug "multi-part attribute value-string = " (String. nv "utf-8"))
        (.add items (ULFileItem. nm nv)))

      (= (.getHttpDataType data) InterfaceHttpData$HttpDataType/FileUpload)
      (let [ ^FileUpload fu data
             nm (nsb (.getName fu)) ]
        (when-not (.isCompleted fu)
          (throw (IOException. "checking uploaded file - incomplete.")))
        (when-not (.isInMemory fu)(.add out fu))
        (.add items (ULFileItem. nm (.getContentType fu)
                                       (.getFilename fu)
                                       (if (.isInMemory fu)
                                         (XData. (.get fu))
                                         (XData. (.getFile fu))))))
      :else nil)
    ))

(defn- finz-decoder

  [ ^ChannelHandlerContext ctx ]

  (let [ ch (.channel ctx)
         ^XData xdata (ga-map ch attr-xs)
         ^comzotohlabscljc.netty.comms.NettyServiceIO
         usercb (ga-map ch attr-ucb) ]
    (doseq [ f (seq (ga-map ch attr-trash)) ]
      (.removeHttpDataFromClean dc f))
    (Try! (.destroy ^HttpPostRequestDecoder (ga-map ch attr-dec) ))
    (.resetContent xdata (ga-map ch attr-items))
    (.onreq usercb ch (ga-map ch attr-dir) (ga-map ch attr-info) xdata)
  ))

(defn- read-formdata ""

  [ ^ChannelHandlerContext ctx ^HttpPostRequestDecoder dc ]

  (let [ ch (.channel ctx)
         items (ga-map ch attr-items)
         out (ga-map ch attr-trash) ]
    (try
      (while (.hasNext dc)
        (let [ data (.next dc) ]
          (try
            (process-multi-part ch data items out)
            (finally
              (.release data)))))
      (catch HttpPostRequestDecoder$EndOfDataDecoderException _
        (warn "read-formdata: EndOfDataDecoderException thrown.")))
  ))

(defn- complete-pipeflow ""

  [ ^ChannelHandlerContext ctx msg ]

  (do
    (finz-ostream ctx)
    (let [ ch (.channel ctx)
           ^XData xdata (ga-map ch attr-xs)
           ^comzotohlabscljc.netty.comms.NettyServiceIO usercb (ga-map ch attr-ucb)
           clen (ga-map ch attr-clen)
           info (ga-map ch attr-info)
           dir (ga-map ch attr-dir)
           os (ga-map ch attr-os) ]
      (when (and (instance? ByteArrayOutputStream os)
                 (> clen 0))
        (.resetContent xdata os))
      (cond
        (instance? HttpResponse dir)
        (do
          (TryC (NetUtils/closeChannel ch))
          (.onres usercb ch dir info xdata))

        (instance? HttpRequest dir)
        (if (nil? (ga-map ch attr-dec))
          (.onreq usercb ch dir info xdata)
          (finz-decoder ctx))

        (instance? WebSocketFrame msg)
        (.onreq usercb ch msg info xdata)

        :else nil)

    )))

(defn- sockit-down ""

  [ ^ChannelHandlerContext ctx ^ByteBuf cbuf ]

  (let [ ch (.channel ctx)
         ^OutputStream cout (ga-map ch attr-os)
         ^XData xdata (ga-map ch attr-xs)
         nsum (NetUtils/sockItDown cbuf cout (ga-map ch attr-clen))
         nout (if (.isDiskFile xdata)
                  cout
                  (NetUtils/swapFileBacked xdata cout nsum)) ]
    (sa-map! ch attr-clen nsum)
    (sa-map! ch attr-os nout)
  ))

(defn- make-infocache ""

  [ ^ChannelHandlerContext ctx usercb ]

  (let [ ch (.channel ctx) ]
    (sa-map! ch attr-trash (ArrayList.))
    (sa-map! ch attr-items (ArrayList.))
    (sa-map! ch attr-os (make-baos))
    (sa-map! ch attr-xs (XData.))
    (sa-map! ch attr-clen 0)
    (sa-map! ch attr-ucb usercb)
    (sa-map! ch attr-dir nil)
    (sa-map! ch attr-info nil)
    (sa-map! ch attr-dec nil)
  ))

(defn- handle-errorcase ""

  [ ^ChannelHandlerContext ctx err ]

  (let [ ch (.channel ctx)
         ^comzotohlabscljc.netty.comms.NettyServiceIO ucb (ga-map ch attr-ucb) ]
    (.onerror ucb ch (ga-map ch attr-info) err)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; websockets
;;
(defn- nioFrameChunk ""

  [ ^ChannelHandlerContext ctx ^ContinuationWebSocketFrame frame ]

  (let [ cbuf (.content frame)
         ch (.channel ctx) ]
    (when-not (nil? cbuf)
      (sockit-down ctx cbuf))
    (when (.isFinalFragment frame)
      (do
        (finz-ostream ctx)
        (when (nil? cbuf)
          (let [ s (nsb (.aggregatedText frame))
                 ^XData xs (ga-map ch attr-xs) ]
            (sa-map! ch attr-clen (.length s))
            (.resetContent xs s)))
        (complete-pipeflow ctx frame) ))
  ))

(defn- get-frame-bits ""

  [ ^ChannelHandlerContext ctx ^WebSocketFrame frame ]

  (when-let [ buf (.content frame) ]
    (sockit-down ctx buf)))

(defn- handle-wsframe ""

  [ ^ChannelHandlerContext ctx ^WebSocketFrame frame ]

  (let [ ch (.channel ctx)
         ^XData xs (ga-map ch attr-xs)
         ^WebSocketServerHandshaker hs (ga-map ch attr-wsk) ]
    (debug "nio-wsframe: received a " (type frame) )
    (cond
      (instance? CloseWebSocketFrame frame)
      (.close hs ch ^CloseWebSocketFrame frame)

      (instance? PingWebSocketFrame frame)
      (WFlush ch (PongWebSocketFrame. (-> (.content frame)(.retain))))

      (instance? BinaryWebSocketFrame frame)
      (do
        (get-frame-bits ctx frame)
        (complete-pipeflow ctx frame))

      (instance? TextWebSocketFrame frame)
      (let [ s (nsb (.text ^TextWebSocketFrame frame)) ]
        (.resetContent xs s)
        (sa-map! ch attr-clen (.length s))
        (complete-pipeflow ctx frame))

      (instance? ContinuationWebSocketFrame frame)
      (nioFrameChunk ctx frame)

      :else ;; what else can this be ????
      nil) ))

(defn- wsockSSL ""

  [ ^ChannelHandlerContext ctx ]

  (let [ ssl (-> (NetUtils/getPipeline ctx)
                 (.get (class SslHandler))) ]
    (when-not (nil? ssl)
      (add-listener (.handshakeFuture ^SslHandler ssl)
                   { :nok (fn [c] (NetUtils/closeChannel ^Channel c)) } ))))

(defn- nio-wsock ""

  [ ^ChannelHandlerContext ctx ^HttpRequest req ]

  (let [ px (if (MaybeSSL ctx) "wss://" "ws://")
         ch (.channel ctx)
         us (str px (HttpHeaders/getHeader req "host") (.getUri req))
         wf (WebSocketServerHandshakerFactory. us nil false)
         hs (.newHandshaker wf req) ]
    (if (nil? hs)
      (do
        (WebSocketServerHandshakerFactory/sendUnsupportedWebSocketVersionResponse ch)
        (Try! (NetUtils/closeChannel ch)))
      (do
        (sa-map! ch attr-wsk hs)
        (add-listener (.handshake hs ch req)
                     { :nok (fn [^Channel c ^Throwable e]
                              (-> (NetUtils/getPipeline c) (.fireExceptionCaught e)))
                       :ok (fn [_] (wsockSSL ctx)) })))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; handle request
;;
(defn- maybe-formdata ""

  ^HttpPostRequestDecoder
  [ ^Channel ch ^HttpRequest req msginfo ]

  (let [ ct (-> (nsb (HttpHeaders/getHeader req "content-type"))
                (.toLowerCase))
         md (:method (ga-map ch attr-info)) ]
    ;; multipart form
    (if (and (or (= "POST" md) (= "PUT" md) (= "PATCH" md))
             (or (>= (.indexOf ct "multipart/form-data") 0)
                 (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
      (let [ fac (DefaultHttpDataFactory. (com.zotoh.frwk.io.IOUtils/streamLimit))
             rc (HttpPostRequestDecoder. fac req) ]
        (sa-map! ch attr-dec rc)
        rc)
      nil)
  ))

(defn- maybe-lastreq ""

  [ ^ChannelHandlerContext ctx req ]

  (when (instance? LastHttpContent req)
    (complete-pipeflow ctx req)))

(defn- decode-upload ""

  [ ^ChannelHandlerContext ctx ^HttpObject req ]

  (let [ ch (.channel ctx)
         ^HttpPostRequestDecoder c (ga-map ch attr-dec) ]
    (when (and (notnil? c)
               (instance? HttpContent req))
      (with-local-vars [err false]
        (try
            (.offer c ^HttpContent req)
            (read-formdata ctx c)
          (catch Throwable e#
            (var-set err true)
            (error e# "")
            (reply-xxx 400)))
        (if-not @err (maybe-lastreq ctx req))))))

(defn- nio-request ""

  [ ^ChannelHandlerContext ctx ^HttpRequest req ]

  (let [ ch (.channel ctx)
         msginfo (ga-map ch attr-info) ]
    (try
      (let [ rc (maybe-formdata ch req msginfo) ]
        (if (nil? rc)
          (when (and (not (:is-chunked msginfo))
                     (instance? ByteBufHolder req))
            (sockit-down ctx (.content ^ByteBufHolder req)))
          (decode-upload ctx req)))
      (catch Throwable e#
        (error e# "")
        (reply-xxx 400)))
    ))

(defn- handle-request ""

  [ ^ChannelHandlerContext ctx ^HttpRequest req options ]

  (make-infocache ctx (:usercb options))
  (let [ ^comzotohlabscljc.netty.comms.RouteCracker rtcObj (:rtcObj options)
         msginfo (getinfo-message req)
         ch (.channel ctx)
         rts (if (and (notnil? rtcObj)
                      (.hasRoutes? rtcObj))
                 (.routable? rtcObj msginfo)
                 true) ]
    (debug "received a " (:method msginfo) " request from " (:uri msginfo))
    (sa-map! ch attr-info msginfo)
    (sa-map! ch attr-rts rts)
    (sa-map! ch attr-dir req)
    (if rts
      (if (= "WS" (:method msginfo))
        (nio-wsock ctx req)
        (do
          (when (HttpHeaders/is100ContinueExpected req) (Continue100 ctx))
          (nio-request ctx req)))
      (do
        (warn "route not matched - ignoring request.")
        (if (true? (:forwardBadRoutes options))
          (complete-pipeflow ctx req)
          (reply-xxx 403))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; handle redirect
;;
(defn- handle-redirect ""

  [ ^ChannelHandlerContext ctx msg ]

  ;; TODO: handle redirect properly, for now, same as error
  (handle-errorcase ctx (IOException. "Unsupported redirect.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; handle response
;;
(defn- handle-response ""

  [ ^ChannelHandlerContext ctx ^HttpResponse res options ]

  (make-infocache ctx (:usercb options))
  (let [ msginfo (getinfo-message res)
         ch (.channel ctx)
         s (.getStatus res)
         r (.reasonPhrase s)
         c (.code s) ]
    (debug "got a http-response: code " c " reason: " r)
    (sa-map! ch attr-info msginfo)
    (sa-map! ch attr-dir res)
    (sa-map! ch attr-rts false)
    (cond
      (and (>= c 200) (< c 300))
      (when (and (not (HttpHeaders/isTransferEncodingChunked res))
                 (instance? ByteBufHolder res))
        (sockit-down ctx (.content ^ByteBufHolder res)))

      (and (>= c 300) (< c 400))
      (handle-redirect ctx)

      :else
      (handle-errorcase ctx (IOException. (str "error code: " c))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; handle chunks
;;
(defn- handle-basic-chunk ""

  [ ^ChannelHandlerContext ctx ^HttpContent msg ]

  (let [ done (try
                  (sockit-down ctx (.content msg))
                  (instance? LastHttpContent msg)
                (catch Throwable e#
                    (do (finz-ostream ctx) (throw e#)))) ]
    (if done (complete-pipeflow ctx msg) nil)))

(defn- handle-chunk ""

  [ ^ChannelHandlerContext ctx ^HttpContent msg ]

  (let [ ch (.channel ctx)
         dc (ga-map ch attr-dec) ]
    (if (nil? dc)
      (handle-basic-chunk ctx msg)
      (decode-upload ctx msg))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; In memory HTTPD
;;
(defn MakeMemHttpd "Make an in-memory http server."

  ^NettyServer
  [^String host port options]

  (MakeNettyNIOServer host port options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; file handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- reply-get-vfile ""

  [^Channel ch ^XData xdata]

  (let [ info (ga-map ch attr-info)
         res (MakeHttpReply 200)
         clen (.size xdata)
         kalive (and (notnil? info) (:keep-alive info)) ]
    (HttpHeaders/setHeader res "content-type" "application/octet-stream")
    (HttpHeaders/setContentLength res clen)
    (HttpHeaders/setTransferEncodingChunked res)
    (WWrite ch res)
    (CloseCF kalive (WFlush ch (ChunkedStream. (.stream xdata)))) ))

(defn- filer-handler ""

  [^File vdir]

  (let [ putter (fn [^Channel ch ^String fname ^XData xdata]
                  (try
                      (save-file vdir fname xdata)
                      (reply-xxx ch 200)
                    (catch Throwable e#
                      (reply-xxx ch 500))))
         getter (fn [^Channel ch ^String fname]
                  (let [ xdata (get-file vdir fname) ]
                    (if (.hasContent xdata)
                      (reply-get-vfile ch xdata)
                      (reply-xxx ch 204)))) ]
    (reify NettyServiceIO
      (onres [_ ch rsp msginfo xdata] nil)
      (presend [_ ch msg] nil)
      (onerror [_ ch msginfo err]
        (do
          (when-not (nil? err) (error err ""))
          (reply-xxx ch 500)))
      (onreq [_ ch req msginfo xdata]
        (let [ mtd (nsb (:method msginfo))
               uri (nsb (:uri msginfo))
               pos (.lastIndexOf uri (int \/))
               p (if (< pos 0) uri (.substring uri (inc pos))) ]
          (debug "Method = " mtd ", Uri = " uri ", File = " p)
          (cond
            (or (= mtd "POST")(= mtd "PUT")) (putter ^Channel ch p xdata)
            (or (= mtd "GET")(= mtd "HEAD")) (getter ^Channel ch p)
            :else (reply-xxx ch 405)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MakeMemFileSvr "A file server which can get/put files."

  ^NettyServer
  [^String host port options]

  (let [ fh (filer-handler (:vdir options)) ]
    (MakeNettyNIOServer host port (merge options { :usercb fh } ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private server-eof nil)

