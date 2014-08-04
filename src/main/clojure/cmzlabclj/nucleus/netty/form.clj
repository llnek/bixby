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

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.nucleus.netty.form

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [ThrowIOE notnil? Bytesify] ]
        [cmzlabclj.nucleus.util.str :only [strim nsb hgl?] ])

  (:import [java.io OutputStream IOException File
                    ByteArrayOutputStream]
           [io.netty.buffer Unpooled]
           [org.apache.commons.lang3 StringUtils]
           [java.util Map$Entry]
           [java.net URLDecoder]
           [io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             SimpleChannelInboundHandler
                             ChannelFuture ChannelHandler]
           [io.netty.handler.codec.http HttpHeaders HttpMessage
                                        HttpContent
                                        HttpRequest
                                        LastHttpContent]
           [io.netty.handler.codec.http.multipart InterfaceHttpData DefaultHttpDataFactory
                                                  HttpPostRequestDecoder Attribute
                                                  HttpPostRequestDecoder$EndOfDataDecoderException
                                                  FileUpload DiskFileUpload
                                                  InterfaceHttpData$HttpDataType]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.util ReferenceCountUtil]
           [com.zotohlab.frwk.netty PipelineConfigurator
                                    AuxHttpFilter FormPostFilter
                                    DemuxedMsg]
           [com.zotohlab.frwk.netty NettyFW]
           [com.zotohlab.frwk.net ULFormItems ULFileItem]
           [com.zotohlab.frwk.io XData IOUtils]
           [com.google.gson JsonObject] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writeHttpData ""

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
            (let [^File fp  (-> (IOUtils/newTempFile false)
                                (aget 0)) ]
              (.renameTo ^DiskFileUpload fu fp)
              (.add fis (ULFileItem. nm  ct fnm  (XData. fp))))
            (let [fos (IOUtils/newTempFile true)
                  ^OutputStream os (aget fos 1)
                  ^File fp (aget fos 0) ]
              (NettyFW/slurpByteBuf (.content fu) os)
              (org.apache.commons.io.IOUtils/closeQuietly os)
              (.add fis (ULFileItem. nm  ct fnm  (XData. fp)))))))

      (= InterfaceHttpData$HttpDataType/Attribute dt)
      (let [baos (ByteArrayOutputStream. 4096)
            ^Attribute attr data ]
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
      (if-let [^InterfaceHttpData data (.next dc) ]
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
    (when (and (notnil? tkns)(> (alength tkns) 0))
      (areduce tkns n memo nil
        (let [t (nsb (aget tkns n))
              ss (StringUtils/split t \=) ]
          (when (and (notnil? ss)(> (alength ss) 0))
            (let [fi (URLDecoder/decode (aget ss 0) "utf-8")
                  fv (if (> (alength ss) 1)
                         (URLDecoder/decode  (aget ss 1) "utf-8")
                         "") ]
              (.add fis (ULFileItem. fi  (Bytesify fv)))))
          nil)))
    fis
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyFormPostFilter

  ^AuxHttpFilter
  []

  (proxy [FormPostFilter][]

    (finzAndDone [c info data ]
      (let [^ChannelHandlerContext ctx c
            ^XData xs data
            xxx (.resetAttrs ^FormPostFilter this ctx)
            itms (splitBodyParams (if (.hasContent xs)(.stringify xs) "")) ]
        (.resetContent xs itms)
        (log/debug "Fire fully decoded message to the next handler")
        (.fireChannelRead ctx (DemuxedMsg. info xs))))

    (handleFormChunk [c obj ]
      (let [^ChannelHandlerContext ctx c
            ^Object msg obj
            ^HttpPostRequestDecoder dc (NettyFW/getAttr ctx NettyFW/FORMDEC_KEY)
            ^ULFormItems fis (NettyFW/getAttr ctx NettyFW/FORMITMS_KEY) ]
        (if (nil? dc)
          ;;(proxy-super handleMsgChunk ctx msg)
          (.handleMsgChunk ^FormPostFilter this ctx msg)
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
                (.resetAttrs ^FormPostFilter this ctx)
                (.fireChannelRead ctx (DemuxedMsg. info xs))))))))

    (handleFormPost [c obj]
      (let [^ChannelHandlerContext ctx c
            ^HttpMessage msg obj
            ch (.channel ctx)
            info (NettyFW/getAttr ch NettyFW/MSGINFO_KEY)
            ctype (-> (HttpHeaders/getHeader msg "content-type")
                      nsb
                      strim
                      cstr/lower-case) ]
        (NettyFW/setAttr ctx NettyFW/FORMITMS_KEY (ULFormItems.))
        (NettyFW/setAttr ctx NettyFW/XDATA_KEY (XData.))
        (if (< (.indexOf ctype "multipart") 0)
          (do
          ;; nothing to decode.
            (NettyFW/setAttr ctx NettyFW/CBUF_KEY (Unpooled/compositeBuffer 1024))
            ;;(proxy-super handleMsgChunk ctx msg))
            (.handleMsgChunk ^FormPostFilter this ctx msg))
          (let [fac (DefaultHttpDataFactory. (IOUtils/streamLimit))
                dc (HttpPostRequestDecoder. fac msg) ]
            (NettyFW/setAttr ctx NettyFW/FORMDEC_KEY dc)
            (.handleFormChunk ^FormPostFilter this ctx msg)))))

    (channelRead0 [c obj]
      (let [^ChannelHandlerContext ctx c
            ^Object msg obj ]
        (log/debug "Channel-read0 called with msg " (type msg))
        (cond
          (instance? HttpRequest msg)
          (.handleFormPost ^FormPostFilter this ctx msg)

          (instance? HttpContent msg)
          (.handleFormChunk ^FormPostFilter this ctx msg)

          :else
          (do
            (log/error "Unexpected message type " (type msg))
            (ReferenceCountUtil/retain msg)
            (.fireChannelRead ctx msg)))))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the decoder is annotated as sharable.  this acts like the singleton.
(def ^:private HTTP-FORMPOST-FILTER (reifyFormPostFilter))

(defn ReifyFormPostFilterSingleton ""

  ^ChannelHandler
  []

  HTTP-FORMPOST-FILTER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private form-eof nil)

