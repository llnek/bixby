;;
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
;;
;; This library is distributed in the hope that it will be useful
;; but without any warranty; without even the implied warranty of
;; merchantability or fitness for a particular purpose.
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;;
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.
;;

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.io.netty

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohcljc.hhh.core.sys])
  (:use [comzotohcljc.hhh.io.core])
  (:use [comzotohcljc.hhh.io.http])
  (:use [comzotohcljc.hhh.io.triggers])
  (:use [comzotohcljc.util.core :only [MubleAPI MakeMMap notnil? ConvLong] ])
  (:use [comzotohcljc.util.seqnum :only [NextLong] ])
  (:use [comzotohcljc.util.mime :only [GetCharset] ])
  (:use [comzotohcljc.util.str :only [hgl? nsb strim nichts?] ])
  (:import (java.net HttpCookie URI URL InetSocketAddress))
  (:import (java.net SocketAddress InetAddress))
  (:import (java.util ArrayList List))
  (:import (java.io IOException))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent WebSocketEvent WebSocketResult))
  (:import (javax.net.ssl SSLContext))
  (:import (io.netty.handler.codec.http HttpRequest HttpResponse CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder))
  (:import (io.netty.bootstrap ServerBootstrap))
  (:import (io.netty.channel Channel))
  (:import (com.zotohlabs.frwk.netty NettyFW))

  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.frwk.io XData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-wsock-result ""

  []

  (let [ impl (MakeMMap) ]
    (.setf! impl :binary false)
    (.setf! impl :text false)
    (.setf! impl :data nil)
    (reify
      MubleAPI
      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl))

      WebSockResult
      (isBinary [_] (.getf impl :binary))
      (isText [_] (.getf impl :text))
      (getData [_] (XData. (.getf impl :data)))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToJava ""

  [^Cookie c]

  (doto (HttpCookie. (.getName c)(.getValue c))
        (.setComment (.getComment c))
        (.setDomain (.getDomain c))
        (.setMaxAge (.getMaxAge c))
        (.setPath (.getPath c))
        (.setVersion (.getVersion c))
        (.setHttpOnly (.isHttpOnly c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-wsock-event ""

  [^comzotohcljc.hhh.io.core.EmitterAPI co
   ^Channel ch ^XData xdata]

  (let [ ssl (notnil? (.get (NettyFW/getPipeline ch) "ssl"))
         ^InetSocketAddress laddr (.localAddress ch)
         ^WebSockResult res (make-wsock-result)
         impl (MakeMMap)
         eeid (NextLong) ]
    (with-meta
      (reify
        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        WebSockEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (isSSL [_] ssl)
        (isText [_] (instance? String (.content xdata)))
        (isBinary [this] (not (.isText this)))
        (getData [_] xdata)
        (getResultObj [_] res)
        (replyResult [this]
          (let [ ^comzotohcljc.hhh.io.core.WaitEventHolder
                 wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
        (emitter [_] co))

      { :typeid :czc.hhh.io/WebSockEvent }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.hhh.io/NettyIO

  [^comzotohcljc.hhh.io.core.EmitterAPI co & args]

  (let [ ^HTTPResult res (MakeHttpResult)
         ^HttpRequest req (nth args 1)
         ^XData xdata (nth args 2)
         ^Channel ch (nth args 0)
         ssl (notnil? (.get (NettyFW/getPipeline ch) "ssl"))
         ^InetSocketAddress laddr (.localAddress ch)
         impl (MakeMMap)
         eeid (NextLong) ]
    (with-meta
      (reify

        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        HTTPEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (emitter [_] co)
        (getCookies [_]
          (let [ v (nsb (HttpHeaders/getHeader req "Cookie"))
                 rc (ArrayList.)
                 cks (if (hgl? v) (CookieDecoder/decode v) []) ]
            (doseq [ ^Cookie c (seq cks) ]
              (.add rc (cookieToJava c)))
            rc))
        (getCookie [_ nm]
          (let [ v (nsb (HttpHeaders/getHeader req "Cookie"))
                 lnm (cstr/lower-case nm)
                 cks (if (hgl? v) (CookieDecoder/decode v) []) ]
            (some (fn [^Cookie c]
                    (if (= (cstr/lower-case (.getName c)) lnm)
                      (cookieToJava c)
                      nil))
                    (seq cks))))

        (isKeepAlive [_] (HttpHeaders/isKeepAlive req))

        (hasData [_] (notnil? xdata))
        (data [_] xdata)

        (contentType [_] (HttpHeaders/getHeader req "content-type"))
        (contentLength [_] (HttpHeaders/getContentLength req 0))

        (encoding [this]  (GetCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaderValues [_ nm] (-> (.headers req) (.getAll nm)))
        (getHeaders [_] (-> (.headers req) (.names)))
        (getHeaderValue [_ nm] (HttpHeaders/getHeader req nm))
        (getParameterValues [_ nm]
          (let [ dc (QueryStringDecoder. (.getUri req))
                 rc (.get (.parameters dc) nm) ]
            (if (nil? rc) (ArrayList.) rc)))
        (getParameters [_]
          (let [ dc (QueryStringDecoder. (.getUri req))
                 m (.parameters dc) ]
            (.keySet m)))
        (getParameterValue [_ nm]
          (let [ dc (QueryStringDecoder. (.getUri req))
                 ^List rc (.get (.parameters dc) nm) ]
            (if (and (notnil? rc) (> (.size rc) 0))
              (.get rc 0)
              nil)))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (.toString (.getProtocolVersion req)))
        (method [_] (.toString (.getMethod req)))

        (host [_] (HttpHeaders/getHost req))

        (queryString [_]
          (let [ s (nsb (.getUri req))
                 pos (.indexOf s "?") ]
            (if (>= pos 0)
              (.substring s pos)
              "")))

        (remotePort [_] (conv-long (HttpHeaders/getHeader req "REMOTE_PORT") 0))
        (remoteAddr [_] (nsb (HttpHeaders/getHeader req "REMOTE_ADDR")))
        (remoteHost [_] (nsb (HttpHeaders/getHeader req "")))

        (scheme [_] (if ssl "https" "http"))

        (serverPort [_] (ConvLong (HttpHeaders/getHeader req "SERVER_PORT") 0))
        (serverName [_] (nsb (HttpHeaders/getHeader req "SERVER_NAME")))

        (isSSL [_] ssl)

        (getUri [_]
          (let [ dc (QueryStringDecoder. (.getUri req)) ]
            (.path dc)))

        (getRequestURL [_] (throw (IOException. "not implemented")))

        (getResultObj [_] res)
        (replyResult [this]
          (let [ ^comzotohcljc.hhh.io.core.WaitEventHolder
                 wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
      )

      { :typeid :czc.hhh.io/HTTPEvent } 

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.hhh.io/NettyIO

  [^comzotohcljc.hhh.core.sys.Element co cfg]

  (let [ c (nsb (:context cfg)) ]
    (.setAttr! co :contextPath (strim c))
    (HttpBasicConfig co cfg) 
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyInitor ""

  [options]

  (let []
    (proxy [ChannelInitializer] []
      (initChannel [^SocketChannel ch]
        (let [ ^ChannelPipeline pl (NetUtils/getPipeline ch) ]
          (AddEnableSSL pl options)
          (AddExpect100 pl options)
          (.addLast pl "codec" (HttpServerCodec.))
          (AddAuxDecoder pl options)
          (.addLast pl "chunker" (ChunkedWriteHandler.))
          (AddMsgDispatcher pl options)
          (AddExceptionCatcher pl options)
          pl)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^comzotohcljc.hhh.core.sys.Element co]

  (let [ ^comzotohcljc.hhh.core.sys.Element
         ctr (.parent ^Hierarchial co)
         options (doto (JsonObject.)
                   (.addProperty "serverkey" (.getAttr co :serverKey))
                   (.addProperty "passwd" (.getAttr co :passwd)))
         options { :emitter co
                   :rtcObj (MakeRouteCracker (.getAttr ctr :routes)) }
         bs (ServerSide/initServerSide cfg options) ]
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.hhh.io/NettyIO

  [^comzotohcljc.hhh.core.sys.Element co]

  (let [ host (nsb (.getAttr co :host))
         port (.getAttr co :port)
         nes (.getAttr co :netty)
         ^ServerBootstrap bs (:bootstrap nes)
         ch (ServerSide/start bs host (int port)) ]
    (.setAttr! co :netty (assoc nes :channel ch))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.hhh.io/NettyIO

  [^comzotohcljc.hhh.core.sys.Element co]

  (let [ nes (.getAttr co :netty)
         ^ServerBootstrap bs (:bootstrap nes)
         ^Channel ch (:channel nes) ]
    (ServerSide/stop  bs ch)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.hhh.io/NettyIO

  [^comzotohcljc.hhh.core.sys.Element co]

  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private netty-eof nil)

