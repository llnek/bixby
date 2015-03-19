;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.io.netty

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.net.routes :only [MakeRouteCracker RouteCracker]]
        [czlabclj.xlib.util.str :only [lcase hgl? nsb strim nichts?]]
        [czlabclj.xlib.util.core
         :only
         [Try! Stringify ThrowIOE MubleAPI
          MakeMMap notnil? ConvLong]]
        [czlabclj.xlib.netty.io]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.io.http]
        [czlabclj.tardis.io.triggers]
        [czlabclj.tardis.io.webss :only [MakeWSSession]]
        [czlabclj.xlib.util.seqnum :only [NextLong]]
        [czlabclj.xlib.util.mime :only [GetCharset]])

  (:import  [java.io Closeable File IOException RandomAccessFile]
            [java.net HttpCookie URI URL InetSocketAddress]
            [java.net SocketAddress InetAddress]
            [java.util ArrayList List HashMap Map]
            [com.google.gson JsonObject]
            [com.zotohlab.gallifrey.io Emitter HTTPEvent HTTPResult
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
            [com.zotohlab.gallifrey.mvc WebAsset HTTPRangeInput]
            [com.zotohlab.frwk.netty NettyFW
             DemuxedMsg
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
                        (.encode cc (nsb (.getValue c))))
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
             (notnil? cf))
    (.addListener cf ChannelFutureListener/CLOSE)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookiesToNetty ""

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

  [^WebSockResult res ^Channel ch
   ^WebSockEvent evt src]

  (let [^XData xs (.getData res)
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

  (let [ct (HttpHeaders/getHeader rsp "content-type")
        rv (.getHeaderValue evt "range") ]
    (if (cstr/blank? rv)
      (ChunkedFile. raf)
      (let [r (HTTPRangeInput. raf ct rv)
            n (.prepareNettyResponse r rsp) ]
        (if (> n 0)
          r
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- netty-reply ""

  [^czlabclj.xlib.util.core.MubleAPI res
   ^Channel ch ^HTTPEvent evt src]

  ;;(log/debug "netty-reply called by event with uri: " (.getUri evt))
  (let [cks (cookiesToNetty (.getf res :cookies))
        code (.getf res :code)
        rsp (NettyFW/makeHttpReply code)
        loc (nsb (.getf res :redirect))
        data (.getf res :data)
        hdrs (.getf res :hds) ]
    ;;(log/debug "about to reply " (.getStatus ^HTTPResult res))
    (with-local-vars [clen 0 raf nil payload nil]
      (doseq [[^String nm vs] (seq hdrs)]
        (when-not (= "content-length" (lcase nm))
          (doseq [^String vv (seq vs)]
            (HttpHeaders/addHeader rsp nm vv))))
      (doseq [s cks]
        (HttpHeaders/addHeader rsp
                               HttpHeaders$Names/SET_COOKIE s) )
      (when (and (>= code 300)
                 (< code 400))
        (when-not (cstr/blank? loc)
          (HttpHeaders/setHeader rsp "Location" loc)))
      (when (and (>= code 200)
                 (< code 300)
                 (not= "HEAD" (.method evt)))
        (var-set  payload
                  (condp instance? data
                    WebAsset
                    (let [^WebAsset ws data]
                      (HttpHeaders/setHeader rsp
                                             "content-type"
                                             (.contentType ws))
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
                    (let [^XData xs data ]
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

      (when (.isKeepAlive evt)
        (HttpHeaders/setHeader rsp "Connection" "keep-alive"))

      (log/debug "Writing out " @clen " bytes back to client");
      (HttpHeaders/setContentLength rsp @clen)

      (.write ch rsp)
      (log/debug "Wrote response headers out to client")

      (when (and (> @clen 0)
                 (notnil? @payload))
        (.write ch @payload)
        (log/debug "Wrote response body out to client."))

      (let [wf (WriteLastContent ch true) ]
        (FutureCB wf #(when (notnil? @raf)
                        (.close ^Closeable @raf)))
        (when-not (.isKeepAlive evt)
          (log/debug "Keep-alive == false, closing channel.  bye.")
          (CloseFuture wf))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeNettyTrigger ""

  ^czlabclj.tardis.io.core.AsyncWaitTrigger
  [^Channel ch evt src]

  (reify AsyncWaitTrigger

    (resumeWithResult [_ res]
      (if (instance? WebSockEvent evt)
        (Try! (netty-ws-reply res ch evt src) )
        (Try! (netty-reply res ch evt src) ) ))

    (resumeWithError [_]
      (let [rsp (NettyFW/makeHttpReply 500) ]
        (try
          (maybeClose evt (.writeAndFlush ch rsp))
          (catch ClosedChannelException e#
            (log/warn "ClosedChannelException thrown while flushing headers"))
          (catch Throwable t# (log/error t# "") )) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToJava ""

  [^Cookie c ^URLCodec cc]

  (doto (HttpCookie. (.getName c)
                     (.decode cc (nsb (.getValue c))))
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

  [^czlabclj.tardis.io.core.EmitterAPI co
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
                     (NettyFW/slurpByteBuf (.content ^BinaryWebSocketFrame msg))
                     :else nil))
    (with-meta
      (reify
        MubleAPI

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

      { :typeid :czc.tardis.io/WebSockEvent }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackCookies ""

  [info]

  (let [v (nsb (GetHeader info "Cookie"))
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
  [^czlabclj.tardis.io.core.EmitterAPI co
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

        MubleAPI

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

        (contentType [_] (GetHeader info "content-type"))
        (contentLength [_] (info "clen"))

        (encoding [this]  (GetCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaders [_] (vec (keys (:headers info))))
        (getHeaderValues [this nm]
          (if (.hasHeader this nm)
            (get (:headers info) (lcase nm))
            []))

        (getHeaderValue [_ nm] (GetHeader info nm))
        (hasHeader [_ nm] (HasHeader? info nm))

        (getParameterValues [this nm]
          (if (.hasParameter this nm)
            (get (:params info) nm)
            []))

        (getParameterValue [_ nm] (GetParameter info nm))
        (getParameters [_] (vec (keys (:params info))))
        (hasParameter [_ nm] (HasParam? info nm))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (:protocol info))
        (method [_] (:method info))

        (queryString [_] (:query info))
        (host [_] (:host info))

        (remotePort [_] (ConvLong (GetHeader info "remote_port") 0))
        (remoteAddr [_] (nsb (GetHeader info "remote_addr")))
        (remoteHost [_] "")

        (scheme [_] (if sslFlag "https" "http"))

        (serverPort [_] (ConvLong (GetHeader info "server_port") 0))
        (serverName [_] (nsb (GetHeader info "server_name")))

        (isSSL [_] sslFlag)

        (getUri [_] (:uri info))

        (getRequestURL [_] (throw (IOException. "not implemented")))

        (getResultObj [_] res)
        (replyResult [this]
          (let [^IOSession mvs (.getSession this)
                code (.getStatus res)
                ^czlabclj.tardis.io.core.WaitEventHolder
                wevt (.release co this) ]
            (cond
              (and (>= code 200)(< code 400)) (.handleResult mvs this res)
              :else nil)
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
      )

      { :typeid :czc.tardis.io/HTTPEvent }

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent ""

  ^HTTPEvent
  [^czlabclj.tardis.io.core.EmitterAPI co
   ^Channel ch
   sslFlag
   ^XData xdata
   info wantSecure]

  (doto (makeHttpEvent2 co ch sslFlag xdata info wantSecure)
    (.bindSession (MakeWSSession co sslFlag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/NettyIO

  [^czlabclj.tardis.io.core.EmitterAPI co & args]

  (let [^Channel ch (nth args 0)
        ssl (notnil? (.get (.pipeline ch)
                           "ssl"))
        msg (nth args 1) ]
    (cond
      (instance? WebSocketFrame msg)
      (makeWEBSockEvent co ch ssl msg)
      :else
      (let [^czlabclj.xlib.net.routes.RouteInfo
            ri (if (> (count args) 2)
                 (nth args 2)
                 nil)
            ^DemuxedMsg req msg ]
        (makeHttpEvent co ch ssl
                       (.payload req)
                       (.info req)
                       (if (nil? ri) false (.isSecure? ri)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/NettyIO

  [^czlabclj.tardis.core.sys.Element co cfg0]

  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        c2 (HttpBasicConfig co cfg) ]
    (.setAttr! co :emcfg c2)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher ""

  ^ChannelHandler
  [^czlabclj.tardis.io.core.EmitterAPI co
   ^czlabclj.tardis.core.sys.Element src
   options]

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [ctx msg]
      (let [ch (.channel ^ChannelHandlerContext ctx)
            cfg (.getAttr src :emcfg)
            ts (:waitMillis cfg)
            evt (IOESReifyEvent co ch msg) ]
        (if (instance? HTTPEvent evt)
          (let [^czlabclj.tardis.io.core.WaitEventHolder
                w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
            (.timeoutMillis w ts)
            (.hold co w)))
        (.dispatch co evt {})))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyInitor ""

  ^PipelineConfigurator
  [^czlabclj.tardis.core.sys.Element co]

  (log/debug "tardis netty pipeline initor called with emitter = " (type co))
  (ReifyHTTPPipe "NettyDispatcher" #(msgDispatcher co co %)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^czlabclj.tardis.core.sys.Element co]

  (let [^czlabclj.tardis.core.sys.Element
        ctr (.parent ^Hierarchial co)
        options (.getAttr co :emcfg)
        bs (InitTCPServer (nettyInitor co) options) ]
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/NettyIO

  [^czlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        host (nsb (:host cfg))
        port (:port cfg)
        nes (.getAttr co :netty)
        ^ServerBootstrap bs (:bootstrap nes)
        ch (StartServer bs host port) ]
    (.setAttr! co :netty (assoc nes :channel ch))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/NettyIO

  [^czlabclj.tardis.core.sys.Element co]

  (let [nes (.getAttr co :netty)
        ^ServerBootstrap bs (:bootstrap nes)
        ^Channel ch (:channel nes) ]
    (StopServer  bs ch)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/NettyIO

  [^czlabclj.tardis.core.sys.Element co]

  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private netty-eof nil)

