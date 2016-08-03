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


(ns ^{:doc ""
      :author "Kenneth Leung" }

  czlab.skaro.io.netty

  (:require
    [czlab.xlib.str
     :refer [urlDecode
             urlEncode
             lcase
             hgl?
             strim
             nichts?]]
    [czlab.skaro.io.webss :refer [wsession<>]]
    [czlab.xlib.mime :refer [getCharset]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [czlab.xlib.core
     :refer [stringify
             try!
             throwIOE
             seqint2
             muble<>
             convLong]])

  (:use [czlab.netty.filters]
        [czlab.net.routes]
        [czlab.netty.io]
        [czlab.skaro.sys.core]
        [czlab.skaro.io.core]
        [czlab.skaro.io.http])

  (:import
    [czlab.net RouteCracker RouteInfo]
    [czlab.skaro.server EventTrigger]
    [czlab.server
     Emitter
     EventHolder]
    [java.io
     Closeable
     File
     IOException
     RandomAccessFile]
    [java.net
     HttpCookie
     URI
     URL
     InetSocketAddress
     InetAddress
     SocketAddress]
    [czlab.skaro.io
     HTTPEvent
     HTTPResult
     IOSession
     WebSockEvent
     WebSockResult]
    [java.nio.channels ClosedChannelException]
    [io.netty.handler.codec.http
     HttpResponseStatus
     HttpRequest
     HttpUtil
     HttpResponse
     ServerCookieDecoder
     ServerCookieEncoder
     DefaultHttpResponse
     HttpVersion
     HttpRequestDecoder
     HttpResponseEncoder
     DefaultCookie
     HttpHeaders$Names
     LastHttpContent
     HttpHeaders
     Cookie
     QueryStringDecoder]
    [io.netty.bootstrap ServerBootstrap]
    [io.netty.channel
     Channel
     ChannelHandler
     ChannelFuture
     ChannelFutureListener
     ChannelPipeline
     ChannelHandlerContext
     SimpleChannelInboundHandler]
    [io.netty.handler.stream
     ChunkedFile
     ChunkedStream
     ChunkedWriteHandler]
    [czlab.skaro.mvc WebAsset HttpRangeInput]
    [czlab.netty
     PipelineConfigurator]
    [io.netty.handler.codec.http.websocketx
     WebSocketFrame
     BinaryWebSocketFrame
     TextWebSocketFrame]
    [io.netty.buffer ByteBuf Unpooled]
    [czlab.xlib
     XData
     Muble
     Hierarchial
     Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

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
  [^HTTPEvent evt ^ChannelFuture cf]

  (->> (:keepAlive? (.msgGist))
       (closeCF cf )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csToNetty

  ""
  [cookies]

  (-> (map #(javaToCookie %)
           (seq cookies))
      (.encode ServerCookieEncoder/STRICT )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyWSReply

  ""
  [^ChannelHandlerContext ctx ^WebSockEvent evt src]

  (let [^WebSockResult res (.resultObj evt)
        ^XData xs (.getv (.getx res) :body)
        ^WebSocketFrame
        f (when (and (some? xs)
                     (.hasContent xs))
            (if (.isBinary res)
              (->> (.javaBytes xs)
                   (Unpooled/wrappedBuffer )
                   (BinaryWebSocketFrame. ))
              (TextWebSocketFrame. (.stringify xs))))]
    (when (some? f)
      (.writeAndFlush ctx f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyOneFile

  ""
  ^ChunkedInput
  [^RandomAccessFile raf
   ^HTTPEvent evt
   ^HttpResponse rsp]

  (-> (getInHeader (.msgGist evt) "range")
      (HttpRangeInput/fileRange rsp raf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyReplyMore

  ""
  [^ChannelHandleContext ctx
   ^HttpResponse rsp
   ^HTTPEvent evt
   & [body]]

  (let
    [clen
     (cond instance? body
       ChunkedInput (.length ^ChunkedInput body)
       XData (.size ^XData body)
       (if (nil? body)
         0
         (throwBadArg "rogue payload type %s"
                    (class body))))
     gist (.msgGist evt)]
    (log/debug "writing out %d bytes to client" clen)
    (when (:keepAlive? gist)
      (setHeader rsp "Connection" "keep-alive"))
    (if-not (hgl? (getHeader rsp "content-length"))
      (HttpUtil/setContentLength rsp clen))
    (.write ctx rsp)
    (log/debug "wrote rsp-headers out to client")
    (when (and (spos? clen)
               (some? body))
      (->>
        (if (inst? XData body)
          (->> (.stream  ^XData body)
               (ChunkedStream.))
          body)
        (.write ctx ))
      (log/debug "wrote rsp-body out to client"))
    (-> (writeLastContent ctx true)
        (closeCF (:keepAlive? gist)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyReply

  ""
  [^ChannelHandlerContext ctx ^HTTPEvent evt]

  (let [res (.resultObj evt)
        {:keys [redirect
                cookies
                code body hds]}
        (.impl (.getx res))
        gist (.msgGist evt)
        rsp (httpReply<> code)]
    ;;headers
    (doseq [[nm vs]  hdrs
           :when (not= "content-length" (lcase nm))]
      (doseq [vv (seq vs)]
        (addHeader rsp nm vv)))
    ;;cookies
    (doseq [s (csToNetty cookies)]
      (addHeader rsp
                 HttpHeaders$Names/SET_COOKIE s))
    (cond
      ;;redirect?
      (and (>= code 300)
           (< code 400))
      (do
        (when (hgl? redirect)
          (setHeader rsp "Location" redirect))
        (nettyReplyMore ctx rsp evt))
      ;;ok?
      (and (>= code 200)
           (< code 300))
      (->>
        (when-not (= "HEAD" (:method gist))
          (condp instance? body
            WebAsset
            (let [^WebAsset ws data
                  raf (RandomAccessFile. (.file ws) "r")]
              (setHeader rsp "content-type" (.contentType ws))
              (replyOneFile raf evt rsp))
            File
            (let [raf (RandomAccessFile. ^File body "r")]
              (replyOneFile raf evt rsp))
            ;;else
            (xdata<> body)))
        (nettyReplyMore ctx rsp evt ))

      :else
      (nettyReplyMore ctx rsp evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nettyTrigger

  "Create a Netty Async Trigger"
  ^EventTrigger
  [^ChannelHandlerContext ctx evt]

  (reify EventTrigger

    (resumeWithResult [_ res]
      (if (inst? WebSockEvent evt)
        (try! (nettyWSReply ctx evt))
        (try! (nettyReply ctx evt))))

    (resumeWithError [_]
      (let [rsp (httpFullReply<> 500)]
        (try
          (maybeClose evt (.writeAndFlush ctx rsp))
          (catch ClosedChannelException _
            (log/warn "closedChannelEx thrown"))
          (catch Throwable t# (log/error t# "") )) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToJava

  ""
  [^Cookie c]

  (doto (HttpCookie. (.getName c)
                     (.getValue c))
    (.setComment (.getComment c))
    (.setDomain (.getDomain c))
    (.setMaxAge (.getMaxAge c))
    (.setPath (.getPath c))
    (.setVersion (.getVersion c))
    (.setHttpOnly (.isHttpOnly c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWEBSockEvent

  ""
  [^Service co ^Channel ch ssl? msg]

  (let
    [text? (inst? TextWebSocketFrame msg)
     body
     (xdata<>
       (cond
         (inst? BinaryWebSocketFrame msg)
         (slurpBytes (.content
                       ^BinaryWebSocketFrame msg))
         text?
         (.text ^TextWebSocketFrame msg)
         :else nil))
     eeid (seqint2) ]
    (with-meta
      (reify WebSockEvent

        (bindSession [_ s] (.setv impl :ios s))
        (session [_] (.getv impl :ios))
        (socket [_] ch)
        (id [_] eeid)
        (checkAuthenticity [_] false)
        (isSSL [_] ssl?)
        (isBinary [this] (not text?))
        (isText [_] text?)
        (body [_] _body)
        (resultObj [_] nil)
        (replyResult [this] nil)
        (emitter [_] co))

      {:typeid ::WebSockEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackCookies

  ""
  ^APersistentMap
  [gist]

  (let
    [^String v (getInHeader gist "Cookie")
     cks (if (hgl? v)
           (.decode ServerCookieDecoder/STRICT v))]
    (persistent!
      (reduce
        #(assoc! %1
                 (.getName ^Cookie %2)
                 (cookieToJava c))
        (transient {})
        (seq cks)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent

  ""
  ^HTTPEvent
  [^Service co ^Channel ch
   ssl? _body gist wantSecure?]

  (let
    [^InetSocketAddress laddr (.localAddress ch)
     cookieJar (crackCookies gist)
     res (httpResult<> co)
     impl (muble<>)
     eeid (seqint2)
     evt
     (with-meta
       (reify HTTPEvent

         (bindSession [_ s] (.setv impl :ios s))
         (session [_] (.getv impl :ios))
         (id [_] eeid)
         (emitter [_] co)
         (checkAuthenticity [_] wantSecure?)

         (cookies [_] cookieJar)
         (msgGist [_] gist)
         (body [_] _body)

         (localAddr [_] (.getHostAddress (.getAddress laddr)))
         (localHost [_] (.getHostName laddr))
         (localPort [_] (.getPort laddr))

         (remotePort [_]
           (convLong (getInHeader gist "remote_port") 0))
         (remoteAddr [_]
           (str (getInHeader gist "remote_addr")))
         (remoteHost [_] "")

         (serverPort [_]
           (convLong (getInHeader gist "server_port") 0))
         (serverName [_]
           (str (getInHeader gist "server_name")))
         (scheme [_] (if ssl? "https" "http"))
         (isSSL [_] ssl?)

         (resultObj [_] res)
         (replyResult [this]
           (let [^IOSession mvs (.session this)
                 code (.status res)
                 ^EventHolder
                 wevt (.release co this)]
             (when
               (and (>= code 200)
                    (< code 400))
               (.handleResult mvs this res))
             (when (some? wevt)
               (.resumeOnResult wevt res)))))
       {:typeid ::HTTPEvent })]
    (doto evt
      (.bindSession (wsession<> co ssl?)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::Netty
  [^Service co & [^Channel ch msg ^RouteInfo ri]]

  (log/info "ioevent: Netty: %s" (.id co))
  (let [ssl? (-> (.pipeline ch)
                 (.get SslHandler)
                 (some?))]
    (if
      (inst? WebSocketFrame msg)
      (makeWEBSockEvent co ch ssl? msg)
      ;else
      (makeHttpEvent
        co ch ssl?
        (:body msg)
        (:gist msg)
        (if (nil? ri) false (.isSecure ri))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher

  ""
  ^ChannelHandler
  [^Service co options]

  (log/debug "dispatcher for service = %s" (.id co))
  (proxy [InboundFilter] []
    (channelRead0 [c msg]
      (let [ch (-> ^ChannelHandlerContext c (.channel))
            {:keys [waitMillis]}
            (.config co)
            evt (ioevent co ch msg)]
        (if (inst? HTTPEvent evt)
          (let [w
                (-> (nettyTrigger ch evt)
                    (asyncWaitHolder evt))]
            (.timeoutMillis w waitMillis)
            (.hold co w)))
        (.dispatch co evt )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty

  ""
  ^Service
  [^Service co]

  (let [^Container ctr (.server co)
        options (.config co)
        bs (httpServer<>
             (reify CPDecorator
               (newHttp1Handler [_ ops] (msgDispatcher co ops))
               (newHttp2Handler [_ _])
               (newHttp2Reqr [_ _]))
             options)]
    (.setv (.getx co) :netty  { :bootstrap bs })
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::Netty
  [^Service co & args]

  (log/info "io->start: Netty: %s" (.id co))
  (let [nes (.getv (.getx co) :netty)
        cfg (.config co)
        bs (:bootstrap nes)
        ch (startServer bs cfg)]
    (.setv (.getx co) :netty (assoc nes :channel ch))
    (io->started co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::Netty
  [^Service co & args]

  (log/info "io->stop Netty: %s" (.id co))
  (let [{:keys [bootstrap channel]}
        (.getv (.getx co) :netty) ]
    (stopServer bootstrap channel)
    (io->stopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Netty
  [^Service co & [cfg0]]

  (log/info "comp->initialize: Netty: %s" (.id co))
  (->> (httpBasicConfig co cfg0)
       (.setv (.getx co) :emcfg ))
  (initNetty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


