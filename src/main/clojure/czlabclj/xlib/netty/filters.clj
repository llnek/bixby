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

  czlabclj.xlib.netty.filters

  (:require [czlabclj.xlib.util.core
             :refer [ThrowIOE
                     MakeMMap
                     notnil?
                     spos?
                     Bytesify
                     TryC Try!
                     SafeGetJsonObject
                     SafeGetJsonInt
                     SafeGetJsonString]]
            [czlabclj.xlib.util.str
             :refer [lcase
                     ucase
                     strim
                     nsb
                     hgl?]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.netty.io]
        [czlabclj.xlib.util.io])

  (:import  [java.io File ByteArrayOutputStream InputStream IOException]
            [io.netty.channel ChannelHandlerContext ChannelPipeline
             ChannelInboundHandlerAdapter ChannelFuture
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
             FormPostFilter SimpleInboundFilter
             DemuxInboundFilter
             ErrorSinkFilter RequestFilter
             Expect100Filter AuxHttpFilter]
            [com.zotohlab.frwk.core CallableWithArgs]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.frwk.net ULFileItem ULFormItems]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeHandleError ""

  [^ChannelHandlerContext ctx]

  (let [ch (.channel ctx)
        v (GetAKey ch MSGTYPE_KEY)]
    (when-not (= "wsock" v)
      (ReplyXXX ch 500))
  ))

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
(defn SharedErrorSinkFilter ""
  ^ChannelHandler
  [] ERROR-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeHandle100 ""

  [^ChannelHandlerContext ctx msg]

  (when (and (instance? HttpMessage msg)
             (HttpHeaders/is100ContinueExpected msg))
    (-> (->> (MakeFullHttpReply HttpResponseStatus/CONTINUE)
             (.writeAndFlush ctx))
        (FutureCB (fn [^ChannelFuture f]
                    (when-not (.isSuccess f)
                      (.fireExceptionCaught ctx (.cause f))))))
  ))

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
(defn SharedExpect100Filter ""
  ^ChannelHandler
  [] EXPECT-100-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti HandleInboundMsg "" (fn [a b c & args] (class c)))
(defmethod HandleInboundMsg AuxHttpFilter

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler
   obj]

  (SetAKey ch CBUF_KEY (Unpooled/compositeBuffer 1024))
  (SetAKey ch XDATA_KEY (XData.))
  (HandleHttpContent ctx ch handler obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private HTTP-REQ-FILTER
  (proxy [RequestFilter][]
    (channelRead0 [c msg]
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx)]
        (log/debug "channelRead0# called with msg: " (type msg))
        (cond
          (instance? HttpRequest msg)
          (HandleInboundMsg ctx ch this msg)

          (instance? HttpContent msg)
          (HandleHttpContent ctx ch this msg)

          :else
          (do
            (log/error "Unexpected inbound msg: " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SharedRequestFilter ""
  ^ChannelHandler
  [] HTTP-REQ-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ResetAKeys FormPostFilter

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
    (ClearAKeys ch)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeHttpData "Parse and eval form fields"

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
        (when (.isCompleted fu)
          (if (instance? DiskFileUpload fu)
            (let [fp (TempFile)]
              (-> ^DiskFileUpload fu (.renameTo fp))
              (->> (ULFileItem. nm ct fnm (XData. fp))
                   (.add fis)))
            (let [[fp ^OutputStream os] (OpenTempFile)]
              (try
                (SlurpByteBuf (.content fu) os)
                (finally (CloseQ os)))
              (->> (ULFileItem. nm ct fnm (XData. fp))
                   (.add fis ))))))

      (= InterfaceHttpData$HttpDataType/Attribute dt)
      (let [^Attribute attr data
            baos (ByteOS)]
        (SlurpByteBuf (.content attr) baos)
        (->> (ULFileItem. nm (.toByteArray baos))
             (.add fis )))

      :else
      (ThrowIOE "Bad POST: unknown http data."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readHttpDataChunkByChunk ""

  [^ChannelHandlerContext ctx
   ^HttpPostRequestDecoder dc
   ^ULFormItems fis ]

  (try
    (while (.hasNext dc)
      (when-let [^InterfaceHttpData
                 data (.next dc) ]
        (try
          (writeHttpData ctx data fis)
          (finally
            (.release data)))))
    (catch HttpPostRequestDecoder$EndOfDataDecoderException _ )
    ;;eat it => indicates end of content chunk by chunk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitBodyParams ""

  ^ULFormItems
  [^String body]

  (log/debug "About to split form body >>>>>>>>>>>>>>>>>>>\n"
  body
  "\n<<<<<<<<<<<<<<<<<<<<<<<<<")

  (let [tkns (StringUtils/split body \&)
        fis (ULFormItems.) ]
    (when-not (empty? tkns)
      (areduce tkns n memo nil
        (let [t (nsb (aget tkns n))
              ss (StringUtils/split t \=) ]
          (when-not (empty? ss)
            (let [fi (URLDecoder/decode (aget ss 0) "utf-8")
                  fv (if (> (alength ss) 1)
                         (URLDecoder/decode (aget ss 1) "utf-8")
                         "") ]
              (->> (ULFileItem. fi (Bytesify fv))
                   (.add fis))))
          nil)))
    fis
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod FinzHttpContent FormPostFilter

  [^ChannelHandlerContext ctx ^Channel ch handler ^XData xs]

  (let [info (GetAKey ch MSGINFO_KEY)
        itms (splitBodyParams (if (.hasContent xs)
                                (.stringify xs) "")) ]
    (ResetAKeys ctx ch handler)
    (.resetContent xs itms)
    (FireMsgToNext ctx info xs)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti HandleFormPart "" (fn [a b c & args] (class c)))
(defmethod HandleFormPart FormPostFilter

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler
   msg]

  (let [^ULFormItems
        fis (GetAKey ch FORMITMS_KEY)
        ^HttpPostRequestDecoder
        dc (GetAKey ch FORMDEC_KEY)]
    (if (nil? dc)
      (HandleHttpContent ctx ch handler msg)
      (with-local-vars [err nil]
        (when (instance? HttpContent msg)
          (let [^HttpContent hc msg
                ct (.content hc) ]
            (when (and (some? ct)
                       (.isReadable ct))
              (try
                (.offer dc hc)
                (readHttpDataChunkByChunk ctx ch dc fis)
                (catch Throwable e#
                  (var-set err e#)
                  (.fireExceptionCaught ctx e#))))))
        (when (and (nil? @err)
                   (instance? LastHttpContent msg))
          (let [^XData xs (GetAKey ch XDATA_KEY)
                info (GetAKey ch MSGINFO_KEY) ]
            (DelAKey ch FORMITMS_KEY)
            (.resetContent xs fis)
            (ResetAKeys ctx ch handler)
            (FireMsgToNext ctx info xs)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti HandleFormPost "" (fn [a b c & args] (class c)))
(defmethod HandleFormPost FormPostFilter

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler
   ^HttpMessage msg]

  (let [info (GetAKey ch MSGINFO_KEY)
        ctype (-> (GetHeader msg HttpHeaders$Names/CONTENT_TYPE)
                  nsb strim lcase) ]
    (doto ch
      (SetAKey FORMITMS_KEY (ULFormItems.))
      (SetAKey XDATA_KEY (XData.)))
    (if (< (.indexOf ctype "multipart") 0)
      (do ;; nothing to decode
        (SetAKey ch CBUF_KEY (Unpooled/compositeBuffer 1024))
        (HandleHttpContent ctx ch handler msg))
      (let [dc (-> (DefaultHttpDataFactory. (StreamLimit))
                   (HttpPostRequestDecoder. msg)) ]
        (SetAKey ch FORMDEC_KEY dc)
        (HandleFormPart ctx ch handler msg)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private HTTP-FORMPOST-FILTER
  (proxy [FormPostFilter][]
    (channelRead0 [c msg]
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx)]
        (log/debug "channelRead0# called with msg: " (type msg))
        (cond
          (instance? HttpRequest msg)
          (HandleFormPost ctx ch msg)

          (instance? HttpContent msg)
          (HandleFormPart ctx ch msg)

          :else
          (do
            (log/error "Unexpected inbound msg: " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SharedFormPostFilter ""
  ^ChannelHandler
  [] HTTP-FORMPOST-FILTER)

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
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- demuxRequest ""

  [^ChannelPipeline pipe ctx ch msg]

  (let [info (MapMsgInfo msg)
        uri (nsb (:uri info))
        mtd (:method info)]
    (log/debug "demux message=>\n{}\n\n{}" msg info)
    (SetAKey ch MSGINFO_KEY info)
    (if (.startsWith uri "/favicon.")
      (do ;; ignore
        (ReplyXXX ch 404)
        false)
      (do
        (maybeHandle100 ctx msg)
        (->> (if (IsFormPost? msg mtd)
               (do
                 (SetAKey ch MSGINFO_KEY (assoc info :formpost true))
                 (SharedFormPostFilter))
               (SharedRequestFilter))
             (.addAfter pipe "InboundDemuxer" "requestFilter" ))
        true))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dropDemuxer ""

  [^ChannelPipeline pipe ^ChannelHandlerContext ctx msg]

  (.fireChannelRead ctx msg)
  (.remove pipe "InboundDemuxer"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inboundDemuxer "Level 1 filter, detects websock/normal http"

  ^ChannelHandler
  [options]

  (let [uri (get-in options [:wsock :uri])
        tmp (MakeMMap) ]
    (proxy [DemuxInboundFilter][]
      (channelRead [c msg]
        (let [^ChannelHandlerContext ctx c
              pipe (.pipeline ctx)
              ch (.channel ctx) ]
          (log/debug "inboundDemuxer got this msg: " (type msg))
          (cond
            (instance? HttpRequest msg)
            (if (IsWEBSock? msg)
              (do
                (log/debug "got a websock req - let's wait for full msg")
                (.setf! tmp :wsreq (fakeFullHttpRequest msg))
                (ReferenceCountUtil/release msg))
              (do
                (log/debug "basic http request - swap in our req-filter")
                (if-not (demuxRequest pipe ctx ch msg)
                  (.setf! tmp :ignore true)
                  (dropDemuxer pipe ctx msg))))

            (some? (.getf tmp :wsreq))
            (try
              (when (instance? LastHttpContent msg)
                (log/debug "websock upgrade request for uri " uri)
                (.addAfter pipe
                           "HttpResponseEncoder"
                           "WebSocketServerProtocolHandler"
                           (WebSocketServerProtocolHandler. uri))
                (dropDemuxer pipe ctx (.getf tmp :wsreq)))
              (finally
                (ReferenceCountUtil/release msg)))

            (true? (.getf tmp :ignore))
            (ReferenceCountUtil/release msg)

            :else
            (ThrowIOE (str "Error while reading message: " (type msg)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyPipeCfgtor "Create a customized netty pipeline"

  ^PipelineConfigurator
  [cfgtor]

  (proxy [PipelineConfigurator][]
    (assemble [pl options]
      (let [ssl (SSLServerHShake options)
            ^ChannelPipeline pipe pl]
        (when (some? ssl) (.addLast pipe "ssl" ssl))
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "InboundDemuxer" (inboundDemuxer options))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
          (.addLast (ErrorSinkFilter/getName) (SharedErrorSinkFilter)))
        (cfgtor pipe options)
        pipe))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

