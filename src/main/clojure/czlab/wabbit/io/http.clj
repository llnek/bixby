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
            [czlab.twisty.codec :refer [passwd<>]]
            [czlab.convoy.net.mime :refer [getCharset]]
            [czlab.xlib.io :refer [xdata<>]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.convoy.netty.routes]
        [czlab.convoy.netty.server]
        [czlab.convoy.netty.core]
        [czlab.wabbit.sys.core]
        [czlab.wabbit.io.core]
        [czlab.twisty.ssl]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.meta]
        [czlab.wabbit.io.web])

  (:import [czlab.convoy.netty MixedFullRequest InboundFilter]
           [java.nio.channels ClosedChannelException]
           [io.netty.handler.codec.http.websocketx
            TextWebSocketFrame
            WebSocketFrame
            BinaryWebSocketFrame]
           [io.netty.handler.codec.http.cookie
            ServerCookieDecoder
            ServerCookieEncoder]
           [io.netty.handler.codec DecoderException]
           [czlab.convoy.netty CPDecorator TcpPipeline]
           [czlab.wabbit.net WebAsset RangeInput]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.buffer ByteBuf Unpooled]
           [io.netty.handler.ssl SslHandler]
           [czlab.convoy.net RouteCracker RouteInfo]
           [czlab.wabbit.server Container]
           [czlab.wabbit.io IoService]
           [clojure.lang APersistentMap]
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
            HttpResult
            WebSockEvent
            WebSockResult]
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
       :passwd (->> (.server co)
                    (.appKey)
                    (passwd<> passwd) (.text))
       :serverKey (if ssl? (io/as-url kfile))}
      (merge cfg ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn h1Result<>
  "Create a HttpResult object"
  ^HttpResult
  [^IoService co]
  (let
    [impl (muble<> {:version (.text HttpVersion/HTTP_1_1)
                    :cookies []
                    :code -1
                    :headers {}})]
    (reify HttpResult

      (setRedirect [_ url] (.setv impl :redirect url))
      (setVersion [_ ver]  (.setv impl :version ver))
      (setStatus [_ code] (.setv impl :code code))
      (status [_] (.getv impl :code))
      (source [_] co)

      (addCookie [_ c]
        (if (some? c)
          (let [a (.getv impl :cookies)]
            (.setv impl :cookies (conj a c)))))

      (containsHeader [_ nm]
        (let [m (.getv impl :headers)
              a (get m (lcase nm))]
          (and (some? a)
               (> (count a) 0))))

      (removeHeader [_ nm]
        (let [m (.getv impl :headers)]
          (->> (dissoc m (lcase nm))
               (.setv impl :headers))))

      (clearHeaders [_]
        (.setv impl :headers {}))

      (addHeader [_ nm v]
        (let [m (.getv impl :headers)
              a (or (get m (lcase nm)) [])]
          (.setv impl
                 :headers
                 (assoc m (lcase nm) (conj a v)))))

      (setHeader [_ nm v]
        (let [m (.getv impl :headers)]
          (.setv impl
                 :headers
                 (assoc m (lcase nm) [v]))))

      (setChunked [_ b] (.setv impl :chunked? b))

      (isEmpty [_] (nil? (.getv impl :body)))

      (content [_] (.getv impl :body))

      (setContent [_ data]
          (.setv impl :body data)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLoadRoutes
  ^APersistentMap
  [^IoService co]
  (let [ctr (.server co)
        ctx (.getx co)
        f (io/file (.appDir ctr)
                   DN_CONF
                   "routes.conf")]
    (doto->>
      (if (.exists f)
        (loadRoutes f) [])
      (.setv ctx :routes ))))

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
(defn- resumeWithResult
  ""
  [ch evt]
  (if-some [e (cast? HttpEvent evt)]
    (replyResult ch (.resultObj e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resumeOnExpiry

  ""
  [^Channel ch ^HttpEvent evt]

  (try
    (->> (httpFullReply<> 500)
         (.writeAndFlush ch )
         (maybeClose evt ))
    (catch ClosedChannelException _
      (log/warn "closedChannelEx thrown"))
    (catch Throwable t# (log/exception t#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWEBSockEvent

  ""
  [^IoService co ^Channel ch ssl? msg]

  (let
    [text? (inst? TextWebSocketFrame msg)
     res (wsockResult<> co)
     eeid (seqint2)
     _body
     (cond
       (inst? BinaryWebSocketFrame msg)
       (toByteArray (.content
                     ^BinaryWebSocketFrame msg))
       text?
       (.text ^TextWebSocketFrame msg))]
    (with-meta
      (reify WebSockEvent

        (checkAuthenticity [_] false)
        (socket [_] ch)
        (id [_] eeid)
        (isSSL [_] ssl?)
        (isBinary [_] (not text?))
        (isText [_] text?)
        (body [_] _body)
        (resultObj [_] res)
        (replyResult [this]
          (nettyWSReply this))
        (source [_] co))

      {:typeid ::WebSockEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent

  ""
  ^HttpEvent
  [^IoService co ^Channel ch gist ^XData _body]

  (let
    [^InetSocketAddress laddr (.localAddress ch)
     ^RouteInfo
     ri (get-in gist [:route :routeInfo])
     wantSess? (boolean
                 (some-> ri (.wantSession)))
     wantSecure? (boolean
                   (some-> ri (.isSecure)))
     eeid (str "event#" (seqint2))
     cookieJar (:cookies gist)
     res (httpResult<> co)
     impl (muble<> {:stale false})]
    (with-meta
      (reify HttpEvent

        (checkAuthenticity [_] wantSecure?)
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
        (fire [this _]
          (when-some [t (.getv impl :trigger)]
            (.setv impl :stale true)
            (.unsetv impl :trigger)
            (cancelTimerTask t)
            (resumeOnExpiry ch this)))

        (scheme [_] (if (:ssl? gist) "https" "http"))
        (isStale [_] (.getv impl :stale))
        (isSSL [_] (:ssl? gist))
        (getx [_] impl)

        (resultObj [_] res)
        (replyResult [this]
          (let [t (.getv impl :trigger)
                mvs (.session this)
                code (.status res)]
            (some-> t (cancelTimerTask ))
            (.unsetv impl :trigger)
            (if (.isStale this)
              (throwIOE "Event has expired"))
            (if (some-> ri (.wantSession))
              (if (or (nil? mvs)
                      (nil? (.isNull mvs)))
                (throwIOE "Invalid/Null session")))
            (if (some? mvs)
              (downstream co gist mvs res))
            (resumeWithResult ch this))))
      {:typeid ::HTTPEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::HTTP
  [^IoService co {:keys [^Channel ch gist ^XData body]}]

  (logcomp "ioevent" co)
  (let [ssl? (maybeSSL? ch)]
    (comment
    (if
      (inst? WebSocketFrame msg)
      (makeWEBSockEvent co ch ssl? msg)))
    (makeHttpEvent co ch gist body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- h1Handler->onRead

  ""
  [^ChannelHandlerContext ctx
   ^IoService co ^FullHttpRequest msg]

  (if-not (decoderSuccess? msg)
    (throw (or (decoderError msg)
               (DecoderException. "Unknown decoder error"))))

  (let [gist (getAKey ctx MSGGIST_KEY)
        body
        (if-some [r (cast? MixedFullRequest msg)]
          (if (.isInMemory r) (.getBytes r) (.getFile r))
          (-> (.content msg) (toByteArray)))
        x (xdata<> body true)
        {:keys [waitMillis]}
        (.config co)
        ^HttpEvent
        evt (ioevent<> co {:ch (.channel ctx)
                           :gist gist
                           :body x})]
    (if (spos? waitMillis)
      (.hold co evt waitMillis))
    (.dispatch co evt )))

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
        (.impl (.getx co))]
    (stopServer bootstrap channel)
    (doto (.getx co)
      (.unsetv :bootstrap)
      (.unsetv :channel))
    (io<stopped> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init

  ::HTTP
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (let [ctr (.server co)
        cfg (->> (httpBasicConfig co cfg0)
                 (.setv (.getx co) :emcfg))
        h
        (proxy [InboundFilter] []
          (channelRead0 [ctx msg]
            (h1Handler->onRead ctx co msg)))]
    (->>
      (httpServer<>
        (reify CPDecorator
          (forH1 [_ ops] h)
          (forH2H1 [_ _])
          (forH2 [_ _]))
        cfg)
      (.setv (.getx co) :bootstrap ))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


