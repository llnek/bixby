;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.netty.filters

  (:require
    [czlab.xlib.util.core :refer [ThrowIOE MubleObj
    Cast? spos? Bytesify]]
    [czlab.xlib.util.str :refer [lcase ucase
    strim hgl?]]
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.xlib.netty.io]
        [czlab.xlib.util.io])

  (:import
    [java.io File ByteArrayOutputStream InputStream IOException]
    [io.netty.channel ChannelHandlerContext ChannelPipeline
    ChannelInboundHandlerAdapter ChannelFuture
    ChannelDuplexHandler
    ChannelOption ChannelFutureListener
    Channel ChannelHandler]
    [org.apache.commons.lang3 StringUtils]
    [io.netty.buffer Unpooled]
    [java.net URLDecoder URL ]
    [io.netty.handler.codec.http HttpHeaders HttpMessage
    HttpHeaders$Values
    HttpHeaders$Names
    LastHttpContent DefaultFullHttpResponse
    DefaultFullHttpRequest HttpContent
    HttpRequest HttpResponse FullHttpRequest
    QueryStringDecoder HttpResponseStatus
    HttpRequestDecoder HttpVersion
    HttpObjectAggregator HttpResponseEncoder]
    [io.netty.handler.codec.http.multipart InterfaceHttpData
    DefaultHttpDataFactory
    HttpPostRequestDecoder Attribute
    HttpPostRequestDecoder$EndOfDataDecoderException
    FileUpload DiskFileUpload
    InterfaceHttpData$HttpDataType]
    [io.netty.util ReferenceCountUtil]
    [io.netty.handler.codec.http.websocketx
    WebSocketServerProtocolHandler]
    [io.netty.handler.stream ChunkedWriteHandler]
    [com.zotohlab.frwk.netty PipelineConfigurator
    SimpleInboundFilter
    InboundAdapter
    ErrorSinkFilter MessageFilter
    Expect100Filter AuxHttpFilter]
    [com.zotohlab.frwk.core CallableWithArgs]
    [com.zotohlab.frwk.io XData]
    [com.zotohlab.frwk.net ULFileItem ULFormItems]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbgPipelineHandlers ""

  [^ChannelPipeline pipe]

  (log/debug "pipeline: handlers= %s" (cs/join "|" (.names pipe))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeHandleError ""

  [^ChannelHandlerContext ctx]

  (let [ch (.channel ctx)
        v (GetAKey ch MSGTYPE_KEY)]
    (when (not= "wsock" v)
      (ReplyXXX ch 500))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ERROR-FILTER
  (proxy [ErrorSinkFilter][]
    (exceptionCaught [c t]
      (log/error t "")
      (maybeHandleError c))

    (channelRead0 [c msg]
      (maybeHandleError c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SharedErrorSinkFilter "" ^ChannelHandler [] ERROR-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeHandle100 ""

  [^ChannelHandlerContext ctx msg]

  (when (and (instance? HttpMessage msg)
             (HttpHeaders/is100ContinueExpected msg))
    (-> (->> (FullHttpReply* HttpResponseStatus/CONTINUE)
             (.writeAndFlush ctx))
        (FutureCB #(let [^ChannelFuture f %1]
                     (when-not (.isSuccess f)
                       (.fireExceptionCaught ctx (.cause f))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private EXPECT-100-FILTER
  (proxy [Expect100Filter][]
    (channelRead0 [c msg]
      (let [^ChannelHandlerContext ctx c]
        (maybeHandle100 ctx msg)
        ;; simplechannelinboundhandler does a release, so add one here
        (ReferenceCountUtil/retain msg)
        (.fireChannelRead ctx msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SharedExpect100Filter "" ^ChannelHandler [] EXPECT-100-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleHttpMessage ""

  [^ChannelHandlerContext ctx
   ^Channel ch
   obj]

  (SetAKey ch CBUF_KEY (Unpooled/compositeBuffer 1024))
  (SetAKey ch XDATA_KEY (XData.))
  (HandleHttpContent ctx ch :http obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ResetAKeys :formpost

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler]

  (let [^ULFormItems
        fis (GetAKey ch FORMITMS_KEY)
        ^HttpPostRequestDecoder
        dc (GetAKey ch FORMDEC_KEY)]
    (DelAKey ch FORMITMS_KEY)
    (DelAKey ch FORMDEC_KEY)
    (when (some? fis) (.destroy fis))
    (when (some? dc) (.destroy dc))
    (ClearAKeys ch)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeHttpData

  "Parse and eval form fields"

  [^ChannelHandlerContext ctx
   ^InterfaceHttpData data
   ^ULFormItems fis ]

  (let [dt (.getHttpDataType data)
        nm (.name dt) ]
    (cond
      (= InterfaceHttpData$HttpDataType/FileUpload dt)
      (let [^FileUpload fu data
            ct (.getContentType fu)
            fnm (.getFilename fu) ]
        (when
          (.isCompleted fu)
          (if
            (instance? DiskFileUpload fu)
            (let [fp (TempFile)]
              (-> ^DiskFileUpload
                  fu (.renameTo fp))
              (->> (XData. fp)
                   (ULFileItem. nm ct fnm )
                   (.add fis)))
            ;else
            (let [[fp os] (OpenTempFile)]
              (try
                (SlurpByteBuf (.content fu) os)
                (finally (CloseQ os)))
              (->> (XData. fp)
                   (ULFileItem. nm ct fnm )
                   (.add fis ))))))

      (= InterfaceHttpData$HttpDataType/Attribute dt)
      (let [^Attribute attr data
            baos (ByteOS)]
        (SlurpByteBuf (.content attr) baos)
        (->> (.toByteArray baos)
             (ULFileItem. nm )
             (.add fis )))

      :else
      (ThrowIOE "Bad POST: unknown http data."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readHttpDataChunkByChunk ""

  [^ChannelHandlerContext ctx
   ^HttpPostRequestDecoder dc
   ^ULFormItems fis ]

  (try
    (while (.hasNext dc)
      (when-some [^InterfaceHttpData
                  data (.next dc) ]
        (try
          (writeHttpData ctx data fis)
          (finally
            (.release data)))))
    (catch HttpPostRequestDecoder$EndOfDataDecoderException _ )))
    ;;eat it => indicates end of content chunk by chunk

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitBodyParams ""

  ^ULFormItems
  [^String body]

  (log/debug "about to split form body %s%s%s"
             ">>>>>>>>>>>>>>>>>>>\n"
             "\n<<<<<<<<<<<<<<<<<<<<<<<<<" body)

  (let [tkns (.split body "&")
        fis (ULFormItems.) ]
    (when-not (empty? tkns)
      (areduce tkns n memo nil
        (let [t (str (aget tkns n))
              ss (.split t "=") ]
          (when-not (empty? ss)
            (let [fi (-> (aget ss 0)
                         (URLDecoder/decode "utf-8"))
                  fv (if (> (alength ss) 1)
                         (-> (aget ss 1)
                             (URLDecoder/decode  "utf-8"))
                         "") ]
              (->> (Bytesify fv)
                   (ULFileItem. fi )
                   (.add fis))))
          nil)))
    fis))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod FinzHttpContent :formpost

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler ^XData xs]

  (let [info (GetAKey ch MSGINFO_KEY) ]
    (ResetAKeys ctx ch handler)
    (->> (if (.hasContent xs)
           (.stringify xs)
           "")
         (splitBodyParams )
         (.resetContent xs ))
    (FireMsgToNext ctx info xs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleFormPart ""

  [^ChannelHandlerContext ctx
   ^Channel ch
   msg]

  (let [fis (GetAKey ch FORMITMS_KEY)
        dc (GetAKey ch FORMDEC_KEY)]
    (if (nil? dc)
      (HandleHttpContent ctx ch :formpost msg)
      (with-local-vars [err nil]
        (when
          (instance? HttpContent msg)
          (let [^HttpContent
                hc msg
                ct (.content hc) ]
            (when (and (some? ct)
                       (.isReadable ct))
              (try
                (-> ^HttpPostRequestDecoder
                    dc
                    (.offer hc))
                (readHttpDataChunkByChunk ctx ch dc fis)
                (catch Throwable e#
                  (var-set err e#)
                  (.fireExceptionCaught ctx e#))))))
        (when (and (nil? @err)
                   (instance? LastHttpContent msg))
          (let [info (GetAKey ch MSGINFO_KEY)
                xs (GetAKey ch XDATA_KEY)]
            (DelAKey ch FORMITMS_KEY)
            (-> ^XData xs (.resetContent fis))
            (ResetAKeys ctx ch :formpost)
            (FireMsgToNext ctx info xs)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleFormPost ""

  [^ChannelHandlerContext ctx
   ^Channel ch
   ^HttpMessage msg]

  (let [info (GetAKey ch MSGINFO_KEY)
        ctype (->> "Content-Type"
                   (GetHeader msg)
                   str
                   strim
                   lcase) ]
    (doto ch
      (SetAKey CBUF_KEY (Unpooled/compositeBuffer 1024))
      (SetAKey FORMITMS_KEY (ULFormItems.))
      (SetAKey XDATA_KEY (XData.)))
    (if (< (.indexOf ctype "multipart") 0)
      (HandleHttpContent ctx ch :formpost msg)
      ;else
      (let [dc (-> (DefaultHttpDataFactory. (StreamLimit))
                   (HttpPostRequestDecoder. msg)) ]
        (SetAKey ch FORMDEC_KEY dc)
        (HandleFormPart ctx ch msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FireAndQuit ""

  [^ChannelPipeline pipe ^ChannelHandlerContext ctx
   handler msg]

  (log/debug "fireAndQuit: about to remove handler: %s" handler)
  (.fireChannelRead ctx msg)
  (if (instance? ChannelHandler handler)
    (.remove pipe ^ChannelHandler handler)
    (.remove pipe (str handler)))
  (DbgPipelineHandlers pipe))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fakeFullHttpRequest ""

  ^FullHttpRequest
  [^HttpRequest req]

  (let [rc (DefaultFullHttpRequest. (.getProtocolVersion req)
                                    (.getMethod req)
                                    (.getUri req)) ]
    (-> (.headers rc)
        (.set (.headers req)))
    ;;(-> (.trailingHeaders rc) (.set (.trailingHeaders req)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private HTTP-FILTER
  (proxy [MessageFilter][]
    (channelRead0 [c msg]
      (let [^ChannelHandlerContext ctx c
            pipe (.pipeline ctx)
            ch (.channel ctx)]
        (cond
          (instance? HttpRequest msg)
          (let [info (MapMsgInfo msg)
                ff (->> (:method info)
                        (IsFormPost? msg)) ]
            (->> (assoc info :formpost ff)
                 (SetAKey ch MSGINFO_KEY ))
            (maybeHandle100 ctx msg)
            (if ff
              (HandleFormPost ctx ch msg)
              (HandleHttpMessage ctx ch msg)))

          (instance? HttpContent msg)
          (let [info (GetAKey ch MSGINFO_KEY)]
            (if (:formpost info)
              (HandleFormPart ctx ch msg)
              (HandleHttpContent ctx ch :http msg)))

          :else
          (do
            (log/error "unexpected inbound msg: %s" (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SharedHttpFilter "" ^ChannelHandler [] HTTP-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private WSOCK-FILTER
  (proxy [InboundAdapter][]
    (channelRead [c msg]
      (let [^ChannelHandlerContext ctx c
            pipe (.pipeline ctx)
            ch (.channel ctx) ]
        (cond
          (and (instance? HttpRequest msg)
               (IsWEBSock? msg))
          (let [uri (-> ^HttpRequest msg (.getUri))
                old (GetAKey ch TOBJ_KEY)]
            (log/debug "got a websock req - let's wait for full msg")
            (->> (merge old {:wsreq (fakeFullHttpRequest msg)
                             :wsuri uri})
              (SetAKey ch TOBJ_KEY))
            (ReferenceCountUtil/release msg))

          (some? (:wsreq (GetAKey ch TOBJ_KEY)))
          (try
            (when (instance? LastHttpContent msg)
              (let [tmp (GetAKey ch TOBJ_KEY)
                    path (->> (str (:wsuri tmp))
                              (QueryStringDecoder. )
                              (.path))]
              (log/debug "websock upgrade request for uri %s" path)
              (.addAfter pipe
                         "HttpResponseEncoder"
                         "WebSocketServerProtocolHandler"
                         (WebSocketServerProtocolHandler. path))
              (->> (:wsreq tmp)
                   (FireAndQuit pipe ctx this ))))
            (finally
              (ReferenceCountUtil/release msg)))

          :else
          ;;msg not released so no need to retain
          (FireAndQuit pipe ctx this  msg))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SharedWSockFilter "" ^ChannelHandler [] WSOCK-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyPipeCfgtor

  "Create a customized netty pipeline"

  ^PipelineConfigurator
  [cfgtor]

  (proxy [PipelineConfigurator][]
    (assemble [pl options]
      (let [ssl (SSLServerHShake options)
            ^ChannelPipeline pipe pl]
        (when (some? ssl) (.addLast pipe "ssl" ssl))
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "WSockFilter" (SharedWSockFilter))
          (.addLast "HttpFilter" (SharedHttpFilter))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
          (.addLast ErrorSinkFilter/NAME (SharedErrorSinkFilter)))
        (cfgtor pipe options)
        pipe))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

