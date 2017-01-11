;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for HTTP/MVC service."
      :author "Kenneth Leung"}

  czlab.wabbit.io.http

  (:require [czlab.convoy.net.util :refer [parseBasicAuth]]
            [czlab.xlib.io :refer [xdata<> slurpUtf8]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.convoy.netty.discarder]
        [czlab.convoy.netty.server]
        [czlab.convoy.netty.routes]
        [czlab.convoy.netty.core]
        [czlab.convoy.net.core]
        [czlab.flux.wflow.core]
        [czlab.wabbit.etc.core]
        [czlab.wabbit.io.core]
        [czlab.twisty.ssl]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.meta])

  (:import [czlab.convoy.net HttpResult RouteCracker RouteInfo]
           [czlab.convoy.netty WholeRequest InboundHandler]
           [czlab.convoy.netty CPDecorator TcpPipeline]
           [java.nio.channels ClosedChannelException]
           [io.netty.handler.codec.http.websocketx
            TextWebSocketFrame
            WebSocketFrame
            BinaryWebSocketFrame]
           [io.netty.handler.codec.http.cookie
            ServerCookieDecoder
            ServerCookieEncoder]
           [io.netty.handler.codec DecoderException]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.buffer ByteBuf Unpooled]
           [io.netty.handler.ssl SslHandler]
           [czlab.flux.wflow Job]
           [czlab.wabbit.server Container]
           [czlab.wabbit.io IoService IoEvent]
           [clojure.lang Atom APersistentMap]
           [czlab.twisty IPassword]
           [java.util Timer TimerTask]
           [io.netty.handler.codec.http
            HttpResponseStatus
            HttpRequest
            HttpUtil
            HttpResponse
            DefaultHttpResponse
            FullHttpRequest
            HttpVersion
            HttpRequestDecoder
            HttpResponseEncoder
            DefaultCookie
            HttpHeaderValues
            HttpHeaderNames
            LastHttpContent
            HttpHeaders
            Cookie
            QueryStringDecoder]
           [java.io
            Closeable
            File
            IOException
            RandomAccessFile]
           [java.net
            HttpCookie
            URI
            URL
            InetAddress
            SocketAddress
            InetSocketAddress]
           [czlab.wabbit.io
            HttpEvent
            WSockEvent]
           [io.netty.channel
            Channel
            ChannelHandler
            ChannelFuture
            ChannelFutureListener
            ChannelPipeline
            ChannelHandlerContext
            SimpleChannelInboundHandler]
           [io.netty.handler.stream
            ChunkedStream
            ChunkedFile
            ChunkedInput
            ChunkedWriteHandler]
           [czlab.xlib
            XData
            Muble
            Hierarchial
            Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(derive ::HTTP :czlab.wabbit.io.core/Service)
(derive ::WebMVC ::HTTP)
(def ^:private ^String auth-token "authorization")
(def ^:private ^String basic-token "basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scanBasicAuth
  "Scan and parse if exists basic authentication"
  ^APersistentMap
  [^HttpEvent evt]
  (if-some+ [v (-> (.msgGist evt)
                   (gistHeader auth-token))]
    (parseBasicAuth v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLoadRoutes
  [cfg]
  (let [{:keys [routes]} cfg]
    (when-not (empty? routes)
      (loadRoutes routes))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- httpBasicConfig
  "Basic http config"
  [^IoService co cfg0]
  (let [{:keys [serverKey port passwd] :as cfg}
        (merge (.config co) cfg0)
        kfile (expandVars serverKey)
        ssl? (hgl? kfile)]
    (if ssl?
      (test-cond "server-key file url"
                 (.startsWith kfile "file:")))
    (->>
      {:port (if-not (spos? port)
               (if ssl? 443 80) port)
       :routes (maybeLoadRoutes cfg)
       :passwd (->> (.server co)
                    (.podKey)
                    (passwd<> passwd) (.text))
       :serverKey (if ssl? (io/as-url kfile))}
      (merge cfg ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaToCookie
  ""
  ^Cookie
  [^HttpCookie c]
  ;; stick with version 0, Java's HttpCookie defaults to 1 but that
  ;; screws up the Path attribute on the wire => it's quoted but
  ;; browser seems to not like it and mis-interpret it.
  ;; Netty's cookie defaults to 0, which is cool with me.
  (doto (DefaultCookie. (.getName c)
                        (.getValue c))
    ;;(.setComment (.getComment c))
    (.setDomain (.getDomain c))
    (.setMaxAge (.getMaxAge c))
    (.setPath (.getPath c))
    ;;(.setDiscard (.getDiscard c))
    (.setVersion 0)
    (.setHttpOnly (.isHttpOnly c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeClose
  ""
  [^HttpEvent evt ^ChannelFuture cf]
  (closeCF cf (:isKeepAlive? (.msgGist evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csToNetty
  ""
  [cookies]
  (preduce<vec>
    #(->> (.encode ServerCookieEncoder/STRICT ^Cookie %2)
          (conj! %1 ))
    (map #(javaToCookie %) (seq cookies))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- killTimerTask
  ""
  [^Muble m kee]
  (cancelTimerTask (.getv m kee)) (.unsetv m kee))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resumeOnExpiry
  ""
  [^Channel ch ^HttpEvent evt]
  (try
    (->> (httpResult<>
           (.socket evt)
           (.msgGist evt)
           HttpResponseStatus/INTERNAL_SERVER_ERROR)
         (.writeAndFlush ch )
         (maybeClose evt ))
    (catch ClosedChannelException _
      (log/warn "closedChannelEx thrown"))
    (catch Throwable t# (log/exception t#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockEvent<>
  ""
  [^IoService co ^Channel ch ssl? msg]
  (let
    [_body
     (-> (cond
           (inst? BinaryWebSocketFrame msg)
           (-> (.content
                 ^BinaryWebSocketFrame msg)
               (toByteArray))
           (inst? TextWebSocketFrame msg)
           (.text ^TextWebSocketFrame msg))
         (xdata<>))
     eeid (seqint2)]
    (with-meta
      (reify WSockEvent
        (isBinary [_] (instBytes? (.content _body)))
        (isText [_] (string? (.content _body)))
        (checkAuthenticity [_] false)
        (socket [_] ch)
        (id [_] eeid)
        (isSSL [_] ssl?)
        (body [_] _body)
        (source [_] co))
      {:typeid ::WSockEvent})))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- httpEvent<>
  ""
  ^HttpEvent
  [^IoService co ^Channel ch ssl? ^WholeRequest req]
  (let
    [^InetSocketAddress laddr (.localAddress ch)
     _body (.content req)
     gist (.msgGist req)
     rgist (:route gist)
     ^RouteInfo ri (:info rgist)
     wantSess? (some-> ri (.wantSession))
     wantSecure? (some-> ri (.isSecure))
     eeid (str "event#" (seqint2))
     cookieJar (:cookies gist)
     impl (muble<> {:stale false})]
    (with-meta
      (reify HttpEvent

        (checkAuthenticity [_] wantSecure?)
        (checkSession [_] wantSess?)
        (session [_] (.getv impl :session))
        (id [_] eeid)
        (source [_] co)
        (socket [_] ch)

        ;;:route {:redirect :status :info :groups :places }
        (routeGist [_] rgist)

        (cookie [_ n] (get cookieJar n))
        (cookies [_] (vals cookieJar))
        (msgGist [_] gist)
        (body [_] _body)

        (localAddr [_]
          (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (remotePort [_]
          (convLong (gistHeader gist "remote_port") 0))
        (remoteAddr [_]
          (str (gistHeader gist "remote_addr")))
        (remoteHost [_]
          (str (gistHeader gist "remote_host")))

        (serverPort [_]
          (convLong (gistHeader gist "server_port") 0))
        (serverName [_]
          (str (gistHeader gist "server_name")))

        (setTrigger [_ t] (.setv impl :trigger t))
        (cancel [_]
          (if-some [t (.getv impl :trigger)]
            (cancelTimerTask t))
          (.unsetv impl :trigger))

        (fire [this _]
          (when-some [t (.getv impl :trigger)]
            (.setv impl :stale true)
            (.unsetv impl :trigger)
            (cancelTimerTask t)
            (resumeOnExpiry ch this)))

        (scheme [_] (if (:ssl? gist) "https" "http"))
        (isStale [_] (.getv impl :stale))
        (isSSL [_] ssl?)
        (getx [_] impl))
      {:typeid ::HTTPEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>
  ::HTTP
  [^IoService co {:keys [^Channel ch msg]}]

  ;;(log/debug "ioevent: channel =>>>>>>>> %s" ch)
  (logcomp "ioevent" co)
  (let [ssl? (maybeSSL? ch)]
    (if
      (inst? WebSocketFrame msg)
      (wsockEvent<> co ch ssl? msg)
      (httpEvent<> co ch ssl? msg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- h1Handler->onRead
  ""
  [^ChannelHandlerContext ctx ^IoService co ^WholeRequest req]
  (let [{:keys [waitMillis]}
        (.config co)
        ^HttpEvent
        evt (ioevent<> co
                       {:msg req
                        :ch (.channel ctx)})]
    (if (spos? waitMillis)
      (.hold co evt waitMillis))
    (.dispatch co evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start
  ::HTTP
  [^IoService co]

  (logcomp "io->start" co)
  (let [bs (.getv (.getx co) :bootstrap)
        cfg (.config co)
        ch (startServer bs cfg)]
    (.setv (.getx co) :channel ch)
    (io<started> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop
  ::HTTP
  [^IoService co]

  (logcomp "io->stop" co)
  (let [{:keys [bootstrap channel]}
        (.intern (.getx co))]
    (stopServer channel)
    (doto (.getx co)
      (.unsetv :bootstrap)
      (.unsetv :channel))
    (io<stopped> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initor
  ""
  [^IoService co cfg0]
  (let [ctr (.server co)
        cfg (->> (httpBasicConfig co cfg0)
                 (.setv (.getx co) :emcfg))]
    (->>
      (httpServer<>
        (proxy [CPDecorator][]
          (forH1 [_]
            (ihandler<>
              #(h1Handler->onRead %1 co %2))))
        cfg)
      (.setv (.getx co) :bootstrap ))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::HTTP
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (initor co cfg0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::WebMVC
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (initor co cfg0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod processOrphan
  HttpEvent
  [_]
  ;; 500 or 503
  (workStream<>
    (script<>
      #(let [^Job job %2
             s (or (.getv job :statusCode)
                   500)
             ^HttpEvent evt (.event job)]
         (->> (httpResult<>
                (.socket evt)
                (.msgGist evt)
                (HttpResponseStatus/valueOf s))
              (replyResult (.socket evt)))
         nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cfgShutdownServer
  ""
  [^Atom gist func arg]
  (let [ch (-> (discardHTTPD<> func)
               (startServer arg))]
    #(stopServer ch)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


