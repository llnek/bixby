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

  czlabclj.xlib.netty.io

  (:require [czlabclj.xlib.util.core
             :refer [Try!
                     TryC
                     RNil
                     ThrowIOE
                     spos?
                     bool!]]
            [czlabclj.xlib.util.io
             :refer [CloseQ
                     StreamLimit
                     OpenTempFile
                     ByteOS]]
            [czlabclj.xlib.util.str
             :refer [lcase
                     ucase
                     strim
                     nsb
                     hgl?]])

  (:require [czlabclj.xlib.util.logging :as log]
            [clojure.string :as cs])

  (:import  [io.netty.buffer CompositeByteBuf ByteBuf ByteBufHolder Unpooled]
            [io.netty.util CharsetUtil AttributeKey ReferenceCounted]
            [io.netty.handler.codec.http.websocketx WebSocketFrame]
            [io.netty.bootstrap Bootstrap ServerBootstrap]
            [java.net URL InetAddress InetSocketAddress]
            [com.zotohlab.frwk.netty PipelineConfigurator]
            [com.zotohlab.frwk.net SSLTrustMgrFactory]
            [com.zotohlab.frwk.core CallableWithArgs]
            [com.zotohlab.frwk.io XData]
            [io.netty.channel.nio NioEventLoopGroup]
            [java.io File ByteArrayOutputStream OutputStream]
            [io.netty.channel.socket.nio
             NioDatagramChannel
             NioServerSocketChannel]
            [javax.net.ssl
             KeyManagerFactory SSLContext
             SSLEngine TrustManagerFactory]
            [java.security KeyStore SecureRandom]
            [io.netty.handler.ssl SslHandler]
            [io.netty.channel Channel ChannelFuture
             ChannelHandlerContext ChannelPipeline
             ChannelHandler ChannelOption
             ChannelFutureListener]
            [io.netty.handler.codec.http HttpVersion
             FullHttpResponse LastHttpContent
             HttpHeaders$Values
             HttpHeaders$Names
             HttpMessage HttpResponse
             DefaultFullHttpResponse
             DefaultHttpResponse HttpContent
             HttpRequest HttpResponseStatus
             HttpHeaders QueryStringDecoder]
            [java.util Map$Entry]
            [io.netty.handler.stream ChunkedWriteHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(defonce ^AttributeKey FORMDEC_KEY  (AttributeKey/valueOf "formdecoder"))
(defonce ^AttributeKey FORMITMS_KEY (AttributeKey/valueOf "formitems"))
(defonce ^AttributeKey MSGFUNC_KEY (AttributeKey/valueOf "msgfunc"))
(defonce ^AttributeKey MSGINFO_KEY (AttributeKey/valueOf "msginfo"))
(defonce ^AttributeKey CBUF_KEY (AttributeKey/valueOf "cbuffer"))
(defonce ^AttributeKey XDATA_KEY (AttributeKey/valueOf "xdata"))
(defonce ^AttributeKey XOS_KEY (AttributeKey/valueOf "ostream"))
(defonce ^AttributeKey MSGTYPE_KEY (AttributeKey/valueOf "msgtype"))
(defonce ^AttributeKey TOBJ_KEY (AttributeKey/valueOf "tmpobj"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FutureCB "Register a callback upon operation completion"

  [^ChannelFuture cf func]

  (->> (reify ChannelFutureListener
         (operationComplete [_ ff] (Try! (func ff))))
       (.addListener cf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WriteLastContent "Write out the last content flag"

  ^ChannelFuture
  [^Channel ch & [flush?]]

  (log/debug "writing last http-content out to client")
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
      (log/debug "netty-op-complete: %s" msg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpReply ""

  ^HttpResponse
  [ & [code]]

  (let [code (or code 200)]
    (DefaultHttpResponse. HttpVersion/HTTP_1_1
                          (HttpResponseStatus/valueOf code))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFullHttpReply ""

  ^FullHttpResponse
  [ & [status payload]]

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
                (assoc memo (lcase n) (into [] rc)))))
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

  (let [baos (ByteOS)]
    (SlurpByteBuf buf baos)
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeSSL? ""

  [^ChannelHandlerContext ctx]

  (-> ctx (.pipeline) (.get SslHandler) (some?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MapParams ""

  [^QueryStringDecoder decr]

  (reduce (fn [memo ^Map$Entry en]
            (let [rc (RNil (.getValue en))]
              (if (empty? rc)
                memo
                (assoc memo (.getKey en) (into [] rc)))))
          {}
          (-> decr (.parameters) (.entrySet))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetMethod ""

  ^String
  [^HttpRequest req]

  (let [mo (GetHeader req "X-HTTP-Method-Override")
        mt (-> req (.getMethod) (.name))]
    (ucase (if-not (empty? mo) mo mt))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MapMsgInfo ""

  [^HttpMessage msg]

  (with-local-vars
    [info
      {:is-chunked (HttpHeaders/isTransferEncodingChunked msg)
       :keep-alive (HttpHeaders/isKeepAlive msg)
       :host (HttpHeaders/getHeader msg "Host" "")
       :protocol (-> msg (.getProtocolVersion) (.toString))
       :clen (HttpHeaders/getContentLength msg 0)
       :uri2 ""
       :query ""
       :wsock false
       :uri ""
       :status ""
       :code 0
       :method ""
       :params {}
       :headers (MapHeaders (.headers msg)) }]
    (cond
      (instance? HttpResponse msg)
      (let [s (-> ^HttpResponse msg (.getStatus))]
        (->> (merge @info {:status (nsb (.reasonPhrase s))
                           :code  (.code s)})
             (var-set info)))
      (instance? HttpRequest msg)
      (let [^HttpRequest req msg
            uriStr (nsb (.getUri req))
            pos (.indexOf uriStr "?")
            dc (QueryStringDecoder. uriStr)]
        (->> (-> (merge @info {:method (GetMethod req)
                               :uri (.path dc)
                               :uri2 uriStr})
                 (assoc :params (MapParams dc)))
             (var-set info))
        (when (>= pos 0)
          (->> (assoc @info :query (.substring uriStr pos))
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
(defn GetUriPath ""

  ^String
  [^HttpRequest req]

  (-> (QueryStringDecoder. (.getUri req)) (.path)))

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

  (if (> lastSum (StreamLimit))
    (let [[fp os] (OpenTempFile)]
      (.resetContent x fp)
      os)
    out
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseCF ""

  [^ChannelFuture cf & [keepAlive?] ]

  (when (and (some? cf)
             (not (bool! keepAlive?)))
    (.addListener cf (ChannelFutureListener/CLOSE))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReplyXXX  ""

  [^Channel ch status & [keepAlive?] ]

  (let [rsp (MakeFullHttpReply status)]
    (log/debug "return HTTP status %s back to client" status)
    (CloseCF (.writeAndFlush ch rsp) (bool! keepAlive?))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SendRedirect ""

  [^Channel ch permanent ^String targetUrl]

  (let [rsp (MakeFullHttpReply (if permanent 301 307))]
    (log/debug "redirecting to -> %s" targetUrl)
    (HttpHeaders/setHeader rsp "location" targetUrl)
    (CloseCF (.writeAndFlush ch rsp))
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
    (log/debug "object %s: has ref-count = %s"
               (try (.toString ^Object obj)
                    (catch Throwable _ "???"))
               (.refCnt ^ReferenceCounted obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ClearAKeys "Clear out channel attributes"

  [^Channel ch]

  (let [^ByteBuf buf (GetAKey ch CBUF_KEY)]
    (when (some? buf) (.release buf))
    (DelAKey ch MSGFUNC_KEY)
    (DelAKey ch MSGINFO_KEY)
    (DelAKey ch CBUF_KEY)
    (DelAKey ch XDATA_KEY)
    (DelAKey ch XOS_KEY)
    (DelAKey ch TOBJ_KEY)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ResetAKeys "Clear ch attributes" (fn [a b c & args] c))
(defmethod ResetAKeys :http

  [^ChannelHandlerContext ctx ^Channel ch handler]

  (ClearAKeys ch))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetContentLength ""

  [^Channel ch clen]

  (let [info (GetAKey ch MSGINFO_KEY)
        olen (:clen info) ]
    (when (or (nil? olen)
              (not (== olen clen)))
      (log/warn "content-length from headers = %s, new clen = %s" olen clen)
      (SetAKey ch MSGINFO_KEY (assoc info :clen clen)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AppendHeaders ""

  [^Channel ch ^HttpHeaders hds]

  (let [info (GetAKey ch MSGINFO_KEY)
        old (:headers info)
        nnw (MapHeaders hds) ]
    (SetAKey ch
             MSGINFO_KEY
             (assoc info
                    :headers (merge {} old nnw)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FlushContent ""

  [^OutputStream os ^ByteBufHolder chunc]

  (when-let [buf (.content chunc)]
    (SlurpByteBuf buf os)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FireMsgToNext ""

  [^ChannelHandlerContext ctx info data]

  (log/debug "fire fully decoded message to the next handler")
  (.fireChannelRead ctx {:info info :payload data}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tooMuchData? ""

  [^ByteBuf content ^ByteBufHolder chunc]

  (if-let [buf (.content chunc)]
    (> (.readableBytes content)
       (- (StreamLimit) (.readableBytes buf)))
    false
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- switchBufToFile ""

  ^OutputStream
  [^ChannelHandlerContext ctx ^Channel ch ^CompositeByteBuf bbuf]

  (let [^XData xs (GetAKey ch XDATA_KEY)
        [fp os] (OpenTempFile)]
    (SlurpByteBuf bbuf os)
    (.resetContent xs fp)
    (SetAKey ch XOS_KEY os)
    os
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti FinzHttpContent "" (fn [a b c & args] c))
(defmethod FinzHttpContent :http

  [^ChannelHandlerContext ctx ^Channel ch handler ^XData xs]

  (let [info (GetAKey ch MSGINFO_KEY)]
    (ResetAKeys ctx ch handler)
    (FireMsgToNext ctx info xs)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti HandleLastContent "" (fn [a b c & args]  c))
(defmethod HandleLastContent :http

  [^ChannelHandlerContext ctx ^Channel ch handler msg]

  (when (instance? LastHttpContent msg)
    (log/debug "got the final last-http-content chunk, end of message")
    (let [^CallableWithArgs func (GetAKey ch MSGFUNC_KEY)
          ^OutputStream os (GetAKey ch XOS_KEY)
          ^ByteBuf cbuf (GetAKey ch CBUF_KEY)
          ^XData xs (GetAKey ch XDATA_KEY)]
      (AppendHeaders ch (-> ^LastHttpContent msg (.trailingHeaders)))
      (if (nil? os)
        (let [baos (ByteOS)]
          (SlurpByteBuf cbuf baos)
          (.resetContent xs baos))
        (CloseQ os))
      (SetContentLength ch (.size xs))
      (FinzHttpContent ctx ch handler xs))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti HandleHttpContent "" (fn [a b c & args]  c))
(defmethod HandleHttpContent :http

  [^ChannelHandlerContext ctx ^Channel ch handler msg]

  (when (instance? HttpContent msg)
    (log/debug "got a valid http-content chunk, part of a message")
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
              (.writerIndex cbuf (+ (.writerIndex cbuf)
                                    (.readableBytes cc))))
            (FlushContent @os chk)))))
    ;;is this the last chunk?
    (HandleLastContent ctx ch handler msg)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsFormPost? "Detects if this request is a http form post"

  [^HttpMessage msg ^String method]

  (let [ct (-> (GetHeader msg HttpHeaders$Names/CONTENT_TYPE)
               nsb lcase) ]
    (and (or (= "POST" method)(= "PUT" method)(= "PATCH" method))
         (or (>= (.indexOf ct "multipart/form-data") 0)
             (>= (.indexOf ct "application/x-www-form-urlencoded") 0)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsWEBSock? "Detects if request is a websocket request"

  [^HttpRequest req]

  (let [^String cn (-> (GetHeader req HttpHeaders$Names/CONNECTION)
                        nsb lcase)
        ^String ws (-> (GetHeader req HttpHeaders$Names/UPGRADE)
                        nsb lcase)
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
        (let [pwd (when (some? pwdStr) (.toCharArray pwdStr))
              x (SSLContext/getInstance flavor)
              ks (KeyStore/getInstance ^String
                                       (if (.endsWith keyUrlStr ".jks")
                                         "JKS"
                                         "PKCS12"))
              t (->> (TrustManagerFactory/getDefaultAlgorithm)
                     (TrustManagerFactory/getInstance))
              k (->> (KeyManagerFactory/getDefaultAlgorithm)
                     (KeyManagerFactory/getInstance)) ]
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
(defn SSLClientHShake "Create a client-side handler for SSL"

  ^ChannelHandler
  [options]

  (TryC
    (let [^String flavor (or (:flavor options) "TLS")
          m (SSLTrustMgrFactory/getTrustManagers)
          ctx (doto (SSLContext/getInstance flavor)
                    (.init nil m nil)) ]
      (SslHandler. (doto (.createSSLEngine ctx)
                         (.setUseClientMode true))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Channel StartServer "Start a Netty server"
(fn [a b c] (class a)))
(defmulti ^Channel StopServer "Stop a Netty server"
(fn [a b] (class a)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer ServerBootstrap

  ^Channel
  [^ServerBootstrap bs ^String host port]

  (let [ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost)) ]
    (log/debug "nettyTCPServer: running on host %s:%s" ip port)
    (-> (.bind bs ip (int port))
        (.sync)
        (.channel))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod StartServer Bootstrap

  ^Channel
  [^Bootstrap bs ^String host port]

  (let [ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost)) ]
    (log/debug "nettyUDPServer: running on host %s:%s" ip port)
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
            #(when-let [gp (.group bs) ]
                (Try! (.shutdownGracefully gp)))))

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

  (let [thds (:threads options)
        bk (:backlog options)
        rb (:rcvBuf options)
        wk (:worker thds)
        bo (:boss thds)]
    (doto (ServerBootstrap.)
      (.group (getEventGroup (int (or bo 4)))
              (getEventGroup (int (or wk 6))))
      (.channel NioServerSocketChannel)
      (.option ChannelOption/SO_REUSEADDR true)
      (.option ChannelOption/SO_BACKLOG
               (int (or bk 100)))
      (.childOption ChannelOption/SO_RCVBUF
                    (int (or rb (* 2 1024 1024))))
      (.childOption ChannelOption/TCP_NODELAY true)
      (.childHandler (.configure cfg options)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn InitUDPServer "Create and configure a UDP Netty Server"

  ^Bootstrap
  [^PipelineConfigurator cfg options]

  (let [thds (:threads options)
        rb (:rcvBuf options)
        bo (:boss thds)]
    (doto (Bootstrap.)
      (.group (getEventGroup (int (or bo 4))))
      (.channel NioDatagramChannel)
      (.option ChannelOption/TCP_NODELAY true)
      (.option ChannelOption/SO_RCVBUF
               (int (or rb (* 2 1024 1024))))
      (.handler (.configure cfg options)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
