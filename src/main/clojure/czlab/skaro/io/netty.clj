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
    [czlab.xlib.str :refer [lcase hgl? strim nichts?]]
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
        [czlab.skaro.core.sys]
        [czlab.skaro.io.core]
        [czlab.skaro.io.http])

  (:import
    [czlab.net RouteCracker RouteInfo]
    [czlab.skaro.server EventTrigger]
    [czlab.server
     EventEmitter
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
    [javax.net.ssl SSLContext]
    [java.nio.channels ClosedChannelException]
    [io.netty.handler.codec.http
     HttpResponseStatus
     HttpRequest
     HttpUtil
     HttpResponse
     CookieDecoder
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
    [czlab.skaro.mvc WebAsset HTTPRangeInput]
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
                        (urlEncode (str (.getValue c))))
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

  (closeCF cf (.isKeepAlive evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csToNetty

  ""
  [cookies]

  (persistent!
    (reduce
      #(conj! %1
              (ServerCookieEncoder/encode (javaToCookie %2)))
      (transient [])
      (seq cookies))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyWSReply

  ""
  [^ChannelHandlerContext ctx ^WebSockEvent evt src]

  (let [^WebSockResult res (.getResultObj evt)
        ^XData xs (.getData res)
        ^WebSocketFrame
        f (if
            (.isBinary res)
            (->> (.javaBytes xs)
                 (Unpooled/wrappedBuffer )
                 (BinaryWebSocketFrame. ))
            ;else
            (TextWebSocketFrame. (.stringify xs))) ]
    (.writeAndFlush ctx f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyOneFile

  ""
  [^RandomAccessFile raf
   ^HTTPEvent evt
   ^HttpResponse rsp]

  (let [ct (getHeader rsp "content-type")
        rv (.getHeaderValue evt "range")]
    (if-not (HTTPRangeInput/isAcceptable rv)
      (ChunkedFile. raf)
      (let [r (HTTPRangeInput. raf ct rv)
            n (.process r rsp)]
        (if (> n 0)
          r
          nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyReply

  ""
  [^ChannelHandlerContext ctx ^HTTPEvent evt src]

  ;;(log/debug "nettyReply called by event with uri: " (.getUri evt))
  (let [^Context res (.getResultObj evt)
        {:keys [redirect cookies code data hds]}
        (.impl (.getx res))
        rsp (httpReply<> code)]
    ;;(log/debug "about to reply " (.getStatus ^HTTPResult res))
    (with-local-vars
      [clen 0
       raf nil payload nil]
      (doseq [[nm vs]  hdrs
             :when (not= "content-length" (lcase nm))]
        (doseq [vv (seq vs)]
          (addHeader rsp nm vv)))
      (doseq [s (csToNetty cookies)]
        (addHeader rsp HttpHeaders$Names/SET_COOKIE s))
      (cond
        (and (>= code 300)
             (< code 400))
        (when-not (empty? redirect)
          (setHeader rsp "Location" redirect))

        (and (>= code 200)
             (< code 300)
             (not= "HEAD" (.method evt)))
        (do
          (var-set
            payload
            (condp instance? data
              WebAsset
              (let [^WebAsset ws data]
                (setHeader rsp "content-type" (.contentType ws))
                (var-set raf
                         (RandomAccessFile. (.getFile ws) "r"))
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
          (if (and (some? @payload)
                   (some? @raf))
            (var-set clen (.length ^RandomAccessFile @raf))))

        :else nil)

      (when (.isKeepAlive evt)
        (setHeader rsp "Connection" "keep-alive"))

      (log/debug "writing out %s bytes back to client" @clen)
      (HttpUtil/setContentLength rsp @clen)

      (.write ctx rsp)
      (log/debug "wrote response headers out to client")

      (when (and (> @clen 0)
                 (some? @payload))
        (.write ctx @payload)
        (log/debug "wrote response body out to client"))

      (let [wf (writeLastContent ctx true)]
        (futureCB wf #(when (some? @raf)
                        (.close ^Closeable @raf)))
        (when-not (.isKeepAlive evt)
          (log/debug "channel will be closed")
          (closeCF wf))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nettyTrigger

  "Create a Netty Async Trigger"
  ^EventTrigger
  [^ChannelHandlerContext ctx evt src]

  (reify EventTrigger

    (resumeWithResult [_ res]
      (if (inst? WebSockEvent evt)
        (try! (nettyWSReply ctx evt src))
        (try! (nettyReply ctx evt src) ) ))

    (resumeWithError [_]
      (let [rsp (httpReply<> 500)]
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
                     (urlDecode (str (.getValue c))))
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
  [^EventEmitter co ^Channel ch ssl msg]

  (let
    [text? (inst? TextWebSocketFrame msg)
     xdata
     (xdata<>
       (cond
         (inst? BinaryWebSocketFrame msg)
         (slurpBytes (.content
                       ^BinaryWebSocketFrame msg))
         text?
         (.text ^TextWebSocketFrame msg)
         :else nil))
     impl (muble<>)
     eeid (seqint2) ]
    (with-meta
      (reify

        Context

        (.getx [_] impl)

        Identifiable

        (id [_] eeid)

        WebSockEvent

        (bindSession [_ s] (.setv impl :ios s))
        (getSession [_] (.getv impl :ios))
        (getSocket [_] ch)
        (id [_] eeid)
        (checkAuthenticity [_] false)
        (isSSL [_] ssl)
        (isBinary [this] (not text?))
        (isText [_] text?)
        (getData [_] xdata)
        (getResultObj [_] nil)
        (replyResult [this] nil)
        (emitter [_] co))

      {:typeid ::WebSockEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackCookies

  ""
  [gist]

  (let
    [^String v (getInHeader gist "Cookie")
     cks (if (hgl? v)
           (CookieDecoder/decode v))]
    (persistent!
      (reduce
        #(assoc! %1
                 (.getName ^Cookie %2)
                 (cookieToJava c))
        (transient {})
        (seq cks)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent2

  ""
  ^HTTPEvent
  [^EventEmitter co ^Channel ch
   ssl? xdata gist wantSecure?]

  (let
    [^InetSocketAddress laddr (.localAddress ch)
     res (httpResult<> co)
     cookieJar (crackCookies info)
     impl (mubleObj!)
     eeid (seqint2)]
    (with-meta
      (reify

        Context
        (.getx [_] impl)

        Identifiable
        (id [_] eeid)

        HTTPEvent

        (bindSession [_ s] (.setv impl :ios s))
        (getSession [_] (.getv impl :ios))
        (id [_] eeid)
        (emitter [_] co)
        (checkAuthenticity [_] wantSecure?)

        (getCookie [_ nm] (get cookieJar nm))
        (getCookies [_] (vals cookieJar))

        (isKeepAlive [_] (true? (gist :keepAlive)))

        (hasData [_] (some? xdata))
        (data [_] xdata)

        (contentType [_] (getInHeader gist "content-type"))
        (contentLength [_] (gist :clen))

        (encoding [this]  (getCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaders [_] (keys (:headers gist)))
        (getHeaderValues [this nm]
          (if (.hasHeader this nm)
            (get (:headers gist) (lcase nm))
            []))

        (getHeaderValue [_ nm] (getInHeader gist nm))
        (hasHeader [_ nm] (hasInHeader? gist nm))

        (getParameterValues [this nm]
          (if (.hasParameter this nm)
            (get (:params gist) nm)
            []))

        (getParameterValue [_ nm] (getInParameter gist nm))
        (getParameters [_] (keys (:params gist)))
        (hasParameter [_ nm] (hasInParam? gist nm))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (:protocol gist))
        (method [_] (:method gist))

        (queryString [_] (:query gist))
        (host [_] (:host gist))

        (remotePort [_] (convLong (getInHeader gist "remote_port") 0))
        (remoteAddr [_] (str (getInHeader gist "remote_addr")))
        (remoteHost [_] "")

        (scheme [_] (if ssl? "https" "http"))

        (serverPort [_] (convLong (getInHeader gist "server_port") 0))
        (serverName [_] (str (getInHeader gist "server_name")))

        (isSSL [_] ssl?)

        (getUri [_] (:uri gist))

        (getRequestURL [_] (throwIOE "not implemented"))

        (getResultObj [_] res)
        (replyResult [this]
          (let [^IOSession mvs (.getSession this)
                code (.getStatus res)
                ^EventHolder
                wevt (.release co this) ]
            (when
              (and (>= code 200)
                   (< code 400))
              (.handleResult mvs this res))
            (when (some? wevt)
              (.resumeOnResult wevt res)))))

      {:typeid ::HTTPEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent

  ""
  ^HTTPEvent
  [^EventEmitter co
   ^Channel ch
   ssl?
   xdata
   gist wantSecure?]

  (doto (makeHttpEvent2 co
                        ch ssl? xdata gist wantSecure?)
    (.bindSession (wsession<> co ssl?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::Netty
  [^EventEmitter co & args]

  (log/info "ioevent: Netty: %s" (.id ^Identifiable co))
  (let [^Channel ch (first args)
        ssl? (-> (.pipeline ch)
                 (.get SslHandler)
                 (some?))
        msg (nth args 1)]
    (if
      (inst? WebSocketFrame msg)
      (makeWEBSockEvent co ch ssl? msg)
      ;else
      (let [^RouteInfo
            ri (if (> (count args) 2)
                 (nth args 2)
                 nil)]
        (makeHttpEvent co ch ssl?
                       (:data msg)
                       (:gist msg)
                       (if (nil? ri) false (.isSecure ri)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::Netty
  [^Context co cfg0]

  (log/info "comp->configure: Netty: %s" (.id ^Identifiable co))
  (->> (merge (.getv (.getx co) :dftOptions) cfg0)
       (httpBasicConfig co )
       (.setv (.getx co) :emcfg ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher

  ""
  ^ChannelHandler
  [^EventEmitter co options]

  (log/debug "netty pipeline dispatcher, emitter = %s" (type co))
  (proxy [InboundFilter] []
    (channelRead0 [c msg]
      (let [ch (-> ^ChannelHandlerContext c (.channel))
            {:keys [waitMillis]}
            (.getv (.getx ^Context co) :emcfg)
            evt (ioevent co ch msg)]
        (if (inst? HTTPEvent evt)
          (let [w
                (-> (nettyTrigger ch evt co)
                    (asyncWaitHolder evt)) ]
            (.timeoutMillis w waitMillis)
            (.hold co w)))
        (.dispatch co evt nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty

  ""
  [^Context co]

  (let [^Context ctr (.parent ^Hierarchial co)
        options (.getv (.getx co) :emcfg)
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
  [^Context co & args]

  (log/info "io->start: NettyIO: %s" (.id ^Identifiable co))
  (let [nes (.getv (.getx co) :netty)
        cfg (.getv (.getx co) :emcfg)
        bs (:bootstrap nes)
        ch (startServer bs cfg)]
    (.setv (.getx co) :netty (assoc nes :channel ch))
    (io->started co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::Netty
  [^Context co & args]

  (log/info "io->stop Netty: %s" (.id ^Identifiable co))
  (let [{:keys [bootstrap channel]}
        (.getv (.getx co) :netty) ]
    (stopServer bootstrap channel)
    (io->stopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Netty
  [^Context co]

  (log/info "comp->initialize: Netty: %s" (.id ^Identifiable co))
  (initNetty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


