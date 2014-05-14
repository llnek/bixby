;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabsclj.tardis.io.jetty

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:use [cmzlabsclj.util.core :only [MubleAPI notnil? juid TryC spos?
                                           ToJavaInt Try!
                                           MakeMMap test-cond Stringify] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.crypto.ssl])
  (:use [cmzlabsclj.util.str :only [hgl? nsb strim] ])
  (:use [cmzlabsclj.crypto.codec :only [Pwdify] ])
  (:use [cmzlabsclj.util.seqnum :only [NextLong] ])
  (:use [cmzlabsclj.tardis.core.constants])
  (:use [cmzlabsclj.tardis.core.sys])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.tardis.io.http])
  (:use [cmzlabsclj.tardis.io.webss])
  (:use [cmzlabsclj.tardis.io.triggers])
  (:import (org.eclipse.jetty.server Server Connector ConnectionFactory))
  (:import (java.util.concurrent ConcurrentHashMap))
  (:import (java.net URL))
  (:import (org.apache.commons.io IOUtils))
  (:import (java.util List Map HashMap ArrayList))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.util NCMap))
  (:import (javax.servlet.http Cookie HttpServletRequest))
  (:import (java.net HttpCookie))
  (:import (com.google.gson JsonObject))
  (:import (org.eclipse.jetty.continuation Continuation ContinuationSupport))
  (:import (com.zotohlabs.frwk.server Component))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.frwk.core Versioned Hierarchial
                                    Identifiable Disposable Startable))
  (:import (org.apache.commons.codec.binary Base64))
  (:import (org.eclipse.jetty.server Connector HttpConfiguration
                                      HttpConnectionFactory SecureRequestCustomizer
                                      Server ServerConnector Handler
                                      SslConnectionFactory))
  (:import (org.eclipse.jetty.util.ssl SslContextFactory))
  (:import (org.eclipse.jetty.util.thread QueuedThreadPool))
  (:import (org.eclipse.jetty.util.resource Resource))
  (:import (org.eclipse.jetty.server.handler AbstractHandler ContextHandler
                                             ContextHandlerCollection ResourceHandler))
  (:import (com.zotohlabs.gallifrey.io IOSession ServletEmitter Emitter))
  (:import (org.eclipse.jetty.webapp WebAppContext))
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse))

  (:import (com.zotohlabs.gallifrey.io WebSockResult HTTPResult HTTPEvent JettyUtils))
  (:import (com.zotohlabs.gallifrey.core Container)))

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

  [^cmzlabsclj.util.core.MubleAPI res
   ^HttpServletRequest req
   ^HttpServletResponse rsp
   src]

  (let [ ^List cks (.getf res :cookies)
         ^URL url (.getf res :redirect)
         ^NCMap hds (.getf res :hds)
         os (.getOutputStream rsp)
         code (.getf res :code)
         data (.getf res :data) ]
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
(defn- makeServletTrigger ""

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
(defmethod CompConfigure :czc.tardis.io/JettyIO

  [^cmzlabsclj.tardis.core.sys.Element co cfg]

  (let [ c (nsb (:context cfg)) ]
    (.setAttr! co K_APP_CZLR (get cfg K_APP_CZLR))
    (.setAttr! co :contextPath (strim c))
    (HttpBasicConfig co cfg)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cfgHTTPS ""

  ^ServerConnector
  [^Server server port ^URL keyfile ^String pwd conf]

  ;; SSL Context Factory for HTTPS and SPDY
  (let [ sslxf (doto (SslContextFactory.)
                     (.setKeyStorePath (-> keyfile (.toURI)(.toURL)(.toString)))
                     (.setKeyStorePassword pwd)
                     (.setKeyManagerPassword pwd))
         config (doto (HttpConfiguration. conf)
                      (.addCustomizer (SecureRequestCustomizer.)))
         https (doto (ServerConnector. server)
                     (.addConnectionFactory (SslConnectionFactory. sslxf "HTTP/1.1"))
                     (.addConnectionFactory (HttpConnectionFactory. config))) ]
    (doto https
          (.setPort port)
          (.setIdleTimeout (int 500000)))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/JettyIO

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ conf (doto (HttpConfiguration.)
                    (.setRequestHeaderSize 8192)  ;; from jetty examples
                    (.setOutputBufferSize (int 32768)))
         keyfile (.getAttr co :serverKey)
         ^String host (.getAttr co :host)
         port (.getAttr co :port)
         pwdObj (.getAttr co :pwd)
         ws (.getAttr co :workers)
         ;;q (QueuedThreadPool. (if (pos? ws) ws 8))
         svr (Server.)
         cc  (if (nil? keyfile)
               (doto (JettyUtils/makeConnector svr conf)
                     (.setPort port)
                     (.setIdleTimeout (int 30000)))
               (cfgHTTPS svr port keyfile (nsb pwdObj)
                         (doto conf
                               (.setSecureScheme "https")
                               (.setSecurePort port)))) ]

    (when (hgl? host) (.setHost cc host))
    (.setName cc (juid))
    (doto svr
      (.setConnectors (into-array Connector [cc])))
    (.setAttr! co :jetty svr)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dispREQ ""

  [ ^cmzlabsclj.tardis.core.sys.Element co
    ^Continuation ct
    ^HttpServletRequest req rsp]

  (let [ ^HTTPEvent evt (IOESReifyEvent co req)
         ssl (= "https" (.getScheme req))
         wss (MakeWSSession co ssl)
         wm (.getAttr co :waitMillis) ]
    (.bindSession evt wss)
    (doto ct
          (.setTimeout wm)
          (.suspend rsp))
    (let [ ^cmzlabsclj.tardis.io.core.WaitEventHolder
           w  (MakeAsyncWaitHolder (makeServletTrigger req rsp co) evt)
          ^cmzlabsclj.tardis.io.core.EmitterAPI src co ]
      (.timeoutMillis w wm)
      (.hold src w)
      (.dispatch src evt {}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serviceJetty ""

  [ co ^HttpServletRequest req ^HttpServletResponse rsp]

  (let [ c (ContinuationSupport/getContinuation req) ]
    (when (.isInitial c)
      (TryC
          (dispREQ co c req rsp) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/JettyIO

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ ^cmzlabsclj.tardis.core.sys.Element
         ctr (.parent ^Hierarchial co)
         ^Server jetty (.getAttr co :jetty)
         ^File app (.getAttr ctr K_APPDIR)
         ^File rcpath (File. app DN_PUBLIC)
         rcpathStr (-> rcpath (.toURI)
                              (.toURL)
                              (.toString))
         cp (strim (.getAttr co :contextPath))
         ctxs (ContextHandlerCollection.)
         c2 (ContextHandler.)
         c1 (ContextHandler.)
         r1 (ResourceHandler.)
         myHandler (proxy [AbstractHandler] []
                     (handle [target baseReq req rsp]
                       (serviceJetty co req rsp))) ]
    ;; static resources are based from resBase, regardless of context
    (-> r1 (.setBaseResource (Resource/newResource rcpathStr)))
    (.setContextPath c1 (str "/" DN_PUBLIC))
    (.setHandler c1 r1)

    (.setClassLoader c2 (.getAttr co K_APP_CZLR))
    (.setContextPath c2 cp)
    (.setHandler c2 myHandler)
    (.setHandlers ctxs (into-array Handler [c1 c2]))
    (.setHandler jetty ctxs)
    (.start jetty)
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/JettyIO

  [^cmzlabsclj.tardis.core.sys.Element co]

  (let [ ^Server svr (.getAttr co :jetty) ]
    (when-not (nil? svr)
      (TryC
          (.stop svr) ))
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookie-to-javaCookie  ""

  [^Cookie c]

  (doto (HttpCookie. (.getName c) (.getValue c))
        (.setDomain (.getDomain c))
        (.setHttpOnly (.isHttpOnly c))
        (.setMaxAge (.getMaxAge c))
        (.setPath (.getPath c))
        (.setSecure (.getSecure c))
        (.setVersion (.getVersion c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/JettyIO

  [co & args]

  (let [ ^HTTPResult result (MakeHttpResult co)
         ^HttpServletRequest req (first args)
         ssl (= "https" (.getScheme req))
         impl (MakeMMap)
         eid (NextLong) ]
    (reify

      Identifiable
      (id [_] eid)

      HTTPEvent

      (getCookie [_ nm]
        (let [ lnm (cstr/lower-case nm)
               cs (.getCookies req) ]
          (some (fn [^Cookie c]
                  (if (= lnm (cstr/lower-case (.getName c)))
                    (cookie-to-javaCookie c)
                    nil))
                (if (nil? cs) [] (seq cs)))) )

      (getId [_] eid)

      (getCookies [_]
        (let [ rc (ArrayList.)
               cs (.getCookies req) ]
          (if-not (nil? cs)
            (doseq [ c (seq cs) ]
              (.add rc (cookie-to-javaCookie c))))
          rc))

      (bindSession [this s]
        (.setf! impl :ios s)
        (.handleEvent ^IOSession s this))

      (getSession [_] (.getf impl :ios))
      (emitter [_] co)
      (isKeepAlive [_] (= (cstr/lower-case (nsb (.getHeader req "connection"))) "keep-alive"))
      (data [_] nil)
      (hasData [_] false)
      (contentLength [_] (.getContentLength req))
      (contentType [_] (.getContentType req))
      (encoding [_] (.getCharacterEncoding req))
      (contextPath [_] (.getContextPath req))

      (hasHeader [_ nm] (notnil? (.getHeader req nm)))
      (getHeaderValue [_ nm] (.getHeader req nm))
      (getHeaderValues [_ nm]
        (let [ rc (ArrayList.) ]
          (doseq [ s (seq (.getHeaders req nm)) ]
            (.add rc s))))

      (getHeaders [_]
        (let [ rc (ArrayList.) ]
          (doseq [ ^String s (seq (.getHeaderNames req)) ]
            (.add rc s))) )

      (getParameterValue [_ nm] (.getParameter req nm))
      (hasParameter [_ nm]
        (.containsKey (.getParameterMap req) nm))

      (getParameterValues [_ nm]
        (let [ rc (ArrayList.) ]
          (doseq [ s (seq (.getParameterValues req nm)) ]
            (.add rc s))))

      (getParameters [_]
        (let [ rc (ArrayList.) ]
          (doseq [ ^String s (seq (.getParameterNames req)) ]
            (.add rc s))) )

      (localAddr [_] (.getLocalAddr req))
      (localHost [_] (.getLocalName req))
      (localPort [_] (.getLocalPort req))

      (queryString [_] (.getQueryString req))
      (method [_] (.getMethod req))
      (protocol [_] (.getProtocol req))

      (remoteAddr [_] (.getRemoteAddr req))
      (remoteHost [_] (.getRemoteHost req))
      (remotePort [_] (.getRemotePort req))

      (scheme [_] (.getScheme req))

      (serverName [_] (.getServerName req))
      (serverPort [_] (.getServerPort req))

      (host [_] (.getHeader req "host"))

      (isSSL [_] (= "https" (.getScheme req)))

      (getUri [_] (.getRequestURI req))

      (getRequestURL [_] (.getRequestURL req))

      (getResultObj [_] result)
      (replyResult [this]
        (let [ ^IOSession mvs (.getSession this)
               code (.getStatus result)
               ^cmzlabsclj.tardis.io.core.WaitEventHolder
               wevt (.release ^cmzlabsclj.tardis.io.core.EmitterAPI co this) ]
          (cond
            (and (>= code 200)(< code 400)) (.handleResult mvs this result)
            :else nil)
          (when-not (nil? wevt)
            (.resumeOnResult wevt result))))

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private jetty-eof nil)

