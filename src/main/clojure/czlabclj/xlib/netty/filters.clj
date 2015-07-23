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
             :refer
             [ThrowIOE MakeMMap notnil? spos?
              TryC Try! SafeGetJsonObject
              SafeGetJsonInt SafeGetJsonString]]
            [czlabclj.xlib.util.str
             :refer
             [lcase ucase strim nsb hgl?]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.netty.request]
        [czlabclj.xlib.netty.io]
        [czlabclj.xlib.netty.form])

  (:import  [io.netty.channel ChannelHandlerContext ChannelPipeline
             ChannelInboundHandlerAdapter ChannelFuture
             ChannelOption ChannelFutureListener
             Channel ChannelHandler]
            [io.netty.handler.ssl SslHandler]
            [io.netty.buffer Unpooled]
            [io.netty.channel.socket.nio NioDatagramChannel
             NioServerSocketChannel]
            [io.netty.channel.nio NioEventLoopGroup]
            [org.apache.commons.lang3 StringUtils]
            [java.net URL InetAddress InetSocketAddress]
            [java.io InputStream IOException]
            [java.util Map Map$Entry]
            [io.netty.handler.codec.http HttpHeaders HttpMessage
             HttpHeaders$Values
             HttpHeaders$Names
             LastHttpContent DefaultFullHttpResponse
             DefaultFullHttpRequest HttpContent
             HttpRequest HttpResponse FullHttpRequest
             QueryStringDecoder HttpResponseStatus
             HttpRequestDecoder HttpVersion
             HttpResponseEncoder]
           [io.netty.bootstrap Bootstrap ServerBootstrap]
           [io.netty.util CharsetUtil ReferenceCountUtil]
           [io.netty.handler.codec.http.websocketx
            WebSocketServerProtocolHandler]
           [io.netty.handler.stream ChunkedWriteHandler]
           [javax.net.ssl KeyManagerFactory SSLContext
            SSLEngine TrustManagerFactory]
           [java.security KeyStore SecureRandom]
           [com.zotohlab.frwk.netty PipelineConfigurator
            RequestFilter
            Expect100Filter AuxHttpFilter
            ErrorSinkFilter]
           [com.zotohlab.frwk.netty SimpleInboundFilter]
           [com.zotohlab.frwk.core CallableWithArgs]
           [com.zotohlab.frwk.io XData]
           [com.zotohlab.frwk.net SSLTrustMgrFactory]
           [com.google.gson JsonObject JsonPrimitive] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


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
  (HandleMsgChunk ctx ch handler obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the decoder is annotated as sharable,  acts like the singleton.
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
          (HandleMsgChunk ctx ch this msg)

          :else
          (do
            (log/error "Unexpected inbound msg: " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyRequestFilterSingleton ""
  ^ChannelHandler
  [] HTTP-REQ-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ResetAKeys FormPostFilter

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler]

  (let [^HttpPostRequestDecoder
        dc (GetAKey ch FORMDEC_KEY)
        ^ULFormItems
        fis (GetAKey ch FORMITMS_KEY)]
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
                         (URLDecoder/decode  (aget ss 1) "utf-8")
                         "") ]
              (->> (ULFileItem. fi (Bytesify fv))
                   (.add fis))))
          nil)))
    fis
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod FinzAndDone FormPostFilter

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
(defmulti HandleFormChunk  "" (fn [a b c & args] (class c)))
(defmethod HandleFormChunk  FormPostFilter

  [^ChannelHandlerContext ctx
   ^Channel ch
   handler
   msg]

  (let [^HttpPostRequestDecoder
        dc (GetAKey ch FORMDEC_KEY)
        ^ULFormItems
        fis (GetAKey ch FORMITMS_KEY)]
    (if (nil? dc)
      (HandleMsgChunk ctx ch handler msg)
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
        (HandleMsgChunk ctx ch handler msg))
      (let [dc (-> (DefaultHttpDataFactory. (StreamLimit))
                   (HttpPostRequestDecoder. msg)) ]
        (SetAKey ch FORMDEC_KEY dc)
        (HandleFormChunk ctx ch handler msg)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private HTTP-FORMPOST-FILTER
  (proxy [FormPostFilter][]
    (channelRead0 [c obj]
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx)
            ^Object msg obj ]
        (log/debug "channelRead0# called with msg: " (type msg))
        (cond
          (instance? HttpRequest msg)
          (handleFormPost ctx ch msg)

          (instance? HttpContent msg)
          (handleFormChunk ctx ch msg)

          :else
          (do
            (log/error "Unexpected inbound msg: " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))))

(defn ReifyFormPostFilterSingleton ""
  ^ChannelHandler
  [] HTTP-FORMPOST-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyMsgFunc ""

  ^CallableWithArgs
  []

  (reify CallableWithArgs
    (run [_ a1 args]
      ;; args === array of objects
      (let [^ChannelHandlerContext ctx a1
            ch (.channel ctx)
            ^String op (aget args 0) ]
        (condp = op
          "setContentLength" (SetContentLength ch (aget args 1))
          "appendHeaders" (AppendHeaders ch (aget args 1))
          nil)))
  ))

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
(defn- onFormPost ""

  [^Channel ch info]

  (SetAKey ch MSGINFO_KEY (assoc info :formpost true))
  (ReifyFormPostFilterSingleton))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doDemux "Detect and handle a FORM post or a normal request."

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^czlabclj.xlib.util.core.Muble impl]

  (let [info (MapMsgInfo req)
        {:keys [method uri]}
        info
        ch (.channel ctx)]
    (log/debug "Demux of message\n{}\n\n{}" req info)
    (doto ch
      (SetAKey MSGFUNC_KEY (reifyMsgFunc))
      (SetAKey MSGINFO_KEY info))
    (.setf! impl :delegate nil)
    (if (.startsWith (nsb uri) "/favicon.")
      (do
        ;; ignore this crap
        (ReplyXXX ch 404)
        (.setf! impl :ignore true))
      (do
        (Expect100Filter/handle100 ctx req)
        (.setf! impl
                :delegate
                (if (IsFormPost? req method)
                  (onFormPost ch info)
                  (ReifyRequestFilterSingleton)))))
    (when-let [d (.getf impl :delegate) ]
      (-> ^AuxHttpFilter d
          (.channelReadXXX ctx req)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyHttpFilter "Filter to sort out standard request or formpost"

  ^ChannelHandler
  []

  (let [impl (MakeMMap {:delegate nil
                        :ignore false}) ]
    (proxy [AuxHttpFilter][]
      (channelRead0 [ctx msg]
        (let [d (.getf impl :delegate)
              e (.getf impl :ignore) ]
          (log/debug "HttpHandler got msg = " (type msg))
          (log/debug "HttpHandler delegate = " d)
          (cond
            (instance? HttpRequest msg)
            (doDemux ctx msg impl)

            (some? d)
            (-> ^AuxHttpFilter d
                (.channelReadXXX ctx msg))

            (true? e)
            nil ;; ignore

            :else
            (ThrowIOE (str "Error while reading message: " (type msg)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- exitHttpDemuxFilter "Send msg upstream and remove the filter"

  [^ChannelHandlerContext ctx msg]

  (do
    (.fireChannelRead ctx msg)
    (.remove (.pipeline ctx) "HttpDemuxFilter")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpDemuxFilter "Level 1 filter, detects websock/normal http"

  ^ChannelHandler
  [options & [hack]]

  (let [ws (:wsock options)
        uri (:uri ws)
        hack (or hack {})
        tmp (MakeMMap) ]
    (proxy [ChannelInboundHandlerAdapter][]
      (channelRead [c msg]
        (log/debug "HttpDemuxFilter got this msg: " (type msg))
        (let [^ChannelHandlerContext ctx c
              pipe (.pipeline ctx)
              ch (.channel ctx) ]
          (cond
            (and (instance? HttpRequest msg)
                 (IsWEBSock? msg))
            (do
              ;; wait for full request
              (log/debug "Got a websock req - let's wait for full msg.")
              (.setf! tmp :wsreq (fakeFullHttpRequest msg))
              (.setf! tmp :wait4wsock true)
              (ReferenceCountUtil/release msg))

            (true? (.getf tmp :wait4wsock))
            (try
              (when (instance? LastHttpContent msg)
                (log/debug "Got a wsock upgrade request for uri "
                           uri
                           ", swapping to netty's websock handler.")
                (.addAfter pipe
                           "HttpResponseEncoder"
                           "WebSocketServerProtocolHandler"
                           (WebSocketServerProtocolHandler. uri))
                ;; maybe do something extra when wsock? caller decide...
                (-> (or (:onwsock hack) (constantly nil))
                    (apply ctx hack options []))
                (exitHttpDemuxFilter ctx (.getf tmp :wsreq)))
              (finally
                (ReferenceCountUtil/release msg)))

            :else
            (do
              (log/debug "Basic http request - swap in our own http handler.")
              (.addAfter pipe
                         "HttpDemuxFilter"
                         "ReifyHttpFilter"
                         (reifyHttpFilter))
              ;; maybe do something extra? caller decide...
              (-> (or (:onhttp hack) (constantly nil))
                  (apply ctx hack options []))
              (log/debug "Added new handler - reifyHttpFilter to the chain")
              (exitHttpDemuxFilter ctx msg))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyHTTPPipe "Create a netty request pipeline."

  (^PipelineConfigurator
    [^String yourHandlerName yourHandlerFn]
    (ReifyHTTPPipe yourHandlerName
                   yourHandlerFn
                   (fn [^ChannelPipeline pipe options]
                     (.addAfter pipe
                                "HttpRequestDecoder",
                                "HttpDemuxFilter"
                                (MakeHttpDemuxFilter options))
                     pipe)))

  (^PipelineConfigurator
    [^String yourHandlerName yourHandlerFn
     epilogue]
    (proxy [PipelineConfigurator][]
      (assemble [pl options]
        (let [ssl (SSLServerHShake options)
              ^ChannelPipeline pipe pl]
          (when-not (nil? ssl) (.addLast pipe "ssl" ssl))
          (doto pipe
            ;;(.addLast "IdleStateHandler" (IdleStateHandler. 100 100 100))
            (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
            (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
            (.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
            (.addLast yourHandlerName
                      ^ChannelHandler (yourHandlerFn options))
            (ErrorSinkFilter/addLast))
          (when (fn? epilogue)
            (epilogue pipe options))
          pipe)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

