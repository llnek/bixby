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

  czlab.skaro.io.netty

  (:require
    [czlab.xlib.net.routes :refer [MakeRouteCracker RouteCracker]]
    [czlab.xlib.util.str :refer [lcase hgl? strim nichts?]]
    [czlab.xlib.util.core
    :refer [try! Stringify ThrowIOE
    Muble NextLong MakeMMap notnil? ConvLong]]
    [czlab.skaro.io.webss :refer [MakeWSSession]]
    [czlab.xlib.util.mime :refer [GetCharset]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.xlib.netty.filters]
        [czlab.xlib.netty.io]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.core]
        [czlab.skaro.io.http]
        [czlab.skaro.io.triggers])

  (:import
    [java.io Closeable File IOException RandomAccessFile]
    [java.net HttpCookie URI URL InetSocketAddress]
    [java.net SocketAddress InetAddress]
    [java.util ArrayList List HashMap Map]
    [com.google.gson JsonObject]
    [com.zotohlab.frwk.server Emitter]
    [com.zotohlab.skaro.io HTTPEvent HTTPResult
    IOSession
    WebSockEvent WebSockResult]
    [javax.net.ssl SSLContext]
    [java.nio.channels ClosedChannelException]
    [io.netty.handler.codec.http HttpRequest
    HttpResponse HttpResponseStatus
    CookieDecoder ServerCookieEncoder
    DefaultHttpResponse HttpVersion
    HttpRequestDecoder
    HttpResponseEncoder DefaultCookie
    HttpHeaders$Names LastHttpContent
    HttpHeaders Cookie QueryStringDecoder]
    [org.apache.commons.codec.net URLCodec]
    [io.netty.bootstrap ServerBootstrap]
    [io.netty.channel Channel ChannelHandler
    ChannelFuture
    ChannelFutureListener
    SimpleChannelInboundHandler
    ChannelPipeline ChannelHandlerContext]
    [io.netty.handler.stream ChunkedFile
    ChunkedStream ChunkedWriteHandler]
    [com.zotohlab.skaro.mvc WebAsset HTTPRangeInput]
    [com.zotohlab.frwk.netty
    MessageFilter
    ErrorSinkFilter PipelineConfigurator]
    [io.netty.handler.codec.http.websocketx
    WebSocketFrame
    BinaryWebSocketFrame TextWebSocketFrame]
    [io.netty.buffer ByteBuf Unpooled]
    [com.zotohlab.frwk.core Hierarchial Identifiable]
    [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaToCookie ""

  ^Cookie
  [^HttpCookie c ^URLCodec cc]

  ;; stick with version 0, Java's HttpCookie defaults to 1 but that
  ;; screws up the Path attribute on the wire => it's quoted but
  ;; browser seems to not like it and mis-interpret it.
  ;; Netty's cookie defaults to 0, which is cool with me.
  (doto (DefaultCookie. (.getName c)
                        (.encode cc (str (.getValue c))))
    ;;(.setComment (.getComment c))
    (.setDomain (.getDomain c))
    (.setMaxAge (.getMaxAge c))
    (.setPath (.getPath c))
    ;;(.setDiscard (.getDiscard c))
    (.setVersion 0)
    (.setHttpOnly (.isHttpOnly c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeClose ""

  [^HTTPEvent evt ^ChannelFuture cf]

  (when (and (not (.isKeepAlive evt))
             (some? cf))
    (.addListener cf ChannelFutureListener/CLOSE)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csToNetty ""

  [cookies]

  (let [cc (URLCodec. "utf-8") ]
    (persistent! (reduce #(conj! %1
                                 (ServerCookieEncoder/encode (javaToCookie %2 cc)))
                         (transient [])
                         (seq cookies)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- netty-ws-reply ""

  [^Channel ch ^WebSockEvent evt src]

  (let [^WebSockResult res (.getResultObj evt)
        ^XData xs (.getData res)
        bits (.javaBytes xs)
        ^WebSocketFrame
        f (cond
            (.isBinary res)
            (BinaryWebSocketFrame. (Unpooled/wrappedBuffer (.javaBytes xs)))
            :else
            (TextWebSocketFrame. (.stringify xs))) ]
    (.writeAndFlush ch f)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyOneFile ""

  [^RandomAccessFile raf
   ^HTTPEvent evt
   ^HttpResponse rsp ]

  (let [ct (GetHeader rsp "content-type")
        rv (.getHeaderValue evt "range") ]
    (if-not (HTTPRangeInput/isAcceptable rv)
      (ChunkedFile. raf)
      (let [r (HTTPRangeInput. raf ct rv)
            n (.process r rsp) ]
        (if (> n 0)
          r
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyReply ""

  [^Channel ch ^HTTPEvent evt src]

  ;;(log/debug "nettyReply called by event with uri: " (.getUri evt))
  (let [^czlab.xlib.util.core.Muble
        res (.getResultObj evt)
        cks (csToNetty (.getf res :cookies))
        code (.getf res :code)
        rsp (MakeHttpReply code)
        loc (str (.getf res :redirect))
        data (.getf res :data)
        hdrs (.getf res :hds) ]

    ;;(log/debug "about to reply " (.getStatus ^HTTPResult res))

    (with-local-vars [clen 0 raf nil payload nil]
      (doseq [[nm vs] (seq hdrs)]
        (when-not (= "content-length" (lcase nm))
          (doseq [vv (seq vs)]
            (AddHeader rsp nm vv))))
      (doseq [s cks]
        (AddHeader rsp HttpHeaders$Names/SET_COOKIE s) )
      (cond
        (and (>= code 300)
             (< code 400))
        (when-not (cs/blank? loc)
          (SetHeader rsp "Location" loc))

        (and (>= code 200)
             (< code 300)
             (not= "HEAD" (.method evt)))
        (do
          (var-set  payload
                    (condp instance? data
                      WebAsset
                      (let [^WebAsset ws data]
                        (SetHeader rsp "content-type" (.contentType ws))
                        (var-set raf
                                 (RandomAccessFile. (.getFile ws)
                                                    "r"))
                        (replyOneFile @raf evt rsp))

                      File
                      (do
                        (var-set raf
                                 (RandomAccessFile. ^File data "r"))
                        (replyOneFile @raf evt rsp))

                      XData
                      (let [^XData xs data]
                        (var-set clen (.size xs))
                        (ChunkedStream. (.stream xs)))

                      ;;else
                      (if-not (nil? data)
                        (let [xs (XData. data)]
                          (var-set clen (.size xs))
                          (ChunkedStream. (.stream xs)))
                        nil)))
          (if (and (notnil? @payload)
                   (notnil? @raf))
            (var-set clen (.length ^RandomAccessFile @raf))))

        :else nil)

      (when (.isKeepAlive evt)
        (SetHeader rsp "Connection" "keep-alive"))

      (log/debug "writing out %s bytes back to client" @clen);
      (HttpHeaders/setContentLength rsp @clen)

      (.write ch rsp)
      (log/debug "wrote response headers out to client")

      (when (and (> @clen 0)
                 (notnil? @payload))
        (.write ch @payload)
        (log/debug "wrote response body out to client"))

      (let [wf (WriteLastContent ch true) ]
        (FutureCB wf #(when-not (nil? @raf)
                        (.close ^Closeable @raf)))
        (when-not (.isKeepAlive evt)
          (log/debug "keep-alive == false, closing channel, bye")
          (CloseCF wf))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeNettyTrigger

  "Create a Netty Async Trigger"

  ^czlab.skaro.io.core.AsyncWaitTrigger
  [^Channel ch evt src]

  (reify AsyncWaitTrigger

    (resumeWithResult [_ res]
      (if (instance? WebSockEvent evt)
        (try! (netty-ws-reply ch evt src) )
        (try! (nettyReply ch evt src) ) ))

    (resumeWithError [_]
      (let [rsp (MakeHttpReply 500) ]
        (try
          (maybeClose evt (.writeAndFlush ch rsp))
          (catch ClosedChannelException _
            (log/warn "closedChannelException thrown while flushing headers"))
          (catch Throwable t# (log/error t# "") )) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToJava ""

  [^Cookie c ^URLCodec cc]

  (doto (HttpCookie. (.getName c)
                     (.decode cc (str (.getValue c))))
    (.setComment (.getComment c))
    (.setDomain (.getDomain c))
    (.setMaxAge (.getMaxAge c))
    (.setPath (.getPath c))
    (.setVersion (.getVersion c))
    (.setHttpOnly (.isHttpOnly c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWEBSockEvent ""

  [^czlab.skaro.io.core.EmitAPI co
   ^Channel ch
   ssl
   ^WebSocketFrame msg]

  (let [textF (instance? TextWebSocketFrame msg)
        impl (MakeMMap)
        xdata (XData.)
        eeid (NextLong) ]
    (.resetContent xdata
                   (cond
                     textF
                     (.text ^TextWebSocketFrame msg)
                     (instance? BinaryWebSocketFrame msg)
                     (SlurpBytes (.content ^BinaryWebSocketFrame msg))
                     :else nil))
    (with-meta
      (reify
        Muble

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (toEDN [_] (.toEDN impl))
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        WebSockEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getSocket [_] ch)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (isSSL [_] ssl)
        (isBinary [this] (not textF))
        (isText [_] textF)
        (getData [_] xdata)
        (getResultObj [_] nil)
        (replyResult [this] nil)
        (emitter [_] co))

      { :typeid :czc.skaro.io/WebSockEvent })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackCookies ""

  [info]

  (let [v (str (GetInHeader info "Cookie"))
        cc (URLCodec. "utf-8")
        cks (if (hgl? v)
              (CookieDecoder/decode v)
              []) ]
    (with-local-vars [rc (transient {})]
      (doseq [^Cookie c (seq cks) ]
        (var-set rc (assoc! @rc
                            (.getName c)
                            (cookieToJava c cc))))
      (persistent! @rc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent2 ""

  ^HTTPEvent
  [^czlab.skaro.io.core.EmitAPI co
   ^Channel ch
   sslFlag
   ^XData xdata
   info wantSecure]

  (let [^InetSocketAddress laddr (.localAddress ch)
        ^HTTPResult res (MakeHttpResult co)
        cookieJar (crackCookies info)
        impl (MakeMMap)
        eeid (NextLong) ]
    (with-meta
      (reify

        Muble

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (toEDN [_] (.toEDN impl))
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        HTTPEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (emitter [_] co)
        (checkAuthenticity [_] wantSecure)

        (getCookie [_ nm] (get cookieJar nm))
        (getCookies [_] (vals cookieJar))

        (isKeepAlive [_] (true? (info "keepAlive")))

        (hasData [_] (notnil? xdata))
        (data [_] xdata)

        (contentType [_] (GetInHeader info "content-type"))
        (contentLength [_] (info "clen"))

        (encoding [this]  (GetCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaders [_] (vec (keys (:headers info))))
        (getHeaderValues [this nm]
          (if (.hasHeader this nm)
            (get (:headers info) (lcase nm))
            []))

        (getHeaderValue [_ nm] (GetInHeader info nm))
        (hasHeader [_ nm] (HasInHeader? info nm))

        (getParameterValues [this nm]
          (if (.hasParameter this nm)
            (get (:params info) nm)
            []))

        (getParameterValue [_ nm] (GetInParameter info nm))
        (getParameters [_] (vec (keys (:params info))))
        (hasParameter [_ nm] (HasInParam? info nm))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (:protocol info))
        (method [_] (:method info))

        (queryString [_] (:query info))
        (host [_] (:host info))

        (remotePort [_] (ConvLong (GetInHeader info "remote_port") 0))
        (remoteAddr [_] (str (GetInHeader info "remote_addr")))
        (remoteHost [_] "")

        (scheme [_] (if sslFlag "https" "http"))

        (serverPort [_] (ConvLong (GetInHeader info "server_port") 0))
        (serverName [_] (str (GetInHeader info "server_name")))

        (isSSL [_] sslFlag)

        (getUri [_] (:uri info))

        (getRequestURL [_] (ThrowIOE "not implemented"))

        (getResultObj [_] res)
        (replyResult [this]
          (let [^IOSession mvs (.getSession this)
                code (.getStatus res)
                ^czlab.skaro.io.core.WaitEventHolder
                wevt (.release co this) ]
            (cond
              (and (>= code 200)(< code 400)) (.handleResult mvs this res)
              :else nil)
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
      )

      { :typeid :czc.skaro.io/HTTPEvent })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent ""

  ^HTTPEvent
  [^czlab.skaro.io.core.EmitAPI co
   ^Channel ch
   sslFlag
   ^XData xdata
   info wantSecure]

  (doto (makeHttpEvent2 co ch sslFlag xdata info wantSecure)
    (.bindSession (MakeWSSession co sslFlag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/NettyIO

  [^czlab.skaro.io.core.EmitAPI co & args]

  (log/info "IOESReifyEvent: NettyIO: %s" (.id ^Identifiable co))
  (let [^Channel ch (nth args 0)
        ssl (notnil? (.get (.pipeline ch)
                           "ssl"))
        msg (nth args 1) ]
    (cond
      (instance? WebSocketFrame msg)
      (makeWEBSockEvent co ch ssl msg)
      :else
      (let [^czlab.xlib.net.routes.RouteInfo
            ri (if (> (count args) 2)
                 (nth args 2)
                 nil)]
        (makeHttpEvent co ch ssl
                       (:payload msg)
                       (:info msg)
                       (if (nil? ri) false (.isSecure? ri)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/NettyIO

  [^czlab.xlib.util.core.Muble co cfg0]

  (log/info "CompConfigure: NettyIO: %s" (.id ^Identifiable co))
  (let [cfg (merge (.getf co :dftOptions) cfg0)
        c2 (HttpBasicConfig co cfg) ]
    (.setf! co :emcfg c2)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher ""

  ^ChannelHandler
  [^czlab.skaro.io.core.EmitAPI co
   ^czlab.xlib.util.core.Muble src
   options]

  (log/debug "netty pipeline dispatcher, emitter = %s" (type co))
  (proxy [MessageFilter] []
    (channelRead0 [c msg]
      (let [ch (-> ^ChannelHandlerContext c (.channel))
            cfg (.getf src :emcfg)
            ts (:waitMillis cfg)
            evt (IOESReifyEvent co ch msg) ]
        (if (instance? HTTPEvent evt)
          (let [^czlab.skaro.io.core.WaitEventHolder
                w
                (-> (MakeNettyTrigger ch evt co)
                    (MakeAsyncWaitHolder  evt)) ]
            (.timeoutMillis w ts)
            (.hold co w)))
        (.dispatch co evt {})))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^czlab.xlib.util.core.Muble co]

  (let [^czlab.xlib.util.core.Muble
        ctr (.parent ^Hierarchial co)
        options (.getf co :emcfg)
        disp (msgDispatcher co co options)
        bs (InitTCPServer
             (ReifyPipeCfgtor
               (fn [p options]
                 (-> ^ChannelPipeline p
                     (.addBefore (ErrorSinkFilter/getName)
                                 "MsgDispatcher"
                                 disp))))
             options) ]
    (.setf! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/NettyIO

  [^czlab.xlib.util.core.Muble co]

  (log/info "IOESStart: NettyIO: %s" (.id ^Identifiable co))
  (let [cfg (.getf co :emcfg)
        host (str (:host cfg))
        port (:port cfg)
        nes (.getf co :netty)
        bs (:bootstrap nes)
        ch (StartServer bs host port) ]
    (.setf! co :netty (assoc nes :channel ch))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/NettyIO

  [^czlab.xlib.util.core.Muble co]

  (log/info "IOESStop NettyIO: %s" (.id ^Identifiable co))
  (let [{:keys [bootstrap channel]}
        (.getf co :netty) ]
    (StopServer  bootstrap channel)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.io/NettyIO

  [^czlab.xlib.util.core.Muble co]

  (log/info "compInitialize: NettyIO: %s" (.id ^Identifiable co))
  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

