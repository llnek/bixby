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

  comzotohlabscljc.tardis.io.http

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:use [comzotohlabscljc.util.core :only [MubleAPI notnil? juid TryC spos?
                                           ToJavaInt
                                           MakeMMap test-cond Stringify] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.crypto.ssl])
  (:use [comzotohlabscljc.util.str :only [hgl? nsb strim] ])
  (:use [comzotohlabscljc.crypto.codec :only [Pwdify] ])
  (:use [comzotohlabscljc.util.seqnum :only [NextLong] ])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.io.webss])
  (:use [comzotohlabscljc.tardis.io.triggers])
  (:import (org.eclipse.jetty.server Server Connector ConnectionFactory))
  (:import (java.util.concurrent ConcurrentHashMap))
  (:import (java.net URL))
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
(defn ScanBasicAuth ""

  [^HTTPEvent evt]

  (if (.hasHeader evt "authorization")
    (let [ s (Stringify (Base64/decodeBase64 (.getHeaderValue evt "authorization")))
           pos (.indexOf s ":") ]
      (if (pos > 0)
        [ (.substring s 0 pos) (.substring s (inc pos)) ]
        []))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; old code
(defn MakeServletEmitter ""

  [^Container parObj]

  (let [ eeid (NextLong)
         impl (MakeMMap) ]
    (.setf! impl :backlog (ConcurrentHashMap.))
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )

        Component

        (version [_] "1.0")
        (id [_] eeid)

        Hierarchial

        (parent [_] parObj)

        ServletEmitter

        (container [this] (.parent this))
        (doService [this req rsp]
          (let [ ^comzotohlabscljc.tardis.core.sys.Element dev this
                 ^long wm (.getAttr dev :waitMillis) ]
            (doto (ContinuationSupport/getContinuation req)
              (.setTimeout wm)
              (.suspend rsp))
            (let [ evt (IOESReifyEvent this req)
                   ^comzotohlabscljc.tardis.io.core.WaitEventHolder
                   w (MakeAsyncWaitHolder (MakeServletTrigger req rsp dev) evt)
                   ^comzotohlabscljc.tardis.io.core.EmitterAPI  src this ]
              (.timeoutMillis w wm)
              (.hold src w)
              (.dispatch src evt {}))) )

        Disposable

        (dispose [this] (IOESDispose this))

        Startable

        (start [this] (IOESStart this))
        (stop [this] (IOESStop this))

        EmitterAPI

        (enabled? [_] (if (false? (.getf impl :enabled)) false true ))
        (active? [_] (if (false? (.getf impl :active)) false true))

        (suspend [this] (IOESSuspend this))
        (resume [this] (IOESResume this))

        (release [_ wevt]
          (when-not (nil? wevt)
            (let [ wid (.id ^Identifiable wevt)
                   b (.getf impl :backlog) ]
              (log/debug "emitter releasing an event with id: " wid)
              (.remove ^Map b wid))))

        (hold [_ wevt]
          (when-not (nil? wevt)
            (let [ wid (.id ^Identifiable wevt)
                   b (.getf impl :backlog) ]
              (log/debug "emitter holding an event with id: " wid)
              (.put ^Map b wid wevt))))

        (dispatch [this ev options]
          (TryC
              (.notifyObservers parObj ev options) )) )

      { :typeid :czc.tardis.io/JettyIO }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HttpBasicConfig ""

  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ ^String file (:server-key cfg)
         ^String fv (:flavor cfg)
         socto (:soctoutmillis cfg)
         kbs (:threshold-kb cfg)
         w (:wait-millis cfg)
         port (:port cfg)
         bio (:sync cfg)
         tds (:workers cfg)
         pkey (:hhh.pkey cfg)
         ssl (hgl? file)
         json (JsonObject.) ]

    (let [ xxx (if (spos? port) port (if ssl 443 80)) ]
      (.addProperty json "port" (ToJavaInt port))
      (.setAttr! co :port port))

    (let [ xxx (nsb (:host cfg)) ]
      (.addProperty json "host" xxx)
      (.setAttr! co :host xxx))

    (let [ ^String xxx (if (hgl? fv) fv "TLS") ]
      (.addProperty json "sslType" xxx)
      (.setAttr! co :sslType xxx))

    (when (hgl? file)
      (test-cond "server-key file url" (.startsWith file "file:"))
      (let [ xxx (URL. file) ]
        (.addProperty json "serverKey" (nsb xxx))
        (.setAttr! co :serverKey xxx))
      (let [ xxx (Pwdify ^String (:passwd cfg) pkey) ]
        (.addProperty json "pwd" (nsb xxx))
        (.setAttr! co :pwd xxx)))

    (let [ xxx (if (spos? socto) socto 0) ]
      (.addProperty json "sockTimeOut" (ToJavaInt xxx))
      (.setAttr! co :sockTimeOut xxx))

    (let [ xxx (if (true? bio) false true) ]
      (.addProperty json "async" (true? xxx))
      (.setAttr! co :async xxx))

    (let [ xxx (if (spos? tds) tds 6) ]
      (.addProperty json "workers" (ToJavaInt xxx))
      (.setAttr! co :workers xxx))

    (let [ xxx (if (spos? kbs) kbs (* 1024 1024 8)) ]
      (.addProperty json "limit" (ToJavaInt xxx))
      (.setAttr! co :limit xxx))

    ;; 5 mins
    (let [ xxx (if (spos? w) w 300000) ]
      (.addProperty json "waitMillis" (ToJavaInt xxx))
      (.setAttr! co :waitMillis xxx))

    (.setAttr! co :emcfg json)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/HTTP

  [co cfg]

  (HttpBasicConfig co cfg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/JettyIO

  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

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

  [^comzotohlabscljc.tardis.core.sys.Element co]

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

  [ ^comzotohlabscljc.tardis.core.sys.Element co
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
    (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
           w  (MakeAsyncWaitHolder (MakeServletTrigger req rsp co) evt)
          ^comzotohlabscljc.tardis.io.core.EmitterAPI src co ]
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

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^comzotohlabscljc.tardis.core.sys.Element
         ctr (.parent ^Hierarchial co)
         ^Server jetty (.getAttr co :jetty)
         ^File app (.getAttr ctr K_APPDIR)
         ^File rcpath (File. app "public")
         rcpathStr (-> rcpath (.toURI)(.toURL)(.toString))
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
    (.setContextPath c1 "/public")
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
(defmethod IOESStart :czc.tardis.io/JettyIOXXXXXX

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^comzotohlabscljc.tardis.core.sys.Element
         ctr(.parent ^Hierarchial co)
         cp (strim (.getAttr co :contextPath))
         ^Server jetty (.getAttr co :jetty)
         ^File app (.getAttr ctr K_APPDIR)
         ^WebAppContext
         webapp (JettyUtils/newWebAppContext app cp "czchhhiojetty" co)
         logDir (-> (File. app "WEB-INF/logs")(.toURI)(.toURL)(.toString))
         resBase (-> app (.toURI)(.toURL)(.toString)) ]
    ;; static resources are based from resBase, regardless of context
    (.setClassLoader webapp (.getAttr co K_APP_CZLR))
    (doto webapp
          (.setDescriptor (-> (File. app "WEB-INF/web.xml")(.toURI)(.toURL)(.toString)))
          (.setParentLoaderPriority true)
          (.setResourceBase resBase )
          (.setContextPath cp))
    ;;webapp.getWebInf()
    (.setHandler jetty webapp)
    (.start jetty)
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/JettyIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^Server svr (.getAttr co :jetty) ]
    (when-not (nil? svr)
      (TryC
          (.stop svr) ))
    (IOESStopped co)
  ))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSockResult ""

  ^WebSockResult
  [co]

  (let [ impl (MakeMMap) ]
    (.setf! impl :binary false)
    (.setf! impl :data nil)
    (reify

      MubleAPI

      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl))

      WebSockResult
      (isBinary [_] (true? (.getf impl :binary)))
      (isText [this] (not (.isBinary this)))
      (getData [_] (XData. (.getf impl :data)))
      (emitter [_] co)

  )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpResult ""

  ^HTTPResult
  [co]

  (let [ impl (MakeMMap) ]
    (.setf! impl :cookies (ArrayList.))
    (.setf! impl :code -1)
    (.setf! impl :hds (NCMap.))
    (.setf! impl :version "HTTP/1.1" )
    (reify

      MubleAPI

      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl))

      HTTPResult
      (setRedirect [_ url] (.setf! impl :redirect url))

      (setProtocolVersion [_ ver]  (.setf! impl :version ver))
      (setStatus [_ code] (.setf! impl :code code))
      (getStatus [_] (.getf impl :code))
      (emitter [_] co)
      (addCookie [_ c]
        (let [ a (.getf impl :cookies) ]
          (when-not (nil? c)
            (.add ^List a c))))

      (containsHeader [_ nm]
        (let [ m (.getf impl :hds) ]
          (.containsKey ^Map m nm)))

      (removeHeader [_ nm]
        (let [ m (.getf impl :hds) ]
          (.remove ^Map m nm)))

      (clearHeaders [_]
        (let [ m (.getf impl :hds) ]
          (.clear ^Map m)))

      (addHeader [_ nm v]
        (let [ ^Map m (.getf impl :hds)
               ^List a (.get m nm) ]
          (if (nil? a)
            (.put m nm (doto (ArrayList.) (.add v)))
            (.add a v))))

      (setHeader [_ nm v]
        (let [ ^Map m (.getf impl :hds)
               a (ArrayList.) ]
          (.add a v)
          (.put m nm a)))

      (setChunked [_ b] (.setf! impl :chunked b))

      (setContent [_ data]
        (if-not (nil? data)
          (.setf! impl :data data)) )

  )) )

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
         wss (MakeWSSession co ssl)
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

      (bindSession [_ s] (.setf! impl :ios s))
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
               ^comzotohlabscljc.tardis.io.core.WaitEventHolder
               wevt (.release ^comzotohlabscljc.tardis.io.core.EmitterAPI co this) ]
          (cond
            (and (>= code 200)(< code 400)) (.handleResult mvs this result)
            :else nil)
          (when-not (nil? wevt)
            (.resumeOnResult wevt result))))

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private http-eof nil)

