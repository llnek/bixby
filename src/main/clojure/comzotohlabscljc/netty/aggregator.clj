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

  comzotohlabscljc.netty.aggregator )

(use '[clojure.tools.logging :only [info warn error debug] ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private FORMDEC-KEY (AttributeKey. "formdecoder"))
(def ^:private XDATA-KEY (AttributeKey. "xdata"))
(def ^:private XOS-KEY (AttributeKey. "ostream"))
(def ^:private MSGINFO-KEY (AttributeKey. "msginfo"))
(def ^:private CBUF-KEY (AttributeKey. "cbuffer"))
(def ^:private WSHSK-KEY (AttributeKey. "wsockhandshaker"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resetAttrs ""

  [^ChannelHandlerContext ctx]

  (let [ ^HttpPostRequestDecoder dc (getAttr ctx FORMDEC-KEY)
         ^ByteBuf buf (getAttr ctx CBUF-KEY) ]
    (delAttr ctx MSGINFO-KEY)
    (delAttr ctx FORMDEC-KEY)
    (delAttr ctx CBUF-KEY)
    (delAttr ctx XDATA-KEY)
    (delAttr ctx XOS-KEY)
    (delAttr ctx WSHSK-KEY)
    (when-not (nil? buf) (.release buf))
    (when-not (nil? dc) (.destroy dc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- delAttr ""

  [^ChannelHandlerContext ctx ^AttributeKey akey]

  (-> (.attr ctx akey)(.remove)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slurpByteBuf ""

  [^ByteBuf buf ^OutputStream os]

  (let [ len (.readableBytes buf) ]
    (.readBytes buf os len)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setAttr ""

  [^ChannelHandlerContext ctx ^AttributeKey akey aval]

  (-> (.attr ctx akey)(.set aval)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAttr ""

  [^ChannelHandlerContext ctx ^AttributeKey akey]

  (-> (.attr ctx akey)(.get)))

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
  [^HttpHeaders hdrs]

  (let []
    (persistent! (reduce (fn [sum ^String n]
                           (assoc! sum (.toLowerCase n) (vec (.getAll hdrs n))))
                       (transient {})
                       (.names hdrs)))
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

  (let [ m { :host (strim (HttpHeaders/getHeader msg "Host"))
             :protocol (strim (.getProtocolVersion msg))
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
      (let [ ws (-> (strim (HttpHeaders/getHeader msg "upgrade"))(.toLowerCase))
             mo (strim (HttpHeaders/getHeader msg "X-HTTP-Method-Override"))
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
    (setAttr ctx FORMDEC-KEY dc)
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
      )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeHttpData ""

  [^InterfaceHttpData data]

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

  (let [ ^HttpPostRequestDecoder dc (getAttr ctx FORMDEC-KEY) ]
    (when (instance? HttpContent msg)
      (let [ ^HttpContent chk msg ]
        (try
          (.offer dc chk)
          (catch  Throwable e#
            ;; TODO quit
            (error e# "")))
        (readHttpDataChunkByChunk dc)
        (when (instance? LastHttpContent msg)
          (let [ info (getAttr ctx MSGINFO-KEY)
                 xs (getAttr ctx XDATA-KEY) ]
            (resetAttrs ctx)
            (.fireChannelRead ctx { :info info :payload xs } )))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tooMuchData? ""

  ;; boolean
  [^ByteBuf content chunc]

  (let [ buf (cond
               (instance? WebSocketFrame chunc) (.content ^WebSocketFrame chunc)
               (instance? HttpContent chunc) (.content ^HttpContent chunc)
               :else nil) ]
    (if (notnil? buf)
      (> (.readableBytes content)
         (- (com.zotohlabs.frwk.io.IOUtils/streamLimit)
            (.readableBytes buf)))
      false)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- switchBufToFile ""

  ^OutputStream
  [^ChannelHandlerContext ctx ^CompositeByteBuf bbuf]

  (let [ [^File fp ^OutputStream os] (newly-tmpfile true)
         ^XData xs (getAttr ctx XDATA-KEY) ]
    (.readBytes bbuf os (.readableBytes bbuf))
    (.flush os)
    (.resetContent xs fp)
    (setAttr ctx XOS-KEY os)
    os))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- flushToFile ""

  [^OutputStream os chunc]

  (let [ buf (cond
               (instance? WebSocketFrame chunc) (.content ^WebSocketFrame chunc)
               (instance? HttpContent chunc) (.content ^HttpContent chunc)
               :else nil) ]
    (when-not (nil? buf)
      (.readBytes buf os (.readableBytes c))
      (.flush os))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addMoreHeaders  ""

  [^ChannelHandlerContext ctx ^HttpHeaders hds]

  (let [ info (getAttr ctx MSGINFO-KEY)
         old (:headers info) ]
    (setAttr ctx MSGINFO-KEY (assoc info
                                    :headers
                                    (merge old (extractHeaders hds))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMsgChunk ""

  [^ChannelHandlerContext ctx msg]

  (let [ ^CompositeByteBuf cbuf (getAttr ctx CBUF-KEY)
         ^XData xs (getAttr ctx XDATA-KEY) ]
    (when (instance? HttpContent msg)
      (let [ ^OutputStream os (if (and (.isEmpty xs) (tooMuchData? cbuf msg))
                                  (switchBufToFile ctx cbuf)
                                  (getAttr ctx XOS-KEY))
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
            (let [ baos (make-baos) ]
              (slurpByteBuf cbuf baos)
              (.resetContent xs (.toByteArray baos)))
            (.close os))
          (let [ info (getAttr ctx MSGINFO-KEY)
                 olen (:clen info)
                 clen (.size xs) ]
            (when-not (= olen clen)
              (warn "content-length read from headers = " olen ", new clen = " clen )
              (setAttr MSGINFO (merge info { :clen clen }))))
          (let [ info (getAttr ctx MSGINFO-KEY) ]
            (resetAttrs ctx)
            (.fireChannelRead ctx { :info info :payload xs })))
    ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleInboundMsg ""

  [^ChannelHandlerContext ctx ^HttpMessage msg]

  (let []
    (setAttr ctx CBUF-KEY (Unpooled/compositeBuffer 1024))
    (setAttr ctx XDATA-KEY (XData .))
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

  (let [ ^SslHandler ssl (-> (NetUtils/getPipeline ctx)
                             (.get (class SslHandler)))
         ch (.getChannel ctx) ]
    (when-not (nil? ssl)
      (-> (.handshake ssl)
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
         info (getAttr ctx MSGINFO-KEY)
         us (str prx (:host info) (.getUri req))
         wf (WebSocketServerHandshakerFactory. us nil false)
         hs (.newHandshaker wf req)
         ch (.getChannel ctx) ]
    (if (nil? hs)
      (do
        (.sendUnsupportedWebSocketVersionResponse wf ch)
        (Try! (NetUtils/closeChannel ch)))
      (do
        (setAttr ctx CBUF-KEY (Unpooled/compositeBuffer 1024))
        (setAttr ctx XDATA-KEY (XData .))
        (setAttr ctx WSHSK-KEY hs)
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

  (let [ ^CompositeByteBuf cbuf (getAttr ctx CBUF-KEY)
         ^XData xs (getAttr ctx XDATA-KEY)
         ^OutputStream os (if (and (.isEmpty xs) (tooMuchData? cbuf frame))
                              (switchBufToFile ctx cbuf)
                              (getAttr ctx XOS-KEY)) ]
    (if (nil? os)
        (do
          (.retain frame)
          (.addComponent cbuf (.content frame))
          (.writerIndex cbuf (+ (.writerIndex cbuf)
                               (-> (.content frame)(.readableBytes)))))
        (flushToFile os frame))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeFinzFrame ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (when (.isFinalFragment frame)
    (let  [ ^CompositeByteBuf cbuf (getAttr ctx CBUF-KEY)
            ^OutputStream os (getAttr ctx XOS-KEY)
            ^XData xs (getAttr ctx XDATA-KEY) ]
      (if (notnil? os)
        (.close os)
        (let [ baos (make-baos) ]
          (slurpByteBuf cbuf baos)
          (.resetContent xs (.toByteArray baos))))
      (let [ info (getAttr ctx MSGINFO-KEY) ]
        (resetAttrs ctx)
        (.fireChannelRead ctx { :info info :payload xs }))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleWSockFrame  ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (let [ ^WebSocketServerHandshaker hs (getAttr ctx WSHSK-KEY)
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

      :else nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeAuxRequestDecoder

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

(def ^:private aggregator-eof nil)

