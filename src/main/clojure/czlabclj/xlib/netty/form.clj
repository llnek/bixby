;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.netty.form

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core :only [ThrowIOE notnil? Bytesify]]
        [czlabclj.xlib.util.io :only [NewlyTempFile CloseQ ByteOS]]
        [czlabclj.xlib.netty.io :only [GetHdr]]
        [czlabclj.xlib.util.str :only [lcase strim nsb hgl?]])

  (:import  [java.io OutputStream IOException File ByteArrayOutputStream]
            [io.netty.buffer Unpooled]
            [org.apache.commons.lang3 StringUtils]
            [java.util Map$Entry]
            [java.net URLDecoder]
            [io.netty.channel ChannelHandlerContext Channel
             ChannelPipeline
             SimpleChannelInboundHandler
             ChannelFuture ChannelHandler]
            [io.netty.handler.codec.http HttpHeaders
             HttpHeaders$Names
             HttpMessage HttpContent HttpRequest LastHttpContent]
            [io.netty.handler.codec.http.multipart InterfaceHttpData
             DefaultHttpDataFactory
             HttpPostRequestDecoder Attribute
             HttpPostRequestDecoder$EndOfDataDecoderException
             FileUpload DiskFileUpload
             InterfaceHttpData$HttpDataType]
            [io.netty.bootstrap ServerBootstrap]
            [io.netty.util ReferenceCountUtil]
            [com.zotohlab.frwk.netty PipelineConfigurator
             AuxHttpFilter FormPostFilter DemuxedMsg]
            [com.zotohlab.frwk.netty NettyFW]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]
            [com.zotohlab.frwk.io IO XData]
            [com.google.gson JsonObject]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
            (let [[^File fp _]
                  (NewlyTempFile)]
              (.renameTo ^DiskFileUpload fu fp)
              (.add fis (ULFileItem. nm  ct fnm  (XData. fp))))
            (let [[^File fp ^OutputStream os]
                  (NewlyTempFile true)]
              (try
                (NettyFW/slurpByteBuf (.content fu) os)
                (finally (CloseQ os)))
              (.add fis (ULFileItem. nm  ct fnm  (XData. fp)))))))

      (= InterfaceHttpData$HttpDataType/Attribute dt)
      (let [^Attribute attr data
            baos (ByteOS)]
        (NettyFW/slurpByteBuf (.content attr) baos)
        (.add fis (ULFileItem. nm (.toByteArray baos))))

      :else
      (ThrowIOE "Bad POST: unknown http data."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitBodyParams ""

  ^ULFormItems
  [^String body]

  (log/debug "About to split form body *************************\n"
  body "\n"
  "****************************************************************")
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyFormPostFilter

  ^AuxHttpFilter
  []

  (proxy [FormPostFilter][]

    (finzAndDone [c data]
      (let [^ChannelHandlerContext ctx c
            ^XData xs data
            info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY)
            itms (splitBodyParams (if (.hasContent xs)
                                    (.stringify xs)
                                    "")) ]
        (.resetAttrs ^FormPostFilter this ctx)
        (.resetContent xs itms)
        (log/debug "Fire fully decoded message to the next handler")
        (.fireChannelRead ctx (DemuxedMsg. info xs))))

    (handleFormChunk [c obj ]
      (let [^ChannelHandlerContext ctx c
            ^Object msg obj
            ^FormPostFilter me this
            ^HttpPostRequestDecoder
            dc (NettyFW/getAttr ctx NettyFW/FORMDEC_KEY)
            ^ULFormItems
            fis (NettyFW/getAttr ctx
                                 NettyFW/FORMITMS_KEY) ]
        (if (nil? dc)
          (.handleMsgChunk me ctx msg)
          (with-local-vars [err nil]
            (when (instance? HttpContent msg)
              (let [^HttpContent hc msg
                    ct (.content hc) ]
                (when (and (notnil? ct)
                           (.isReadable ct))
                  (try
                    (.offer dc hc)
                    (readHttpDataChunkByChunk ctx dc fis)
                    (catch Throwable e#
                      (var-set err e#)
                      (.fireExceptionCaught ctx e#))))))
            (when (and (nil? @err)
                       (instance? LastHttpContent msg))
              (let [^XData xs (NettyFW/getAttr ctx NettyFW/XDATA_KEY)
                    info (NettyFW/getAttr ctx NettyFW/MSGINFO_KEY) ]
                (NettyFW/delAttr ctx NettyFW/FORMITMS_KEY)
                (.resetContent xs fis)
                (.resetAttrs me ctx)
                (.fireChannelRead ctx (DemuxedMsg. info xs))))))))

    (handleFormPost [c obj]
      (let [^ChannelHandlerContext ctx c
            ^HttpMessage msg obj
            ^FormPostFilter me this
            ch (.channel ctx)
            info (NettyFW/getAttr ch NettyFW/MSGINFO_KEY)
            ctype (-> (GetHdr msg HttpHeaders$Names/CONTENT_TYPE)
                      nsb strim lcase) ]
        (doto ctx
          (NettyFW/setAttr NettyFW/FORMITMS_KEY (ULFormItems.))
          (NettyFW/setAttr NettyFW/XDATA_KEY (XData.)))
        (if (< (.indexOf ctype "multipart") 0)
          (do
            ;; nothing to decode.
            (NettyFW/setAttr ctx NettyFW/CBUF_KEY (Unpooled/compositeBuffer 1024))
            (.handleMsgChunk me ctx msg))
          (let [fac (DefaultHttpDataFactory. (IO/streamLimit))
                dc (HttpPostRequestDecoder. fac msg) ]
            (NettyFW/setAttr ctx NettyFW/FORMDEC_KEY dc)
            (.handleFormChunk me ctx msg)))))

    (channelRead0 [c obj]
      (let [^ChannelHandlerContext ctx c
            ^FormPostFilter me this
            ^Object msg obj ]
        (log/debug "Channel-read0 called with msg " (type msg))
        (cond
          (instance? HttpRequest msg)
          (.handleFormPost me ctx msg)

          (instance? HttpContent msg)
          (.handleFormChunk me ctx msg)

          :else
          (do
            (log/error "Unexpected message type " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the decoder is annotated as sharable.  this acts like the singleton.
(def ^:private HTTP-FORMPOST-FILTER (reifyFormPostFilter))

(defn ReifyFormPostFilterSingleton "Return the singleton FormPost Filter."

  ^ChannelHandler
  []

  HTTP-FORMPOST-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private form-eof nil)

