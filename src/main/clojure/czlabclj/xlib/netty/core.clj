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
             [Try! TryC RNil ThrowIOE spos?]]
            [czlabclj.xlib.util.files :as fs]
            [czlabclj.xlib.util.io :refer [StreamLimit OpenTempFile ]]
            [czlabclj.xlib.util.str
             :refer
             [lcase ucase strim nsb hgl?]])

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cs])

  (:import  [io.netty.util CharsetUtil AttributeKey ReferenceCounted]

            [io.netty.channel.socket.nio NioDatagramChannel
             NioServerSocketChannel]
            [io.netty.channel.nio NioEventLoopGroup]
            [java.net URL InetAddress InetSocketAddress]
            [io.netty.bootstrap Bootstrap ServerBootstrap]
            [com.zotohlab.frwk.netty PipelineConfigurator]
            [javax.net.ssl KeyManagerFactory SSLContext
             SSLEngine TrustManagerFactory]
            [java.security KeyStore SecureRandom]
            [com.zotohlab.frwk.net SSLTrustMgrFactory]

            [io.netty.channel Channel ChannelFuture
             ChannelHandlerContext ChannelPipeline
             ChannelHandler ChannelOption
             ChannelFutureListener]
            [com.zotohlab.frwk.core CallableWithArgs]
            [io.netty.handler.codec.http.websocketx WebSocketFrame]
            [io.netty.handler.codec.http HttpVersion
             FullHttpResponse LastHttpContent
             HttpHeaders$Values
             HttpHeaders$Names
             HttpMessage HttpResponse
             DefaultFullHttpResponse
             DefaultHttpResponse HttpContent
             HttpRequest HttpResponseStatus
             HttpHeaders QueryStringDecoder]
            [io.netty.buffer CompositeByteBuf ByteBuf Unpooled]
            [java.util Map$Entry]
            [java.io File ByteArrayOutputStream OutputStream]
            [org.apache.commons.lang3.tuple MutablePair]
            [org.apache.commons.io IOUtils]
            [io.netty.handler.ssl SslHandler]
            [io.netty.handler.stream ChunkedWriteHandler]
            [com.zotohlab.frwk.netty PipelineConfigurator]
            [com.zotohlab.frwk.io XData]




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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FutureCB "Register a callback upon operation completion"

  [^ChannelFuture cf func]

  (->> (reify ChannelFutureListener
         (operationComplete [_ ff]
           (Try! (apply func (.isSuccess ^ChannelFuture ff) []))))
       (.addListener cf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WriteLastContent "Write out the last content flag."

  ^ChannelFuture
  [^Channel ch &[flush?]]

  (log/debug "Writing last http-content out to client.")
  (if flush?
    (.writeAndFlush ch LastHttpContent/EMPTY_LAST_CONTENT)
    (.write ch LastHttpContent/EMPTY_LAST_CONTENT)
  ))

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
  [ &[code]]

  (let [code (or code 200)]
    (DefaultHttpResponse. HttpVersion/HTTP_1_1
                          (HttpResponseStatus/valueOf code))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply ""

  ^FullHttpResponse
  [ &[status payload]]

  (let
    [status (or status 200)
     p (cond
         (instance? String payload)
         (Unpooled/copiedBuffer ^String payload CharsetUtil/UTF_8)
         (instance? ByteBuf payload)
         payload
         :else nil)]
    (if (nil? p)
      (DefaultFullHttpResponse. HttpVersion/HTTP_1_1
                                (HttpResponseStatus/valueOf status))
      (DefaultFullHttpResponse. HttpVersion/HTTP_1_1
                                (HttpResponseStatus/valueOf status) p))
  ))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddHeader "Add the header value"

  [^HttpMessage msg
   ^String nm ^String value]

  (HttpHeaders/addHeader msg nm value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetHeader "Set the header value"

  [^HttpMessage msg
   ^String nm ^String value]

  (HttpHeaders/setHeader msg nm value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHeader "Get the header value"

  ^String
  [^HttpMessage msg ^String nm]

  (HttpHeaders/getHeader msg nm))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SlurpByteBuf ""
  [^ByteBuf buf ^OutputStream os]
  (let [len (if (nil? buf) 0 (.readableBytes buf))]
    (if (> len 0)
      (.readBytes buf os (int len))
      (.flush os))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SlurpBytes ""
  ^bytes
  [^ByteBuf buf]
  (let [baos (ByteArrayOutputStream. 4096)]
    (SlurpByteBuf buf baos)
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddMoreHeaders ""

  [^ChannelHandlerContext ctx ^Channel ch ^HttpHeaders hds]

  (-> ^CallableWithArgs (GetAKey ch MSGFUNC_KEY)
      (.run ctx (object-array ["appendHeaders" hds]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeSSL? ""

  [^ChannelHandlerContext ctx]

  (-> ctx
      (.pipeline)
      (.get SslHandler)
      (some?)))

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
  (-> (QueryStringDecoder. (.getUri req))
      (.path)))

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

  (if (> lastSum  (StreamLimit))
    (let [[fp os] (OpenTempFile)]
      (.resetContent x fp)
      os)
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
               (try (.toString ^Object obj)
                    (catch Throwable _ "???"))
               (.refCnt ^ReferenceCounted obj))))

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
;;
(defn FireMsgToNext ""
  [^ChannelHandlerContext ctx info data]
  (log/debug "Fire fully decoded message to the next handler")
  (.fireChannelRead ctx {:info info :payload data}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HANDLE MESSAGE CHUNK (HttpContent)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tooMuchData? ""

  [^ByteBuf content chunc]

  (let
    [buf (cond
           (instance? WebSocketFrame chunc)
           (-> ^WebSocketFrame chunc (.content))
           (instance? HttpContent chunc)
           (-> ^HttpContent chunc (.content))
           :else nil)]
    (if (some? buf)
      (> (.readableBytes content)
         (- (StreamLimit) (.readableBytes buf)))
      false)
  ))

(defn- switchBufToFile ""

  ^OutputStream
  [^ChannelHandlerContext ctx ^Channel ch ^CompositeByteBuf bbuf]

  (let [^XData xs (GetAKey ch XDATA_KEY)
        [fp os] (OpenTempFile)]
    (SlurpByteBuf bbuf os)
    (-> ^OutputStream
        os (.flush))
    (.resetContent xs fp)
    (SetAKey ch XOS_KEY os)
    os
  ))

(defn- flushToFile ""

  [^OutputStream os chunc]

  (let
    [buf (cond
           (instance? WebSocketFrame chunc)
           (-> ^WebSocketFrame chunc (.content))
           (instance? HttpContent chunc)
           (-> ^HttpContent chunc (.content))
           :else nil) ]
    (when (some? buf)
      (SlurpByteBuf buf os)
      (.flush os))
  ))

(defn- finzAndDone ""

  [^ChannelHandlerContext ctx ^Channel ch ^XData xs]

  (let [info (GetAKey ch MSGINFO_KEY)]
    (ResetAKeys ch)
    (FireMsgToNext ctx info xs)
  ))

(defn- maybeFinzMsgChunk ""

  [^ChannelHandlerContext ctx ^Channel ch msg]

  (when (instance? LastHttpContent msg)
    (log/debug "Got the final last-http-content chunk, end of message")
    (let [^CallableWithArgs func (GetAKey ch MSGFUNC_KEY)
          ^OutputStream os (GetAKey ch XOS_KEY)
          ^ByteBuf cbuf (GetAKey ch CBUF_KEY)
          ^XData xs (GetAKey ch XDATA_KEY)]
      (AddMoreHeaders ctx ch (-> ^LastHttpContent msg (.trailingHeaders)))
      (if (nil? os)
        (let [baos (ByteArrayOutputStream.)]
          (SlurpByteBuf cbuf baos)
          (.resetContent xs baos))
        (IOUtils/closeQuietly os))
      (.run func ctx (object-array ["setContentLength" (.size xs)]))
      (finzAndDone ctx ch xs))
  ))

(defn HandleMsgChunk ""

  [^ChannelHandlerContext ctx ^Channel ch msg]

  (when (instance? HttpContent msg)
    (log/debug "Got a valid http-content chunk, part of a message.")
    (with-local-vars [os nil]
      (let [^CompositeByteBuf cbuf (GetAKey ch CBUF_KEY)
            ^XData xs (GetAKey ch XDATA_KEY)
            ^HttpContent chk msg
            cc (.content chk)]
        (var-set os (GetAKey ch XOS_KEY))
        ;;if we have not done already, may be see if we need to switch to file
        (when (and (not (.hasContent xs))
                   (tooMuchData? cbuf msg))
          (var-set os (switchBufToFile ctx ch cbuf)))
        (when (.isReadable cc)
          (if (nil? @os)
            (do
              (.retain chk)
              (.addComponent cbuf cc)
              (.writerIndex cbuf (+ (.writerIndex cbuf) (.readableBytes cc))))
            (flushToFile @os chk)))))
    ;;is this the last chunk?
    (maybeFinzMsgChunk ctx ch msg)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsFormPost? "Detects if this request is a http form post"

  [^HttpMessage msg ^String method]

  (let [ct (-> (GetHeader msg HttpHeaders$Names/CONTENT_TYPE)
               nsb strim lcase) ]
    (and (or (= "POST" method)(= "PUT" method)(= "PATCH" method))
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsWEBSock? "Detects if request is a websocket request"

  [^HttpRequest req]

  (let [^String cn (-> (GetHeader req HttpHeaders$Names/CONNECTION)
                        nsb strim lcase)
        ^String ws (-> (GetHeader req HttpHeaders$Names/UPGRADE)
                        nsb strim lcase)
        ^String mo (-> (GetHeader req "X-HTTP-Method-Override")
                        nsb strim ucase) ]
    (and (>= (.indexOf ws "websocket") 0)
         (>= (.indexOf cn "upgrade") 0)
         (= "GET" (if-not (hgl? mo)
                    (-> (.getMethod req)
                        (.name)
                        (ucase))
                    mo)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SSLServerHShake "Create a server-side handler for SSL"

  ^ChannelHandler
  [options]

  (let [^String flavor (or (:flavor options) "TLS")
        ^String keyUrlStr (:serverKey options)
        ^String pwdStr (:passwd options) ]
    (when (hgl? keyUrlStr)
      (TryC
        (let [pwd (when-not (nil? pwdStr) (.toCharArray pwdStr))
              x (SSLContext/getInstance flavor)
              ks (KeyStore/getInstance ^String
                                       (if (.endsWith keyUrlStr ".jks")
                                         "JKS"
                                         "PKCS12"))
              t (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
              k (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm)) ]
          (with-open [inp (-> (URL. keyUrlStr)
                              (.openStream)) ]
            (.load ks inp pwd)
            (.init t ks)
            (.init k ks pwd)
            (.init x
                   (.getKeyManagers k)
                   (.getTrustManagers t)
                   (SecureRandom/getInstance "SHA1PRNG"))
            (SslHandler. (doto (.createSSLEngine x)
                           (.setUseClientMode false)))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SSLClientHShake "Create a client side handler for SSL"

  ^ChannelHandler
  [options]

  (TryC
    (let [^String flavor (or (:flavor options) "TLS")
          ctx (doto (SSLContext/getInstance flavor)
                    (.init nil (SSLTrustMgrFactory/getTrustManagers) nil)) ]
      (SslHandler. (doto (.createSSLEngine ctx)
                         (.setUseClientMode true))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- demuxSvrType "" [a & args] (class a))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Channel StartServer "Start a Netty server." demuxSvrType)
(defmulti ^Channel StopServer "Stop a Netty server." demuxSvrType)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer ServerBootstrap

  ^Channel
  [^ServerBootstrap bs ^String host port]

  (let [ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost)) ]
    (log/debug "NettyTCPServer: running on host " ip ", port " port)
    (try
      (-> (.bind bs ip (int port))
          (.sync)
          (.channel))
      (catch InterruptedException e#
        (ThrowIOE e#)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer Bootstrap

  ^Channel
  [^Bootstrap bs ^String host port]

  (let [ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost)) ]
    (log/debug "NettyUDPServer: running on host " ip ", port " port)
    (-> (.bind bs ip (int port))
        (.channel))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StopServer ServerBootstrap

  [^ServerBootstrap bs ^Channel ch]

  (FutureCB (.close ch)
            #(let [gc (.childGroup bs)
                   gp (.group bs) ]
               (when (some? gc) (Try! (.shutdownGracefully gc)))
               (when (some? gp) (Try! (.shutdownGracefully gp))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StopServer Bootstrap

  [^Bootstrap bs ^Channel ch]

  (FutureCB (.close ch)
            #(let [gp (.group bs) ]
                (when (some? gp) (Try! (.shutdownGracefully gp))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getEventGroup ""

  ^NioEventLoopGroup
  [thds]

  (if (spos? thds)
    (NioEventLoopGroup. thds)
    (NioEventLoopGroup.)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitTCPServer "Create and configure a TCP Netty Server"

  ^ServerBootstrap
  [^PipelineConfigurator cfg options]

  (let [thds (:threads options)]
    (doto (ServerBootstrap.)
      (.group (getEventGroup (or (:boss thds) 4))
              (getEventGroup (or (:worker thds) 6)))
      (.channel NioServerSocketChannel)
      (.option ChannelOption/SO_REUSEADDR true)
      (.option ChannelOption/SO_BACKLOG
               (int (or (:backlog options) 100)))
      (.childOption ChannelOption/SO_RCVBUF
                    (int (or (:rcvBuf options)
                             (* 2 1024 1024))))
      (.childOption ChannelOption/TCP_NODELAY true)
      (.childHandler (.configure cfg options)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitUDPServer "Create and configure a UDP Netty Server"

  ^Bootstrap
  [^PipelineConfigurator cfg options]

  (let [thds (:threads options)]
    (doto (Bootstrap.)
      (.group (getEventGroup (or (:boss thds) 4)))
      (.channel NioDatagramChannel)
      (.option ChannelOption/TCP_NODELAY true)
      (.option ChannelOption/SO_RCVBUF
               (int (or (:rcvBuf options)
                        (* 2 1024 1024)))))
  ))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
