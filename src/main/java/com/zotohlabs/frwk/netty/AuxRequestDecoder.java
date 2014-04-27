
package com.zotohlabs.frwk.netty;

public enum AuxRequestDecoder {
;

  private static final AttributeKey FORMDEC-KEY =AttributeKey.valueOf( "formdecoder");
  private static final AttributeKey FORMITMS-KEY= AttributeKey.valueOf("formitems");
  private static final AttributeKey XDATA-KEY =AttributeKey.valueOf("xdata");
  private static final AttributeKey XOS-KEY =AttributeKey.valueOf("ostream");
  private static final AttributeKey MSGINFO-KEY= AttributeKey.valueOf("msginfo");
  private static final AttributeKey CBUF-KEY =AttributeKey.valueOf("cbuffer");
  private static final AttributeKey WSHSK-KEY =AttributeKey.valueOf("wsockhandshaker");

  private static void setAttr( ChannelHandlerContext ctx, AttributeKey akey,  Object aval) {
    ctx.attr(akey).set(aval);
  }

  private static void delAttr(ChannelHandlerContext ctx , AttributeKey akey) {
    ctx.attr(akey).remove();
  }

  private static Object getAttr( ChannelHandlerContext ctx, AttributeKey akey) {
    return ctx.attr(akey).get();
  }

  private static void slurpByteBuf(ByteBuf buf, OutputStream os) {
    int len =  buf.readableBytes();
    if (len > 0) {
      buf.readBytes( os, len);
    }
  }


  private static void resetAttrs(ChannelHandlerContext ctx) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC-KEY);
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS-KEY);
    ByteBuf buf = (ByteBuf) getAttr(ctx, CBUF-KEY);

    delAttr(ctx,FORMITMS-KEY);
    delAttr(ctx,MSGINFO-KEY);
    delAttr(ctx,FORMDEC-KEY);
    delAttr(ctx,CBUF-KEY);
    delAttr(ctx,XDATA-KEY);
    delAttr(ctx,XOS-KEY);
    delAttr(ctx,WSHSK-KEY);
    if (buf != null) buf.release();
    if (dc != null) dc.destroy();
    if (fis != null) fis.destroy();
  }


  private static boolean isFormPost ( HttpRequest req, String method) {
    String ct = nsb(HttpHeaders.getHeader(req, "content-type")).toLowerCase();
    ;; multipart form
    return ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) ||
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  }



}


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- extractHeaders ""

  ;; map
  [^HttpHeaders hdrs]

  (let []
    (persistent! (reduce (fn [sum ^String n]
                           (assoc! sum (cstr/lower-case n) (vec (.getAll hdrs n))))
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
                         (.parameters decr)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- extractMsgInfo ""

  ;; map
  [^HttpMessage msg]

  (with-local-vars [ m { :host (strim (HttpHeaders/getHeader msg "Host" ""))
                         :protocol (strim (.getProtocolVersion msg))
                         :keep-alive (HttpHeaders/isKeepAlive msg)
                         :clen (HttpHeaders/getContentLength msg 0)
                         :uri ""
                         :status ""
                         :code 0
                         :formpost false
                         :wsock false
                         :params {}
                         :method ""
                         :is-chunked (HttpHeaders/isTransferEncodingChunked msg)
                         :headers (extractHeaders (.headers msg))  } ]
    (when (instance? HttpResponse msg)
      (let [ s (.getStatus ^HttpResponse msg)
             r (.reasonPhrase s)
             c (.code s) ]
        (var-set m (merge @m { :code c :status r } ))))
    (when (instance? HttpRequest msg)
      (let [ ws (cstr/lower-case (strim (HttpHeaders/getHeader msg "upgrade")))
             mo (strim (HttpHeaders/getHeader msg "X-HTTP-Method-Override"))
             ^HttpRequest req msg
             md (-> req (.getMethod) (.name))
             mt (cstr/upper-case (if mo mo md))
             form (isFormPost msg mt)
             wsock (and (= "GET" mt) (= "websocket" ws))
             dc (QueryStringDecoder. (.getUri req))
             pms (extractParams dc)
             uri (.path dc) ]
        (var-set m (merge @m { :uri uri :formpost form :wsock wsock :params pms :method mt }))))
    @m
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare handleFormPostChunk)
(defn- handleFormPost ""

  [^ChannelHandlerContext ctx ^HttpRequest req]

  (let [ fac (DefaultHttpDataFactory. (com.zotohlabs.frwk.io.IOUtils/streamLimit))
         dc (HttpPostRequestDecoder. fac req) ]
    (setAttr ctx FORMITMS-KEY (ULFormItems.))
    (setAttr ctx FORMDEC-KEY dc)
    (handleFormPostChunk ctx req)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeHttpData ""

  [^ChannelHandlerContext ctx ^InterfaceHttpData data]

  (when-not (nil? data)
    (let [ ^String nm (-> (.getHttpDataType data)(.name))
           ^ULFormItems fis (getAttr ctx FORMITMS-KEY) ]
      (cond
        (= (.getHttpDataType data) (InterfaceHttpData$HttpDataType/Attribute))
        (let [ ^io.netty.handler.codec.http.multipart.Attribute attr data
               baos (MakeBitOS) ]
          (slurpByteBuf (.content attr) baos)
          (.add fis (ULFileItem. nm (.toByteArray baos))))

        (= (.getHttpDataType data) (InterfaceHttpData$HttpDataType/FileUpload))
        (let [ ^FileUpload fu data
               ct (.getContentType fu)
               fnm (.getFilename fu) ]
          (when (.isCompleted fu)
            (cond
              (instance? DiskFileUpload fu)
              (let [ fp (NewlyTmpfile false) ]
                (.renameTo ^DiskFileUpload fu fp)
                (.add fis (ULFileItem. nm  ct fnm (XData. fp))))

              :else
              (let [ [ ^File fp ^OutputStream os ] (NewlyTmpfile true)
                     buf (.content fu) ]
                (slurpByteBuf buf os)
                (IOUtils/closeQuietly os)
                (.add fis (ULFileItem. nm  ct fnm (XData. fp))))
            )))

        :else (throw (IOException. "Bad POST: unknown http data.")))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readHttpDataChunkByChunk ""

  [^ChannelHandlerContext ctx ^HttpPostRequestDecoder dc]

  (try
    (while (.hasNext dc)
      (when-let [ data (.next dc) ]
        (try
          (writeHttpData ctx data)
          (finally
            (.release ^InterfaceHttpData data)))))
    (catch HttpPostRequestDecoder$EndOfDataDecoderException e#) ;; eat it => indicates end of content chunk by chunk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFormPostChunk ""

  [^ChannelHandlerContext ctx msg]

  (with-local-vars [ ^HttpPostRequestDecoder dc (getAttr ctx FORMDEC-KEY)
                     err nil ]
    (when (instance? HttpContent msg)
      (try
        (.offer ^HttpPostRequestDecoder @dc  ^HttpContent msg)
        (readHttpDataChunkByChunk ctx @dc)
        (catch  Throwable e#
          (var-set err e#)
          (.fireExceptionCaught ctx e#))))
    (when (and (nil? @err) (instance? LastHttpContent msg))
      (let [ info (getAttr ctx MSGINFO-KEY)
             fis (getAttr ctx FORMITMS-KEY)
             ^XData xs (getAttr ctx XDATA-KEY) ]
        (delAttr ctx FORMITMS-KEY)
        (.resetContent xs fis)
        (resetAttrs ctx)
        (.fireChannelRead ctx { :info info :payload xs } )))
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

  (let [ [^File fp ^OutputStream os] (NewlyTmpfile true)
         ^XData xs (getAttr ctx XDATA-KEY) ]
    (slurpByteBuf bbuf os)
    (.flush os)
    (.resetContent xs fp)
    (setAttr ctx XOS-KEY os)
    os
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- flushToFile ""

  [^OutputStream os chunc]

  (let [ buf (cond
               (instance? WebSocketFrame chunc) (.content ^WebSocketFrame chunc)
               (instance? HttpContent chunc) (.content ^HttpContent chunc)
               :else nil) ]
    (when-not (nil? buf)
      (slurpByteBuf buf os)
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
(defn- maybeFinzMsgChunk

  [^ChannelHandlerContext ctx msg]

  (let [ ^OutputStream os (getAttr ctx XOS-KEY)
         ^ByteBuf cbuf (getAttr ctx CBUF-KEY)
         ^XData xs (getAttr ctx XDATA-KEY)
         info (getAttr ctx MSGINFO-KEY) ]
    (when (instance? LastHttpContent msg)
      (addMoreHeaders ctx (.trailingHeaders ^LastHttpContent msg))
      (if (nil? os)
        (let [ baos (MakeBitOS) ]
          (slurpByteBuf cbuf baos)
          (.resetContent xs baos))
        (do (IOUtils/closeQuietly os)))
      (let [ olen (:clen info)
             clen (.size xs) ]
        (when-not (= olen clen)
          (log/warn "content-length read from headers = " olen ", new clen = " clen )
          (setAttr ctx MSGINFO-KEY (merge info { :clen clen }))))
      (let [ info (getAttr ctx MSGINFO-KEY) ]
        (resetAttrs ctx)
        (.fireChannelRead ctx { :info info :payload xs })))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMsgChunk ""

  [^ChannelHandlerContext ctx msg]

  (let [ ^CompositeByteBuf cbuf (getAttr ctx CBUF-KEY)
         ^XData xs (getAttr ctx XDATA-KEY) ]
    (when (instance? HttpContent msg)
      (let [ ^OutputStream os (if (and (not (.hasContent xs)) (tooMuchData? cbuf msg))
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
            (flushToFile os chk))))
      (maybeFinzMsgChunk ctx msg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleRedirect ""

  [^ChannelHandlerContext ctx ^HttpMessage msg]

  (let [ err (IOException. "Redirect is not supported at this time.") ]
    (log/error err "")
    (.fireExceptionCaught ctx err)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleInboundMsg ""

  [^ChannelHandlerContext ctx ^HttpMessage msg]

  (with-local-vars [ info (getAttr ctx MSGINFO-KEY) good true ]
    (setAttr ctx CBUF-KEY (Unpooled/compositeBuffer 1024))
    (setAttr ctx XDATA-KEY (XData.))
    (when (instance? HttpResponse msg)
      (let [ c (:code @info) ]
        (cond
          (and (>= c 200) (< c 300))
          nil

          (and (>= c 300) (< c 400))
          (do
            (var-set good false)
            (handleRedirect ctx msg))

          :else (log/warn "received http-response with error code " c))))
    (when @good
      (handleMsgChunk ctx msg))
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
         ch (.channel ctx) ]
    (when-not (nil? ssl)
      (-> (.handshakeFuture ssl)
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
         ch (.channel ctx) ]
    (if (nil? hs)
      (do
        (WebSocketServerHandshakerFactory/sendUnsupportedVersionResponse ch)
        (Try! (NetUtils/closeChannel ch)))
      (do
        (setAttr ctx CBUF-KEY (Unpooled/compositeBuffer 1024))
        (setAttr ctx XDATA-KEY (XData.))
        (setAttr ctx WSHSK-KEY hs)
        (-> (.handshake hs ch req)
            (.addListener (reify ChannelFutureListener
                            (operationComplete [_ f]
                              (if (.isSuccess f)
                                  (wsSSL ctx)
                                  (.fireExceptionCaught ctx (.cause f)))))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readFrame  ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (let [ ^CompositeByteBuf cbuf (getAttr ctx CBUF-KEY)
         ^XData xs (getAttr ctx XDATA-KEY)
         ^OutputStream os (if (and (not (.hasContent xs)) (tooMuchData? cbuf frame))
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
      (when-not (nil? os)
        (do (.close os))
        (let [ baos (MakeBitOS) ]
          (slurpByteBuf cbuf baos)
          (.resetContent xs baos)))
      (let [ info (getAttr ctx MSGINFO-KEY) ]
        (resetAttrs ctx)
        (.fireChannelRead ctx { :info info :payload xs }))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleWSockFrame  ""

  [^ChannelHandlerContext ctx ^WebSocketFrame frame]

  (let [ ^WebSocketServerHandshaker hs (getAttr ctx WSHSK-KEY)
         ch (.channel ctx) ]
    (log/debug "nio-wsframe: received a " (type frame))
    (cond
      (instance? CloseWebSocketFrame frame)
      (do
        (resetAttrs ctx)
        (.close hs ch ^CloseWebSocketFrame frame)
        (Try! (NetUtils/closeChannel ch)))

      (instance? PingWebSocketFrame frame)
      (.write ch (PongWebSocketFrame. (.content frame)))

      (or (instance? ContinuationWebSocketFrame frame)
          (instance? TextWebSocketFrame frame)
          (instance? BinaryWebSocketFrame frame))
      (do
        (readFrame ctx frame)
        (maybeFinzFrame ctx frame))

      :else (throw (IOException. "Bad wsock: unknown frame.")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- auxReqHandler ""

  [^ChannelHandlerContext ctx msg options]

  (let []
    (cond
      (or (instance? HttpResponse msg)
          (instance? HttpRequest msg))
      (let [ info (extractMsgInfo msg) ]
        (setAttr ctx MSGINFO-KEY info)
        (cond
          (:formpost info)
          (handleFormPost ctx msg)

          (:wsock info)
          (handleWSock ctx msg)

          :else
          (handleInboundMsg ctx msg)))

      (instance? HttpContent msg)
      (let [ info (getAttr ctx MSGINFO-KEY) ]
        (if (:formpost info)
          (handleFormPostChunk ctx msg)
          (handleMsgChunk ctx msg)))

      (instance? WebSocketFrame msg)
      (handleWSockFrame ctx msg)

      :else (throw (IOException. (str "Bad message: unknown http object: " (type msg)))) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AuxDecoder

  ^ChannelHandler
  [options]

  (NettyInboundHandler auxReqHandler options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddAuxDecoder ""

  ^ChannelPipeline
  [pipe options]

  (let []
    (.addLast ^ChannelPipeline pipe "auxdecode" (AuxDecoder options))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private auxdecode-eof nil)

