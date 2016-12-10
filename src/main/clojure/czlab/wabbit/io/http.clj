;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

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
(def ^:private ^String AUTH "authorization")
(def ^:private ^String BASIC "basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scanBasicAuth
  "Scan and parse if exists basic authentication"
  ^APersistentMap
  [^HttpEvent evt]
  (if-some+ [v (-> (.msgGist evt)
                   (gistHeader AUTH))]
    (parseBasicAuth v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLoadRoutes
  [^IoService co]
  (let [{:keys [routes]}
        (.config co)]
    (when-not (empty? routes)
      (->> (loadRoutes routes)
           (.setv (.getx co) :routes )))))

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
       :routes (maybeLoadRoutes co)
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
           ch
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
     ^RouteInfo ri (get-in gist [:route :info])
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
             ^IoEvent evt (.event job)]
         (->> (httpResult<>
                (.socket evt)
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


