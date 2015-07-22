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

  czlabclj.xlib.netty.core

  (:require [czlabclj.xlib.util.core
             :refer
             [RNil ]]
            [czlabclj.xlib.util.str
             :refer
             [lcase ucase strim nsb hgl?]])

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cs])

  (:import  [io.netty.util CharsetUtil AttributeKey ReferenceCounted]
            [io.netty.channel Channel ChannelFuture
             ChannelHandlerContext ChannelPipeline
             ChannelFutureListener]
            [io.netty.handler.codec.http HttpVersion FullHttpResponse
             HttpMessage HttpResponse DefaultFullHttpResponse
             DefaultHttpResponse
             HttpRequest HttpResponseStatus
             HttpHeaders QueryStringDecoder]
            [io.netty.buffer ByteBuf Unpooled]
            [java.util Map$Entry]
            [java.io File ByteArrayOutputStream OutputStream]
            [org.apache.commons.lang3.tuple MutablePair]
            [io.netty.handler.ssl SslHandler]
            [io.netty.handler.stream ChunkedWriteHandler]

            [com.zotohlab.frwk.netty PipelineConfigurator]
            [com.zotohlab.frwk.io IO XData]

           ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(defonce ^AttributeKey FORMDEC_KEY  (AttributeKey/valueOf "formdecoder"))
(defonce ^AttributeKey FORMITMS_KEY (AttributeKey/valueOf "formitems"))
(defonce ^AttributeKey MSGFUNC_KEY (AttributeKey/valueOf "msgfunc"))
(defonce ^AttributeKey MSGINFO_KEY (AttributeKey/valueOf "msginfo"))
(defonce ^AttributeKey CBUF_KEY (AttributeKey/valueOf "cbuffer"))
(defonce ^AttributeKey XDATA_KEY (AttributeKey/valueOf "xdata"))
(defonce ^AttributeKey XOS_KEY (AttributeKey/valueOf "ostream"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbgNettyDone ""

  ^ChannelFutureListener
  [msg]

  (reify ChannelFutureListener
    (operationComplete [_ _]
      (log/debug "netty-op-complete: {}" msg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpReply ""

  ^HttpResponse
  [code]

  (DefaultHttpResponse. HttpVersion/HTTP_1_1
                        (HttpResponseStatus/valueOf code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpReply ""

  ^HttpResponse
  []

  (MakeHttpReply 200))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetAKey ""
  [^Channel ch ^AttributeKey akey  aval]
  (-> ch (.attr akey) (.set aval)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DelAKey ""
  [^Channel ch ^AttributeKey akey]
  (-> ch (.attr akey) (.remove)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetAKey ""
  ^Object
  [^Channel ch ^AttributeKey akey]
  (-> ch (.attr akey) (.get)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MapHeaders ""
  [^HttpHeaders hdrs]
  (reduce (fn [memo n]
            (let [rc (RNil (.getAll hdrs ^String n))]
              (if (empty? rc)
                memo
                (assoc memo (lcase n) rc))))
          {}
          (.names hdrs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SlurpByteBuf ""
  [^ByteBuf buf ^OutputStream os]
  (let [len (if (nil? buf) 0 (.readableBytes buf))]
    (if (> len 0)
      (.readBytes buf os len)
      (.flush os))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SlurpByteBuf ""
  ^bytes
  [^ByteBuf buf]
  (let [baos (ByteArrayOutputStream. 4096)]
    (SlurpByteBuf buf baos)
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MapParams ""
  [^QueryStringDecoder decr]
  (reduce (fn [memo ^Map$Entry en]
            (let [rc (RNil (.getValue en))]
              (if (empty? rc)
                memo
                (assoc memo (.getKey en) rc))))
          {}
          (-> decr (.parameters) (.entrySet))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MapMsgInfo ""
  [^HttpMessage msg]
  (with-local-vars
    [info
      {"is-chunked" (HttpHeaders/isTransferEncodingChunked msg)
       "keep-alive" (HttpHeaders/isKeepAlive msg)
       "host" (HttpHeaders/getHeader msg "Host" "")
       "protocol" (-> msg (.getProtocolVersion) (.toString))
       "clen" (HttpHeaders/getContentLength msg 0)
       "uri2" ""
       "query" ""
       "wsock" false
       "uri" ""
       "status" ""
       "code" 0
       "method" ""
       "params" {}
       "headers" (MapHeaders (.headers msg)) }]
    (cond
      (instance? HttpResponse msg)
      (let [s (-> ^HttpResponse msg (.getStatus))]
        (->> (merge @info {"status" (nsb (.reasonPhrase s))
                          "code"  (.code s)})
             (var-set info)))
      (instance? HttpRequest msg)
      (let [mo (HttpHeaders/getHeader msg "X-HTTP-Method-Override")
            ^HttpRequest req msg
            uriStr (nsb (.getUri req))
            pos (.indexOf uriStr "?")
            md (-> req (.getMethod) (.name))
            mt (if-not (empty? mo) mo md)
            dc (QueryStringDecoder. uriStr)]
        (->> (-> (merge @info {"method" (ucase mt)
                               "uri" (.path dc)
                               "uri2" uriStr})
                 (assoc "params" (MapParams dc)))
             (var-set info))
        (when (>= pos 0)
          (->> (assoc @info "query" (.substring uriStr pos))
               (var-set info))))
      :else nil)
    @info
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddWriteChunker ""

  ^ChannelPipeline
  [^ChannelPipeline pipe]
  (doto pipe
    (.addLast  "ChunkedWriteHandler"  (ChunkedWriteHandler.))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetMethod ""

  ^String
  [^HttpRequest req]
  (let [mo (HttpHeaders/getHeader req "X-HTTP-Method-Override")
        mt (-> req (.getMethod) (.name) (ucase))]
    (if-not (empty? mo)
      mo
      mt)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetUriPath ""

  ^String
  [^HttpRequest req]
  (QueryStringDecoder. (-> req (.getUri) (.path))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SockItDown ""

  ^long
  [^ByteBuf cbuf ^OutputStream out lastSum]

  (let [cnt (if (nil? cbuf) 0 (.readableBytes cbuf))]
    (loop [bits (byte-array 4096)
           total cnt]
      (when (> total 0)
        (let [len (Math/min (int 4096) (int total))]
          (.readBytes cbuf bits 0 len)
          (.write out bits 0 len)
          (recur bits (- total len)))))
    (.flush out)
    (+ lastSum cnt)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SwapFileBacked ""

  ^OutputStream
  [^XData x ^OutputStream out lastSum]

  (if (> lastSum  (IO/streamLimit))
    (let [fos (IO/newTempFile true)]
      (.resetContent x (.getLeft fos))
      (.getRight fos))
    out
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeChannelInitor ""

  ^ChannelHandler
  [^PipelineConfigurator cfg options]
  (.configure cfg options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseCF ""
  [^ChannelFuture cf keepAlive]
  (when (and (some? cf)
             (not keepAlive))
    (.addListener cf (ChannelFutureListener/CLOSE))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReplyXXX  ""

  ([^Channel ch status keepAlive]
    (let [rsp (MakeHttpReply status)]
      (HttpHeaders/setContentLength rsp 0)
      (TryC
        (log/debug "Return HTTP status ({}) back to client" status)
        (CloseCF (.writeAndFlush ch rsp) keepAlive))))

  ([^Channel ch status ]
   (ReplyXXX ch status false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeSSL ""

  [^ChannelHandlerContext ctx]
  (-> ctx
      (.channel)
      (.pipeline)
      (.get SslHandler)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply ""

  ^FullHttpResponse
  [status ^ByteBuf payload]
  (DefaultFullHttpResponse. HttpVersion/HTTP_1_1
                            (HttpResponseStatus/valueOf status)
                            payload))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply ""

  ^FullHttpResponse
  [status ^String payload]
  (DefaultFullHttpResponse. HttpVersion/HTTP_1_1
                            (HttpResponseStatus/valueOf status)
                            (Unpooled/copiedBuffer payload CharsetUtil/UTF_8)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply ""

  ^FullHttpResponse
  [status]
  (DefaultFullHttpResponse. HttpVersion/HTTP_1_1
                            (HttpResponseStatus/valueOf status)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply ""

  ^FullHttpResponse
  []
  (MakeFullHttpReply 200))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SendRedirect ""
  [^Channel ch permanent ^String targetUrl]
  (let [rsp (MakeFullHttpReply (if permanent 301 307))]
    (log/debug "Redirecting to -> {}" targetUrl)
    (HttpHeaders/setHeader rsp "location" targetUrl)
    (CloseCF (.writeAndFlush ch rsp) false)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Continue100 ""
  [^ChannelHandlerContext ctx]
  (-> ctx
      (.channel)
      (.writeAndFlush (MakeFullHttpReply 100))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbgRefCount ""
  [obj]
  (when (instance? ReferenceCounted obj)
    (log/debug "Object {}: has ref-count = {}"
               (try (.toString obj)
                    (catch Throwable _ "???"))
               (.refCnt obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResetAKeys ""

  [^Channel ch]

  (let [^ByteBuf buf (GetAKey ch CBUF_KEY)]
    (when-not (nil? buf)
      (.release buf))
    (DelAKey ch MSGFUNC_KEY)
    (DelAKey ch MSGINFO_KEY)
    (DelAKey ch CBUF_KEY)
    (DelAKey ch XDATA_KEY)
    (DelAKey ch XOS_KEY)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHeaderNames ""

  [info]

  (if-let [hds (get info "headers")]
    (keys hds)
    #{}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHeaderValues ""

  [info header]

  {:pre [(some? header)]}

  (if-let [hds (get info "headers")]
    (or (get hds (lcase header)) [])
    []
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetParameters ""

  [info]

  (if-let [hds (get info "params")]
    (keys hds)
    #{}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetParameterValues ""

  [info pm]

  {:pre [(some? pm)]}

  (if-let [pms (get info "params")]
    (or (get pms pm) [])
    []
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
