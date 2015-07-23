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
(defn- handleInboundMsg ""

  [^ChannelHandlerContext ctx
   ^Channel ch
   obj]

  (SetAKey ch CBUF_KEY (Unpooled/compositeBuffer 1024))
  (SetAKey ch XDATA_KEY (XData.))
  (HandleMsgChunk ctx ch obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the decoder is annotated as sharable,  acts like the singleton.
(defonce ^:private HTTP-REQ-FILTER
  (proxy [RequestFilter][]
    (channelRead0 [c obj]
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx)
            ^Object msg obj ]
        (log/debug "channelRead0# called with msg: " (type msg))
        (cond
          (instance? HttpRequest msg)
          (handleInboundMsg ctx ch msg)

          (instance? HttpContent msg)
          (HandleMsgChunk ctx ch msg)

          :else
          (do
            (log/error "Unexpected inbound msg: " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))))

(defn ReifyReqFilterSingleton "" ^ChannelHandler [] HTTP-REQ-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;FORM POST HANDLER
(defn- resetAKeys ""
  [^Channel ch]
  (let [^HttpPostRequestDecoder
        dc (GetAKey ch FORMDEC_KEY)
        ^ULFormItems
        fis (GetAKey ch FORMITMS_KEY)]
    (DelAKey ch FORMITMS_KEY)
    (DelAKey ch FORMDEC_KEY)
    (when (some? fis) (.destroy fis))
    (when (some? dc) (.destroy dc))
    (ResetAKeys ch)
  ))

(defn- writeHttpData "Parse and eval form fields."

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
              (.renameTo ^DiskFileUpload fu fp)
              (.add fis (ULFileItem. nm  ct fnm  (XData. fp))))
            (let [[fp ^OutputStream os] (OpenTempFile)]
              (try
                (SlurpByteBuf (.content fu) os)
                (finally (CloseQ os)))
              (.add fis (ULFileItem. nm  ct fnm  (XData. fp)))))))

      (= InterfaceHttpData$HttpDataType/Attribute dt)
      (let [^Attribute attr data
            baos (ByteOS)]
        (SlurpByteBuf (.content attr) baos)
        (.add fis (ULFileItem. nm (.toByteArray baos))))

      :else
      (ThrowIOE "Bad POST: unknown http data."))
  ))


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


(defn- splitBodyParams ""

  ^ULFormItems
  [^String body]

  (log/debug "About to split form body *************************\n"
  body
  "\n****************************************************************")

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
              (.add fis (ULFileItem. fi (Bytesify fv)))))
          nil)))
    fis
  ))


(defn- finzAndDone ""

  [^ChannelHandlerContext ctx ^Channel ch ^XData xs]

  (let [info (GetAKey ch MSGINFO_KEY)
        itms (splitBodyParams (if (.hasContent xs)
                                (.stringify xs) "")) ]
    (resetAKeys ch)
    (.resetContent xs itms)
    (FireMsgToNext ctx info xs)
  ))

(defn- handleFormChunk  ""
  [^ChannelHandlerContext ctx
   ^Channel ch
   ^HttpMessage msg]
  (let [^HttpPostRequestDecoder
        dc (GetAKey ch FORMDEC_KEY)
        ^ULFormItems
        fis (GetAKey ch FORMITMS_KEY)]
    (if (nil? dc)
      (HandleMsgChunk ctx ch msg)
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
            (resetAKeys ch)
            (.fireChannelRead ctx {:info info
                                   :payload xs})))))
  ))


(defn- handleFormPost ""
  [^ChannelHandlerContext ctx
   ^Channel ch
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
        (HandleMsgChunk ctx ch msg))
      (let [dc (-> (DefaultHttpDataFactory. (StreamLimit))
                   (HttpPostRequestDecoder. msg)) ]
        (SetAKey ch FORMDEC_KEY dc)
        (handleFormChunk ctx ch msg)))
  ))


(defonce ^:private XXX
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




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setContentLength ""

  [^ChannelHandlerContext ctx clen]

  (let [info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY)
        olen (:clen info) ]
    (when-not (== olen clen)
      (log/warn "Content-length read from headers = " olen ", new clen = " clen)
      (NettyFW/setAttr ctx NettyFW/MSGINFO_KEY (assoc info :clen clen)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- appendHeaders ""

  [^ChannelHandlerContext ctx
   ^HttpHeaders hds]

  (let [info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY)
        old (:headers info)
        nnw (ExtractHeaders hds) ]
    (NettyFW/setAttr ctx
                     NettyFW/MSGINFO_KEY
                     (assoc info
                            :headers
                            (merge {} old nnw)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyMsgFunc ""

  ^CallableWithArgs
  []

  (reify CallableWithArgs
    (run [_ a1 args]
      ;; args === array of objects
      (let [^ChannelHandlerContext ctx a1
            ^String op (aget args 0) ]
        (condp = op
          "setContentLength" (setContentLength ctx (aget args 1))
          "appendHeaders" (appendHeaders ctx (aget args 1))
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
(defn- onFormPost "" [^ChannelHandlerContext ctx info]
  (NettyFW/setAttr ctx
                   NettyFW/MSGINFO_KEY (assoc info :formpost true))
  (ReifyFormPostFilterSingleton))

(defn- doDemux "Detect and handle a FORM post or a normal request."

  [^ChannelHandlerContext ctx
   ^HttpRequest req
   ^czlabclj.xlib.util.core.Muble impl]

  (let [info (ExtractMsgInfo req)
        {:keys [method uri]}
        info
        ch (.channel ctx)]
    (log/debug "Demux of message\n{}\n\n{}" req info)
    (doto ctx
      (NettyFW/setAttr NettyFW/MSGFUNC_KEY (reifyMsgFunc))
      (NettyFW/setAttr NettyFW/MSGINFO_KEY info))
    (.setf! impl :delegate nil)
    (if (.startsWith (nsb uri) "/favicon.")
      (do
        ;; ignore this crap
        (NettyFW/replyXXX ch 404)
        (.setf! impl :ignore true))
      (do
        (Expect100Filter/handle100 ctx req)
        (.setf! impl
                :delegate
                (if (IsFormPost req method)
                  (onFormPost ctx info)
                  (ReifyRequestFilterSingleton)))))
    (when-let [d (.getf impl :delegate) ]
      (-> ^AuxHttpFilter d
          (.channelReadXXX ctx req)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyHttpFilter "Filter to sort out standard request or formpost."

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

            (notnil? d)
            (-> ^AuxHttpFilter d
                (.channelReadXXX ctx msg))

            (true? e)
            nil ;; ignore

            :else
            (ThrowIOE (str "Fatal error while reading http message. " (type msg)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- exitHttpDemuxFilter "Send msg upstream and remove the filter."

  [^ChannelHandlerContext ctx msg]

  (do
    (.fireChannelRead ctx msg)
    (.remove (.pipeline ctx) "HttpDemuxFilter")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpDemuxFilter "First level filter, detects websock or normal http."

  (^ChannelHandler [options ] (MakeHttpDemuxFilter options {}))

  (^ChannelHandler [options hack]

    (let [ws (:wsock options)
          uri (:uri ws)
          tmp (MakeMMap) ]
      (proxy [ChannelInboundHandlerAdapter][]
        (channelRead [c obj]
          (log/debug "HttpDemuxFilter got this msg " (type obj))
          (let [^ChannelHandlerContext ctx c
                ^Object msg obj
                pipe (.pipeline ctx)
                ch (.channel ctx) ]
            (cond
              (and (instance? HttpRequest msg)
                   (IsWEBSock msg))
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
                (log/debug "Standard http request - swap in our own http handler.")
                (.addAfter pipe
                           "HttpDemuxFilter"
                           "ReifyHttpFilter"
                           (reifyHttpFilter))
                ;; maybe do something extra? caller decide...
                (-> (or (:onhttp hack) (constantly nil))
                    (apply ctx hack options []))
                (log/debug "Added new handler - reifyHttpFilter to the chain")
                (exitHttpDemuxFilter ctx msg))))))
  )))

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

