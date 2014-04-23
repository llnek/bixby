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

  comzotohlabscljc.netty.auxdecode

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:import (org.apache.commons.io IOUtils ))
  (:import (java.io ByteArrayOutputStream IOException File OutputStream ))
  (:import (java.util Map$Entry))
  (:import ( com.zotohlabs.frwk.net ULFormItems ULFileItem))
  (:import (io.netty.util AttributeKey Attribute))
  (:import (io.netty.buffer CompositeByteBuf ByteBuf Unpooled))
  (:import (io.netty.handler.codec.http.multipart DefaultHttpDataFactory DiskFileUpload
                                                  FileUpload HttpPostRequestDecoder
                                                  HttpPostRequestDecoder$EndOfDataDecoderException
                                                  InterfaceHttpData InterfaceHttpData$HttpDataType))
  (:import (io.netty.handler.stream ChunkedWriteHandler ChunkedStream))
  (:import (io.netty.channel ChannelHandlerContext Channel
                             ChannelFutureListener ChannelFuture
                             ChannelPipeline ChannelHandler))
  (:import (io.netty.handler.codec.http HttpHeaders HttpVersion HttpContent LastHttpContent
                                        HttpHeaders$Values HttpHeaders$Names
                                        HttpMessage HttpRequest HttpResponse HttpResponseStatus
                                        DefaultFullHttpResponse DefaultHttpResponse QueryStringDecoder
                                        HttpMethod HttpObject
                                        DefaultHttpRequest HttpServerCodec HttpClientCodec
                                        HttpResponseEncoder))
  (:import (io.netty.handler.ssl SslHandler))
  (:import (io.netty.handler.codec.http.websocketx WebSocketFrame WebSocketServerHandshaker
                                                   WebSocketServerHandshakerFactory ContinuationWebSocketFrame
                                                   CloseWebSocketFrame BinaryWebSocketFrame TextWebSocketFrame
                                                   PingWebSocketFrame PongWebSocketFrame))
  (:import (com.zotohlabs.frwk.net NetUtils))
  (:import (com.zotohlabs.frwk.io XData))
  (:use [comzotohlabscljc.util.core :only [ notnil? Try! TryC] ])
  (:use [comzotohlabscljc.util.str :only [strim nsb hgl?] ])
  (:use [comzotohlabscljc.netty.comms])
  (:use [comzotohlabscljc.util.io :only [NewlyTmpfile MakeBitOS] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private FORMDEC-KEY (AttributeKey. "formdecoder"))
(def ^:private FORMITMS-KEY (AttributeKey. "formitems"))
(def ^:private XDATA-KEY (AttributeKey. "xdata"))
(def ^:private XOS-KEY (AttributeKey. "ostream"))
(def ^:private MSGINFO-KEY (AttributeKey. "msginfo"))
(def ^:private CBUF-KEY (AttributeKey. "cbuffer"))
(def ^:private WSHSK-KEY (AttributeKey. "wsockhandshaker"))

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
(defn- resetAttrs ""

  [^ChannelHandlerContext ctx]

  (let [ ^HttpPostRequestDecoder dc (getAttr ctx FORMDEC-KEY)
         ^ULFormItems fis (getAttr ctx FORMITMS-KEY)
         ^ByteBuf buf (getAttr ctx CBUF-KEY) ]
    (delAttr ctx FORMITMS-KEY)
    (delAttr ctx MSGINFO-KEY)
    (delAttr ctx FORMDEC-KEY)
    (delAttr ctx CBUF-KEY)
    (delAttr ctx XDATA-KEY)
    (delAttr ctx XOS-KEY)
    (delAttr ctx WSHSK-KEY)
    (when-not (nil? buf) (.release buf))
    (when-not (nil? dc) (.destroy dc))
    (when-not (nil? fis) (.destroy fis))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isFormPost ""

  ;; boolean
  [^HttpRequest req ^String method]

  (let [ ct (cstr/lower-case (nsb (HttpHeaders/getHeader req "content-type"))) ]
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

