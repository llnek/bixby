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

  comzotohlabscljc.netty.adder )

(use '[clojure.tools.logging :only [info warn error debug] ])

(import '(io.netty.buffer Unpooled ByteBuf))
(import '(java.util List))
(import '(java.io IOException))

(import '(io.netty.channel ChannelFuture
  ChannelFutureListener
  ChannelHandler
  ChannelHandlerContext
  ChannelPipeline))

(import '(io.netty.handler.codec DecoderResult
  MessageToMessageDecoder))

(import '(io.netty.handler.codec.http
  HttpHeaders HttpHeaders$Names
  HttpVersion DefaultFullHttpResponse
  HttpResponseStatus))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private XDATA-KEY (AttributeKey. "msg-xdata"))
(def ^:private XOS-KEY (AttributeKey. "xdata-fos"))
(def ^:private MSG-KEY (AttributeKey. "full-msg"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- check-decode-result ""

  ^Throwable
  [^HttpObject msg]

  (let [ r (.getDecoderResult msg) ]
    (if (.isSuccess r)
      nil
      (if-let [x (.cause r) ]
              x (IOException. "Decode Error")) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-full-msg ""

  ^FullHttpMessage
  [^HttpObject msg ^ByteBuf bbuf flag]

  (cond
    (instance? HttpResponse msg)
    (let [ ^HttpResponse res msg
           cm (DefaultFullHttpResponse. (.getProtocolVersion res)
                                (.getStatus res)
                                bbuf
                                flag) ]
      (when flag
        (-> (.headers cm) (.set (.headers res)))
        (HttpHeaders/removeTransferEncodingChunked cm))
      cm)

    (instance? HttpRequest msg)
    (let [ ^HttpRequest req msg
           cm (DefaultFullHttpRequest. (.getProtocolVersion req)
                               (.getMethod req)
                               (.getUri req)
                               bbuf
                               flag) ]
      (when flag
        (-> (.headers cm) (.set (.headers req)))
        (HttpHeaders/removeTransferEncodingChunked cm))
      cm)

    :else nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- switch-to-file ""

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
(defn- flush-to-file ""

  [^OutputStream os ^HttpContent chk]

  (let [ c (.content chk)
         len (.readableBytes c) ]
    (.readBytes c os len)
    (.flush os)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- too-much-data ""

  [^ByteBuf content ^HttpObject chk]

  (> (.readableBytes content)
     (- (StreamLimit/xxx) (-> (.content chk)(.readableBytes)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; each message we will check the decode-result.  if we get an error
;; we assume that no more chunks will follow.  (i hope so).
;; we just hand off what we have at the moment downstream.
(defn PayloadAggregator ""

  ^ChannelHandler
  [options]

  (proxy [MessageToMessageDecoder] []
    (decode [ c msg out ]
      (let [ ^ChannelHandlerContext ctx c ]
        (cond
          (or (instance? HttpResponse msg)
              (instance? HttpRequest msg))
          (let [ err (check-decode-result msg) ]
            (if err
              (do
                (HttpHeaders/removeTransferEncodingChunked msg)
                (.add ^List out (make-full-msg msg (Unpooled/EMPTY_BUFFER) false)))
              (let [ cm (make-full-msg msg (Unpooled/compositeBuffer 1024) true) ]
                (-> (.attr ctx MSG-KEY)(.set cm)))))

          (instance? HttpContent msg)
          (do
            (let [ ^FullHttpMessage cm (-> (.attr ctx MSG-KEY) (.get))
                   ^XData xs (-> (.attr ctx XDATA-KEY)(.get))
                   ^Throwable err (check-decode-result msg)
                   ^CompositeByteBuf cbuf (.content cm)
                   ^HttpContent chk msg
                   ^OutputStream os (if (and (.isEmpty xs)
                                             (too-much-data cbuf chk))
                                        (switch-to-file ctx cbuf)
                                        (-> (.attr ctx XOS-KEY)(.get))) ]
              (when (-> (.content chk)(.isReadable))
                (if (.isEmpty xs)
                  (do
                    (.retain chk)
                    (.addComponent cbuf (.content chk))
                    (.writerIndex cbuf (+ (.writerIndex cbuf)
                                         (-> (.content chk)(.readableBytes)))))
                  (flush-to-file os chk)))
              (when (instance? LastHttpContent msg)
                (-> (.headers cm)(.add (.trailingHeaders ^LastHttpContent msg))))
              (when err
                (.setDecoderResult cm (DecoderResult/failure err)))
              (when (or (instance? LastHttpContent msg) err)
                ;; all done
                (let [ clen (if (nil? os)
                                (.readableBytes cbuf)
                                (do
                                  (.close os)
                                  (-> (.attr ctx XOS-KEY)(.set nil))
                                  (if-let [ ^XData s (-> (.attr ctx XDATA-KEY)(.get)) ]
                                    (.size s)
                                    0))) ]
                  (-> (.headers cm)(.set (HttpHeaders$Names/CONTENT_LENGTH)
                        (String/valueOf clen))))
                (.add ^List out cm))))

        :else (throw (IOException. "Bad HttpObject."))))

    )))


(def ^:private aggregator-eof nil)

