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

  comzotohlabscljc.netty.expect100 )

(use '[clojure.tools.logging :only [info warn error debug] ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isFormPost ""

  ;; boolean
  [^HttpRequest req ^String method]

  (let [ ct (-> (nsb (HttpHeaders/getHeader req "content-type"))
                (.toLowerCase)) ]
    ;; multipart form
    (and (or (= "POST" method) (= "PUT" method) (= "PATCH" method))
             (or (>= (.indexOf ct "multipart/form-data") 0)
                 (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- extractHeaders ""

  ;; map
  [^HttpHeaders msg]

  (let []
    (persistent! (reduce (fn [sum ^String n]
                           (assoc! sum (.toLowerCase n) (vec (.getAll msg n))))
                       (transient {}) (.names msg)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- extractParams ""

  ;; map
  [^QueryStringDecoder decr]

  (let []
    (persistent! (reduce (fn [sum ^Map$Entry en]
                                       (assoc! sum (nsb (.getKey en)) (vec (.getValue en))))
                                     (transient {})
                                     (.getParameters decr)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- extractMsgInfo ""

  ;; map
  [^HttpMessage msg]

  (let [ m { :protocol (nsb (.getProtocolVersion msg))
             :keep-alive (HttpHeaders/isKeepAlive msg)
             :clen (HttpHeaders/getContentLength msg 0)
             :uri ""
             :formpost false
             :wsock false
             :params {}
             :method ""
             :is-chunked (.isChunked msg)
             :headers (extractHeaders (.headers msg))  } ]
    (if (instance? HttpRequest msg)
      (let [ mo (strim (HttpHeaders/getHeader msg "X-HTTP-Method-Override"))
             ws (.toLowerCase (strim (HttpHeaders/getHeader msg "upgrade")))
             md (-> msg (.getMethod) (.getName))
             mt (-> (if mo mo md) (.toUpperCase))
             form (isFormPost msg mt)
             wsock (and (= "GET" mt) (= "websocket" ws))
             dc (QueryStringDecoder. (.getUri msg))
             pms (extractParams dc)
             uri (.getPath dc) ]
        (merge m { :uri uri :formpost form :wsock wsock :params pms :method mt }))
      m)
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFormPost ""

  [^ChannelHandlerContext ctx ^HttpRequest req]

  (let [ fac (DefaultHttpDataFactory. (com.zotohlabs.frwk.io.IOUtils/streamLimit))
         dc (HttpPostRequestDecoder. fac req) ]
    (-> (.attr ctx FORMDEC-KEY)(.set dc))
    (handleFormPostChunk ctx req)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readHttpDataChunkByChunk ""

  [^HttpPostRequestDecoder dc]

  (try
    (while (.hasNext dc)
      (let [ ^InterfaceHttpData data (.next dc) ]
        (when (notnil? data)
          (try
            (writeHttpData data)
            (finally
              (.release data))))))
    (catch EndOfDataDecoderException e#
      ;; eat it => indicates end of content chunk by chunk
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeHttpData ""

  [^InterfaceHttpData data]
;;attribute.getHttpDataType().name()
  (let [ nm (-> (.getHttpDataType data)(.name)) ]
    (cond
      (= (.getHttpDataType data) (HttpDataType/Attribute))
      (let [ ^Attribute attr data ]
        (try
          (.getValue attr)
          (catch IOException e#
            (error e# "")))
        (do somethiong))

      (= (.getHttpDataType data) (HttpDataType/FileUpload))
      (let [ ^FileUpload fu data ]
        (when (.isCompleted fu)
          ))

      :else nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFormPostChunk ""

  [^ChannelHandlerContext ctx msg]

  (let [ ^HttpPostRequestDecoder dc (-> (.attr ctx FORMDEC-KEY)(.get)) ]
    (when (instance? HttpContent msg)
      (let [ ^HttpContent chk msg ]
        (try
          (.offer dc chk)
          (catch  Throwable e#
            ;; TODO quit
            (error e# "")))
        (readHttpDataChunkByChunk dc)
        (when (instance? LastHttpContent msg)
          (-> (.attr ctx FORMDEC-KEY)(.remove))
          (.destroy dc))))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tooMuchData? ""

  ;; boolean
  [^ByteBuf content ^HttpContent chk]

  (> (.readableBytes content)
     (- (com.zotohlabs.frwk.io.IOUtils/streamLimit)
        (-> (.content chk)(.readableBytes)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- switchBufToFile ""

  ^OutputStream
  [^ChannelHandlerContext ctx ^CompositeByteBuf bbuf]

  (let [ [^File fp ^OutputStream os] (newly-tmpfile true)
         ^XData xs (-> (.attr ctx XDATA-KEY)(.get))
         len (.readableBytes bbuf) ]
    (.readBytes bbuf os (int len))
    (.flush os)
    (.resetContent xs fp)
    (-> (.attr ctx XOS-KEY)(.set os))
    os))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- flushToFile ""

  [^OutputStream os ^HttpContent chk]

  (let [ c (.content chk)
         len (.readableBytes c) ]
    (.readBytes c os len)
    (.flush os)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- flushBuf ""

  [^XData xs ^ByteBuf buf]

  (let [ len (.readableBytes buf)
         baos (make-baos) ]
    (.readBytes buf baos len)
    (.resetContent xs (-> baos (.flush)(.toByteArray)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addMoreHeaders  ""

  [^ChannelHandlerContext ctx ^HttpHeaders hds]

  (let [ info (-> (.attr ctx MSGINFO-KEY)(.get))
         old (:headers info) ]
    (-> (.attr ctx MSGINFO-KEY)
        (.set (assoc info :headers (merge old (extractHeaders hds))) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMsgChunk ""

  [^ChannelHandlerContext ctx msg]

  (let [ ^CompositeByteBuf cbuf (-> (.attr ctx CBUF-KEY)(.get))
         ^XData xs (-> (.attr ctx XDATA-KEY)(.get)) ]
    (when (instance? HttpContent msg)
      (let [ ^OutputStream os (if (and (.isEmpty xs) (tooMuchData? cbuf msg))
                                  (switchBufToFile ctx cbuf)
                                  (-> (.attr ctx XOS-KEY)(.get)))
             ^HttpContent chk msg ]
        (when (-> (.content chk)(.isReadable))
          (if (nil? os)
            (do
              (.retain chk)
              (.addComponent cbuf (.content chk))
              (.writerIndex cbuf (+ (.writerIndex cbuf)
                                   (-> (.content chk)(.readableBytes)))))
            (flushToFile os chk)))
        (when (instance? LastHttpContent msg)
          (addMoreHeaders ctx (.trailingHeaders ^LastHttpContent msg))
          (if (nil? os)
            (flushBuf xs cbuf)
            (do
              (.close os)
              (-> (.attr ctx XOS-KEY)(.remove))))
          (let [ info (-> (.attr ctx MSGINFO-KEY)(.get))
                 olen (:clen info)
                 clen (.size xs) ]
            (when-not (= olen clen)
              (warn "content-length read from headers = " olen ", new clen = " clen )
              (-> (.attr ctx MSGINFO)(.set (merge info { :clen clen }))))))
      ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleInboundMsg ""

  [^ChannelHandlerContext ctx ^HttpMessage msg]

  (let []
    (-> (.attr ctx CBUF-KEY)(.set (Unpooled/compositeBuffer 1024)))
    (-> (.attr ctx XDATA-KEY)(.set (XData .)))
    (handleMsgChunk ctx msg)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeSSL ""

  [^ChannelHandlerContext ctx]

  (notnil? (-> (NetUtils/getPipeline ctx)
               (.get (class SslHandler)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsSSL ""

  [^ChannelHandlerContext ctx]

  (let [ ssl (-> (NetUtils/getPipeline ctx)
                 (.get (class SslHandler)))
         ch (.getChannel ctx) ]
    (when-not (nil? ssl)
      (-> (.handshake ^SslHandler ssl)
         (.addListener (reify ChannelFutureListener
                         (operationComplete [_ f]
                           (when-not (.isSuccess f)
                             (NetUtils/closeChannel ch)) )))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleWSock ""

  [^ChannelHandlerContext ctx ^HttpRequest req]

  (let [ prx (if (maybeSSL ctx) "wss://" "ws://")
         info (-> (.attr ctx MSGINFO-KEY)(.get))
         us (str prx (:host info) (.getUri req))
         wf (WebSocketServerHandshakerFactory. us nil false)
         hs (.newHandshaker wf req)
         ch (.getChannel ctx) ]
    (if (nil? hs)
      (do
        (.sendUnsupportedWebSocketVersionResponse wf ch)
        (Try! (NetUtils/closeChannel ch)))
      (do
        (-> (.attr ctx WSHSK-KEY)(.set hs))
        (-> (.handshake hs ch req)
            (.addListener (reify ChannelFutureListener
                            (operationComplete [_ f]
                              (if (.isSuccess f)
                                  (wsSSL ctx)
                                  (Channels/fireExceptionCaught ch  (.getCause f)))))))))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readFrame  ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (let  [ ^OutputStream os (-> (.attr ctx BAOS-KEY)(.get))
          ^ByteBuf buf (.content frame)
          len (.readableBytes buf) ]
    (.readBytes buf os len)
    (.flush os)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeFinzFrame ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (let  [ ^OutputStream os (-> (.attr ctx BAOS-KEY)(.get))
          ^XData xs (-> (.attr ctx XDATA-KEY)(.get)) ]
    (when (.isFinalFragment frame)
      (let [ bits (-> os (.flush)(.toByteArray)) ]
        (if (-> (.attr ctx WSTEXT-KEY)(.get))
          (.resetContent xs (String. bits "utf-8"))
          (.resetContent xs bits)))
      (-> (.attr ctx BAOS-KEY)(.remvoe)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleWSockFrame  ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (let [ ^WebSocketServerHandshaker hs (-> (.attr ctx WSHSK-KEY)(.get))
         ^XData xs (-> (.attr ctx XDATA-KEY)(.get))
         ch (.getChannel ctx) ]
    (debug "nio-wsframe: received a " (type frame))
    (cond
      (instance? CloseWebSocketFrame frame)
      (.close hs ch ^CloseWebSocketFrame frame)

      (instance? PingWebSocketFrame frame)
      (.write ch (PongWebSocketFrame. (.content frame)))

      (or (instance? ContinuationWebSocketFrame frame)
          (instance? TextWebSocketFrame frame)
          (instance? BinaryWebSocketFrame frame))
      (do
        (readFrame ctx frame)
        (maybeFinzFrame ctx frame))

      :else ;; what else can this be ????
      nil) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Handler ""

  ^ChannelHandler
  []

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [c msg]
      (let [ ^ChannelHandlerContext ctx c ]
        (cond
          (or (instance? HttpResponse msg)
              (instance? HttpRequest msg))
          (let [ info (extractMsgInfo msg) ]
            (-> (.attr ctx MSGINFO-KEY)(.set info))
            (cond
              (:formpost info) (handleFormPost ctx msg))
              (:wsock info) (handleWSock ctx msg)
              :else (handleInboundMsg ctx msg))

          (instance? HttpContent msg)
          (let [ info (-> (.attr ctx MSGINFO-KEY)(.get)) ]
            (if (:formpost info)
              (handleFormPostChunk ctx msg)
              (handleMsgChunk ctx msg)))

          (instance? WebSocketFrame msg)
          (handleWSockFrame ctx msg)

          :else nil)))
  ))









;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private expect100-eof nil)

