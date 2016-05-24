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
      :author "kenl" }

  czlab.skaro.io.netty

  (:require
    [czlab.xlib.str :refer [lcase hgl? strim nichts?]]
    [czlab.skaro.io.webss :refer [mkWSSession]]
    [czlab.xlib.mime :refer [getCharset]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [czlab.xlib.core
     :refer [try!
             stringify
             throwIOE
             nextLong
             mubleObj!
             convLong]])

  (:use [czlab.netty.filters]
        [czlab.net.routes]
        [czlab.netty.io]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.core]
        [czlab.skaro.io.http])

  (:import
    [czlab.net RouteCracker RouteInfo]
    [czlab.wflow.server Emitter
     EventTrigger
     EventHolder]
    [java.io Closeable
     File
     IOException
     RandomAccessFile]
    [java.net HttpCookie
     URI
     URL
     InetSocketAddress
     SocketAddress InetAddress]
    [czlab.skaro.io HTTPEvent
     HTTPResult
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
    [czlab.skaro.mvc WebAsset HTTPRangeInput]
    [czlab.netty MessageFilter
     ErrorSinkFilter PipelineConfigurator]
    [io.netty.handler.codec.http.websocketx
     WebSocketFrame
     BinaryWebSocketFrame
     TextWebSocketFrame]
    [io.netty.buffer ByteBuf Unpooled]
    [czlab.xlib XData
     Muble Hierarchial Identifiable]))

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
    (.setHttpOnly (.isHttpOnly c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeClose ""

  [^HTTPEvent evt ^ChannelFuture cf]

  (when (and (not (.isKeepAlive evt))
             (some? cf))
    (.addListener cf ChannelFutureListener/CLOSE)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csToNetty ""

  [cookies]

  (let [cc (URLCodec. "utf-8") ]
    (persistent!
      (reduce
        #(conj! %1
                (ServerCookieEncoder/encode (javaToCookie %2 cc)))
        (transient [])
        (seq cookies)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyWSReply ""

  [^Channel ch ^WebSockEvent evt src]

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
    (.writeAndFlush ch f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyOneFile ""

  [^RandomAccessFile raf
   ^HTTPEvent evt
   ^HttpResponse rsp ]

  (let [ct (getHeader rsp "content-type")
        rv (.getHeaderValue evt "range") ]
    (if-not (HTTPRangeInput/isAcceptable rv)
      (ChunkedFile. raf)
      (let [r (HTTPRangeInput. raf ct rv)
            n (.process r rsp) ]
        (if (> n 0)
          r
          nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyReply ""

  [^Channel ch ^HTTPEvent evt src]

  ;;(log/debug "nettyReply called by event with uri: " (.getUri evt))
  (let [^Muble res (.getResultObj evt)
        loc (.getv res :redirect)
        cks (.getv res :cookies)
        code (.getv res :code)
        data (.getv res :data)
        hdrs (.getv res :hds)
        rsp (httpReply code) ]

    ;;(log/debug "about to reply " (.getStatus ^HTTPResult res))

    (with-local-vars
      [clen 0
       raf nil payload nil]
      (doseq [[nm vs]  hdrs
             :when (not= "content-length" (lcase nm)) ]
        (doseq [vv (seq vs)]
          (addHeader rsp nm vv)))
      (doseq [s (csToNetty cks)]
        (addHeader rsp HttpHeaders$Names/SET_COOKIE s))
      (cond
        (and (>= code 300)
             (< code 400))
        (when-not (empty? loc)
          (setHeader rsp "Location" loc))

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
      (HttpHeaders/setContentLength rsp @clen)

      (.write ch rsp)
      (log/debug "wrote response headers out to client")

      (when (and (> @clen 0)
                 (some? @payload))
        (.write ch @payload)
        (log/debug "wrote response body out to client"))

      (let [wf (writeLastContent ch true) ]
        (futureCB wf #(when (some? @raf)
                        (.close ^Closeable @raf)))
        (when-not (.isKeepAlive evt)
          (log/debug "keep-alive == false, closing channel, bye")
          (closeCF wf))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nettyTrigger

  "Create a Netty Async Trigger"

  ^EventTrigger
  [^Channel ch evt src]

  (reify EventTrigger

    (resumeWithResult [_ res]
      (if (instance? WebSockEvent evt)
        (try! (nettyWSReply ch evt src) )
        (try! (nettyReply ch evt src) ) ))

    (resumeWithError [_]
      (let [rsp (HttpReply* 500) ]
        (try
          (maybeClose evt (.writeAndFlush ch rsp))
          (catch ClosedChannelException _
            (log/warn "closedChannelException thrown while flushing headers"))
          (catch Throwable t# (log/error t# "") )) ))))

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
    (.setHttpOnly (.isHttpOnly c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWEBSockEvent ""

  [^Emitter co
   ^Channel ch
   ssl
   ^WebSocketFrame msg]

  (let [textF (instance? TextWebSocketFrame msg)
        impl (mubleObj!)
        xdata (XData.)
        eeid (nextLong) ]
    (.resetContent xdata
                   (cond
                     textF
                     (.text ^TextWebSocketFrame msg)
                     (instance? BinaryWebSocketFrame msg)
                     (slurpBytes (.content ^BinaryWebSocketFrame msg))
                     :else nil))
    (with-meta
      (reify

        Muble

        (setv [_ k v] (.setv impl k v) )
        (seq [_] (.seq impl))
        (getv [_ k] (.getv impl k) )
        (toEDN [_] (.toEDN impl))
        (unsetv [_ k] (.unsetv impl k) )
        (clear [_] (.clear impl))

        Identifiable

        (id [_] eeid)

        WebSockEvent

        (bindSession [_ s] (.setv impl :ios s))
        (getSession [_] (.getv impl :ios))
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

      {:typeid :czc.skaro.io/WebSockEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackCookies ""

  [info]

  (let [v (getInHeader info "Cookie")
        cc (URLCodec. "utf-8")
        cks (if (hgl? v)
              (CookieDecoder/decode ^String v)
              []) ]
    (with-local-vars [rc (transient {})]
      (doseq [^Cookie c  cks]
        (var-set rc (assoc! @rc
                            (.getName c)
                            (cookieToJava c cc))))
      (persistent! @rc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent2 ""

  ^HTTPEvent
  [^Emitter co
   ^Channel ch
   sslFlag
   ^XData xdata
   info wantSecure]

  (let [^InetSocketAddress laddr (.localAddress ch)
        ^HTTPResult res (httpResult co)
        cookieJar (crackCookies info)
        impl (mubleObj!)
        eeid (nextLong) ]
    (with-meta
      (reify

        Muble

        (setv [_ k v] (.setv impl k v) )
        (seq [_] (.seq impl))
        (getv [_ k] (.getv impl k) )
        (unsetv [_ k] (.unsetv impl k) )
        (toEDN [_] (.toEDN impl))
        (clear [_] (.clear impl))

        Identifiable

        (id [_] eeid)

        HTTPEvent

        (bindSession [_ s] (.setv impl :ios s))
        (getSession [_] (.getv impl :ios))
        (getId [_] eeid)
        (emitter [_] co)
        (checkAuthenticity [_] wantSecure)

        (getCookie [_ nm] (get cookieJar nm))
        (getCookies [_] (vals cookieJar))

        (isKeepAlive [_] (true? (info "keepAlive")))

        (hasData [_] (some? xdata))
        (data [_] xdata)

        (contentType [_] (getInHeader info "content-type"))
        (contentLength [_] (info "clen"))

        (encoding [this]  (getCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaders [_] (vec (keys (:headers info))))
        (getHeaderValues [this nm]
          (if (.hasHeader this nm)
            (get (:headers info) (lcase nm))
            []))

        (getHeaderValue [_ nm] (getInHeader info nm))
        (hasHeader [_ nm] (hasInHeader? info nm))

        (getParameterValues [this nm]
          (if (.hasParameter this nm)
            (get (:params info) nm)
            []))

        (getParameterValue [_ nm] (getInParameter info nm))
        (getParameters [_] (vec (keys (:params info))))
        (hasParameter [_ nm] (hasInParam? info nm))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (:protocol info))
        (method [_] (:method info))

        (queryString [_] (:query info))
        (host [_] (:host info))

        (remotePort [_] (convLong (getInHeader info "remote_port") 0))
        (remoteAddr [_] (str (getInHeader info "remote_addr")))
        (remoteHost [_] "")

        (scheme [_] (if sslFlag "https" "http"))

        (serverPort [_] (convLong (getInHeader info "server_port") 0))
        (serverName [_] (str (getInHeader info "server_name")))

        (isSSL [_] sslFlag)

        (getUri [_] (:uri info))

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

      {:typeid :czc.skaro.io/HTTPEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent ""

  ^HTTPEvent
  [^Emitter co
   ^Channel ch
   sslFlag
   ^XData xdata
   info wantSecure]

  (doto (makeHttpEvent2 co
                        ch sslFlag xdata info wantSecure)
    (.bindSession (mkWSSession co sslFlag))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioReifyEvent :czc.skaro.io/NettyIO

  [^Emitter co & args]

  (log/info "ioReifyEvent: NettyIO: %s" (.id ^Identifiable co))
  (let [^Channel ch (nth args 0)
        ssl (-> (.pipeline ch)
                (.get "ssl")
                (some?))
        msg (nth args 1) ]
    (if
      (instance? WebSocketFrame msg)
      (makeWEBSockEvent co ch ssl msg)
      ;else
      (let [^RouteInfo
            ri (if (> (count args) 2)
                 (nth args 2)
                 nil)]
        (makeHttpEvent co ch ssl
                       (:payload msg)
                       (:info msg)
                       (if (nil? ri) false (.isSecure ri)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/NettyIO

  [^Muble co cfg0]

  (log/info "CompConfigure: NettyIO: %s" (.id ^Identifiable co))
  (->> (merge (.getv co :dftOptions) cfg0)
       (HttpBasicConfig co )
       (.setv co :emcfg ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher ""

  ^ChannelHandler
  [^Emitter co
   ^Muble src
   options]

  (log/debug "netty pipeline dispatcher, emitter = %s" (type co))
  (proxy [MessageFilter] []
    (channelRead0 [c msg]
      (let [ch (-> ^ChannelHandlerContext c (.channel))
            {:keys [waitMillis]}
            (.getv src :emcfg)
            evt (ioReifyEvent co ch msg) ]
        (if (instance? HTTPEvent evt)
          (let [w
                (-> (nettyTrigger ch evt co)
                    (asyncWaitHolder evt)) ]
            (.timeoutMillis w waitMillis)
            (.hold co w)))
        (.dispatch co evt {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initNetty ""

  [^Muble co]

  (let [^Muble ctr (.parent ^Hierarchial co)
        options (.getv co :emcfg)
        disp (msgDispatcher co co options)
        bs (initTCPServer
             (reifyPipeCfgtor
               (fn [p _]
                 (-> ^ChannelPipeline p
                     (.addBefore ErrorSinkFilter/NAME
                                 "MsgDispatcher"
                                 disp))))
             options) ]
    (.setv co :netty  { :bootstrap bs })
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStart :czc.skaro.io/NettyIO

  [^Muble co & args]

  (log/info "ioStart: NettyIO: %s" (.id ^Identifiable co))
  (let [{:keys [host port]}
        (.getv co :emcfg)
        nes (.getv co :netty)
        bs (:bootstrap nes)
        ch (startServer bs host port) ]
    (.setv co :netty (assoc nes :channel ch))
    (ioStarted co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStop :czc.skaro.io/NettyIO

  [^Muble co & args]

  (log/info "ioStop NettyIO: %s" (.id ^Identifiable co))
  (let [{:keys [bootstrap channel]}
        (.getv co :netty) ]
    (stopServer  bootstrap channel)
    (ioStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.io/NettyIO

  [^Muble co]

  (log/info "compInitialize: NettyIO: %s" (.id ^Identifiable co))
  (initNetty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


