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

  comzotohlabscljc.tardis.io.triggers

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [ThrowIOE MakeMMap Stringify notnil? Try!] ])
  (:use [comzotohlabscljc.util.str :only [nsb] ])
  (:use [comzotohlabscljc.tardis.io.core])

  (:import (org.eclipse.jetty.continuation Continuation ContinuationSupport))
  (:import (io.netty.channel Channel ChannelFuture ChannelFutureListener))
  (:import (io.netty.handler.codec.http HttpResponseStatus HttpResponse HttpHeaders
                                        HttpHeaders$Names HttpVersion LastHttpContent
                                        ServerCookieEncoder DefaultHttpResponse))
  (:import (java.nio.channels ClosedChannelException))
  (:import (com.zotohlabs.gallifrey.mvc WebAsset
                                        HTTPRangeInput ))
  (:import (java.nio ByteBuffer))
  (:import (java.io OutputStream IOException))
  (:import (io.netty.handler.stream ChunkedStream ChunkedFile))
  (:import (java.util List Timer TimerTask))
  (:import (java.net HttpCookie))
  (:import (javax.servlet.http Cookie HttpServletRequest HttpServletResponse))
  (:import (io.netty.buffer ByteBuf Unpooled ByteBufHolder))
  (:import (com.zotohlabs.gallifrey.io WebSockEvent WebSockResult HTTPEvent))
  (:import (org.apache.commons.io IOUtils))
  (:import (com.zotohlabs.frwk.netty NettyFW))
  (:import (java.io RandomAccessFile File))
  (:import (com.zotohlabs.frwk.util NCMap))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.frwk.core Identifiable))
  (:import (io.netty.handler.codec.http.websocketx WebSocketFrame
                                                   BinaryWebSocketFrame
                                                   TextWebSocketFrame)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isServletKeepAlive ""

  [^HttpServletRequest req]

  (let [ v (.getHeader req "connection") ]
    (= "keep-alive" (cstr/lower-case (nsb v)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToServlet ""

  ^Cookie
  [^HttpCookie c]

  (doto (Cookie. (.getName c) (.getValue c))
        (.setDomain (nsb (.getDomain c)))
        (.setHttpOnly (.isHttpOnly c))
        (.setMaxAge (.getMaxAge c))
        (.setPath (nsb (.getPath c)))
        (.setSecure (.getSecure c))
        (.setVersion (.getVersion c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyServlet ""

  [^comzotohlabscljc.util.core.MubleAPI res
   ^HttpServletRequest req
   ^HttpServletResponse rsp
   src]

  (let [ ^List cks (.getf res :cookies)
         ^URL url (.getf res :redirect)
         ^NCMap hds (.getf res :hds)
         os (.getOutputStream rsp)
         code (.getf res :code)
         data (.getf res :data)
         status (HttpResponseStatus/valueOf (int code)) ]

    (when (nil? status) (ThrowIOE (str "Bad HTTP Status code: " code)))
    (try
      (.setStatus rsp code)
      (doseq [[^String nm vs] (seq hds)]
        (when-not (= "content-length" (cstr/lower-case  nm))
          (doseq [vv (seq vs) ]
            (.addHeader rsp nm vv))))
      (doseq [ c (seq cks) ]
        (.addCookie rsp (cookieToServlet c)))
      (cond
        (and (>= code 300)(< code 400))
        (.sendRedirect rsp (.encodeRedirectURL rsp (nsb url)))
        :else
        (let [ ^XData dd (cond
                            (instance? XData data) data
                            (notnil? data) (XData. data)
                            :else nil)
               clen (if (and (notnil? dd) (.hasContent dd))
                        (.size dd)
                        0) ]
            (.setContentLength rsp clen)
            (.flushBuffer rsp)
            (when (> clen 0)
                  (IOUtils/copyLarge (.stream dd) os 0 clen)
                  (.flush os) )))
      (catch Throwable e# (log/error e# ""))
      (finally
        (Try! (when-not (isServletKeepAlive req) (.close os)))
        (-> (ContinuationSupport/getContinuation req)
          (.complete))) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeServletTrigger ""

  [^HttpServletRequest req ^HttpServletResponse rsp src]

  (reify AsyncWaitTrigger

    (resumeWithResult [_ res]
      (replyServlet res req rsp src) )

    (resumeWithError [_]
        (try
            (.sendError rsp 500)
          (catch Throwable e#
            (log/error e# ""))
          (finally
            (-> (ContinuationSupport/getContinuation req)
              (.complete)))) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeClose ""

  [^HTTPEvent evt ^ChannelFuture cf]

  (when-not (.isKeepAlive evt)
    (when-not (nil? cf)
      (.addListener cf ChannelFutureListener/CLOSE ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookiesToNetty ""

  ^String
  [^List cookies]

  (persistent! (reduce (fn [sum ^HttpCookie c]
                         (conj! sum
                                (ServerCookieEncoder/encode
                                  (.getName c)(.getValue c))))
                       (transient [])
                       (seq cookies))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- netty-ws-reply ""

  [^WebSockResult res ^Channel ch ^WebSockEvent evt src]

  (let [ ^XData xs (.getData res)
         bits (.javaBytes xs)
         ^WebSocketFrame
         f (cond
              (.isBinary res)
              (BinaryWebSocketFrame. (Unpooled/wrappedBuffer bits))

              :else
              (TextWebSocketFrame. (nsb (Stringify bits)))) ]
    (NettyFW/writeFlush ch f)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyOneFile ""

  [ ^RandomAccessFile raf
    ^HTTPEvent evt
    ^HttpResponse rsp ]

  (let [ ct (HttpHeaders/getHeader rsp "content-type")
         rv (.getHeaderValue evt "range") ]
    (if (cstr/blank? rv)
      (ChunkedFile. raf)
      (let [ r (HTTPRangeInput. raf ct rv)
             n (.prepareNettyResponse r rsp) ]
        (if (> n 0)
          r
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- netty-reply ""

  [^comzotohlabscljc.util.core.MubleAPI res
   ^Channel ch
   ^HTTPEvent evt
   src]

  (let [ cks (cookiesToNetty (.getf res :cookies))
         code (.getf res :code)
         rsp (NettyFW/makeHttpReply code)
         loc (nsb (.getf res :redirect))
         data (.getf res :data)
         hdrs (.getf res :hds) ]
    (with-local-vars [ clen 0 raf nil payload nil ]
      (doseq [[^String nm vs] (seq hdrs)]
        (when-not (= "content-length" (cstr/lower-case  nm))
          (doseq [vv (seq vs)]
            (HttpHeaders/addHeader rsp nm vv))))
      (doseq [s cks]
        (HttpHeaders/addHeader rsp HttpHeaders$Names/SET_COOKIE cks) )
      (when (and (>= code 300)(< code 400))
        (when-not (cstr/blank? loc)
          (HttpHeaders/setHeader rsp "Location" loc)))
      (when (and (>= code 200)
                 (< code 300)
                 (not= "HEAD" (.method evt)))
        (var-set  payload
                  (cond
                    (instance? WebAsset data)
                    (let [ ^WebAsset ws data ]
                      (HttpHeaders/setHeader rsp "content-type" (.contentType ws))
                      (var-set raf (RandomAccessFile. (.getFile ws) "r"))
                      (replyOneFile @raf evt rsp))

                    (instance? File data)
                    (do
                      (var-set raf (RandomAccessFile. ^File data "r"))
                      (replyOneFile @raf evt rsp))

                    (instance? XData data)
                    (let [ ^XData xs data ]
                      (var-set clen (.size xs))
                      (ChunkedStream. (.stream xs)))

                    (notnil? data)
                    (let [ xs (XData. data) ]
                      (var-set clen (.size xs))
                      (ChunkedStream. (.stream xs)))

                    :else
                    nil))
        (if (and (notnil? @payload)
                 (notnil? @raf))
          (var-set clen (.length ^RandomAccessFile @raf))))

      (when (.isKeepAlive evt)
        (HttpHeaders/setHeader rsp "Connection" "keep-alive"))

      (HttpHeaders/setContentLength rsp @clen)

      (NettyFW/writeOnly ch rsp)
      (log/debug "wrote response headers out to client")

      (when (and (> @clen 0)
                 (notnil? @payload))
        (NettyFW/writeOnly ch @payload)
        (log/debug "wrote response body out to client"))

      (let [ wf (NettyFW/writeFlush ch LastHttpContent/EMPTY_LAST_CONTENT) ]
        (log/debug "flushed last response content out to client")
        (.addListener wf
                      (reify ChannelFutureListener
                        (operationComplete [_ ff]
                          (Try! (when (notnil? @raf)
                                      (.close ^RandomAccessFile @raf))))))
        (when-not (.isKeepAlive evt)
          (log/debug "keep-alive == false, closing channel.  bye.")
          (.addListener wf ChannelFutureListener/CLOSE)))

    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeNettyTrigger ""

  [^Channel ch evt src]

  (reify AsyncWaitTrigger

    (resumeWithResult [_ res]
      (cond
        (instance? WebSockEvent evt)
        (Try! (netty-ws-reply res ch evt src) )
        :else
        (Try! (netty-reply res ch evt src) ) ))

    (resumeWithError [_]
      (let [ rsp (NettyFW/makeHttpReply 500) ]
        (try
          (maybeClose evt (NettyFW/writeFlush ch rsp))
          (catch ClosedChannelException e#
            (log/warn "ClosedChannelException thrown while flushing headers"))
          (catch Throwable t# (log/error t# "") )) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeAsyncWaitHolder

  [ ^comzotohlabscljc.tardis.io.core.AsyncWaitTrigger trigger
    ^HTTPEvent event ]

  (let [ impl (MakeMMap) ]
    (reify

      Identifiable
      (id [_] (.getId event))

      WaitEventHolder

      (resumeOnResult [this res]
        (let [ ^Timer tm (.getf impl :timer)
               ^comzotohlabscljc.tardis.io.core.EmitterAPI  src (.emitter event) ]
          (when-not (nil? tm) (.cancel tm))
          (.release src this)
          ;;(.mm-s impl :result res)
          (.resumeWithResult trigger res)))

      (timeoutMillis [me millis]
        (let [ tm (Timer. true) ]
          (.setf! impl :timer tm)
          (.schedule tm (proxy [TimerTask][]
                          (run [] (.onExpiry me))) ^long millis)))

      (timeoutSecs [this secs]
        (timeoutMillis this (* 1000 secs)))

      (onExpiry [this]
        (let [ ^comzotohlabscljc.tardis.io.core.EmitterAPI
               src (.emitter event) ]
          (.release src this)
          (.setf! impl :timer nil)
          (.resumeWithError trigger) ))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private triggers-eof nil)

