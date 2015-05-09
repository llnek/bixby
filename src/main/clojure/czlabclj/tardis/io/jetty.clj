;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.io.jetty

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlabclj.xlib.util.str :only [lcase ucase hgl? nsb strim]]
        [czlabclj.xlib.util.core
         :only
         [Muble notnil? juid TryC spos? NextLong
          ToJavaInt Try!
          MakeMMap test-cond Stringify]]
        [czlabclj.xlib.crypto.ssl]
        [czlabclj.xlib.crypto.codec :only [Pwdify]]
        [czlabclj.xlib.net.routes]
        [czlabclj.tardis.core.consts]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.io.http]
        [czlabclj.tardis.io.webss]
        [czlabclj.tardis.io.triggers])

  (:import  [org.eclipse.jetty.server Server Connector ConnectionFactory]
            [java.util.concurrent ConcurrentHashMap]
            [java.net URL]
            [jregex Matcher Pattern]
            [org.apache.commons.io IOUtils]
            [java.util List Map HashMap ArrayList]
            [java.io File]
            [com.zotohlab.frwk.util NCMap]
            [javax.servlet.http Cookie HttpServletRequest]
            [java.net HttpCookie]
            [com.google.gson JsonObject]
            [org.eclipse.jetty.continuation Continuation
             ContinuationSupport]
            [com.zotohlab.frwk.server Component Emitter]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.frwk.core Versioned Hierarchial
             Identifiable Disposable Startable]
            [org.apache.commons.codec.binary Base64]
            [org.eclipse.jetty.server Connector HttpConfiguration
             HttpConnectionFactory SecureRequestCustomizer
             Server ServerConnector Handler
             SslConnectionFactory]
            [org.eclipse.jetty.util.ssl SslContextFactory]
            [org.eclipse.jetty.util.thread QueuedThreadPool]
            [org.eclipse.jetty.util.resource Resource]
            [org.eclipse.jetty.server.handler AbstractHandler
             ContextHandler
             ContextHandlerCollection
             ResourceHandler]
            [com.zotohlab.skaro.io IOSession ServletEmitter]
            [org.eclipse.jetty.webapp WebAppContext]
            [javax.servlet.http HttpServletRequest HttpServletResponse]
            [com.zotohlab.skaro.io WebSockResult
             HTTPResult
             HTTPEvent JettyUtils]
            [com.zotohlab.skaro.core Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isServletKeepAlive ""

  [^HttpServletRequest req]

  (if-let [v (.getHeader req "connection") ]
    (>= (.indexOf (lcase v)
                  "keep-alive") 0)
    false
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

  [^czlabclj.xlib.util.core.Muble res
   ^HttpServletRequest req
   ^HttpServletResponse rsp
   src]

  (let [^URL url (.getf res :redirect)
        os (.getOutputStream rsp)
        cks (.getf res :cookies)
        hds (.getf res :hds)
        code (.getf res :code)
        data (.getf res :data) ]
    (try
      (.setStatus rsp code)
      (doseq [[nm vs] hds]
        (when (not= "content-length"
                    (lcase nm))
          (doseq [vv vs]
            (.addHeader rsp ^String nm ^String vv))))
      (doseq [c cks ]
        (.addCookie rsp (cookieToServlet c)))
      (cond
        (and (>= code 300)
             (< code 400))
        (.sendRedirect rsp
                       (.encodeRedirectURL rsp
                                           (nsb url)))
        :else
        (let [^XData dd (cond
                          (instance? XData data)
                          data
                          (notnil? data)
                          (XData. data)
                          :else nil)
              clen (if (and (notnil? dd)
                            (.hasContent dd))
                     (.size dd)
                     0) ]
            (.setContentLength rsp clen)
            (.flushBuffer rsp)
            (when (> clen 0)
              (IOUtils/copyLarge (.stream dd) os 0 clen)
              (.flush os) )))
      (catch Throwable e#
        (log/error e# ""))
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

  [^czlabclj.tardis.core.sys.Elmt co cfg0]

  (log/info "CompConfigure: JettyIO: " (.id ^Identifiable co))
  (let [cfg (merge (.getAttr co :dftOptions) cfg0)]
    (.setAttr! co :emcfg
               (HttpBasicConfig co (dissoc cfg K_APP_CZLR)))
    (.setAttr! co K_APP_CZLR (get cfg K_APP_CZLR))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cfgHTTPS ""

  ^ServerConnector
  [^Server server port
   ^URL keyfile ^String pwd conf]

  ;; SSL Context Factory for HTTPS and SPDY
  (let [sslxf (doto (SslContextFactory.)
                (.setKeyStorePath (-> keyfile
                                      (.toString )))
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

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [conf (doto (HttpConfiguration.)
               (.setRequestHeaderSize 8192)  ;; from jetty examples
               (.setOutputBufferSize (int 32768)))

        ^czlabclj.tardis.core.sys.Elmt
        ctr (.parent ^Hierarchial co)
        rts (.getAttr ctr :routes)

        cfg (.getAttr co :emcfg)
        keyfile (:serverKey cfg)
        ^String host (:host cfg)
        port (:port cfg)
        pwdObj (:passwd cfg)
        ws (:workers cfg)
         ;;q (QueuedThreadPool. (if (pos? ws) ws 8))
        svr (Server.)
        cc  (if (nil? keyfile)
              (doto (JettyUtils/makeConnector svr conf)
                (.setPort port)
                (.setIdleTimeout (int 30000)))
              (cfgHTTPS svr port keyfile
                        (if (nil? pwdObj) nil (nsb pwdObj))
                        (doto conf
                          (.setSecureScheme "https")
                          (.setSecurePort port)))) ]
    (when (hgl? host) (.setHost cc host))
    (.setName cc (juid))
    (doto svr
      (.setConnectors (into-array Connector [cc])))
    (.setAttr! co :jetty svr)
    (.setAttr! co :cracker (MakeRouteCracker rts))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dispREQ ""

  [^czlabclj.tardis.core.sys.Elmt co
   ^Continuation ct
   ^HttpServletRequest req rsp]

  (let [^czlabclj.xlib.net.routes.RouteCracker
        ck (.getAttr co :cracker)
        cfg {:method (ucase (.getMethod req))
             :uri (.getRequestURI req)}
        [r1 r2 r3 r4]
        (.crack ck cfg)]
    (cond
      (and r1
           (hgl? r4))
      (JettyUtils/replyRedirect req rsp r4)

      (= r1 true)
      (let [^czlabclj.xlib.net.routes.RouteInfo ri r2
            ^HTTPEvent evt (IOESReifyEvent co req)
            ssl (= "https" (.getScheme req))
            wss (MakeWSSession co ssl)
            cfg (.getAttr co :emcfg)
            wm (:waitMillis cfg)
            pms (.collect ri ^Matcher r3) ]
        ;;(log/debug "mvc route filter MATCHED with uri = " (.getRequestURI req))
        (.bindSession evt wss)
        ;;(.setTimeout ct wm)
        (let [^czlabclj.tardis.io.core.WaitEventHolder
              w (MakeAsyncWaitHolder (makeServletTrigger req
                                                         rsp co)
                                     evt)
              ^czlabclj.tardis.io.core.EmitAPI src co]
          (.timeoutMillis w wm)
          (.hold src w)
          (.dispatch src evt {:router (.getHandler ri)
                              :params (merge {} pms)
                              :template (.getTemplate ri) })))

      :else
      (do
        (log/debug "Failed to match uri: " (.getRequestURI req))
        (JettyUtils/replyXXX req rsp 404)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serviceJetty ""

  [co ^HttpServletRequest req ^HttpServletResponse rsp]

  (when-let [c (ContinuationSupport/getContinuation req) ]
    (when (.isInitial c)
      (TryC
        (.suspend c rsp)
        (dispREQ co c req rsp) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/JettyIO

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "IOESStart: JettyIO: " (.id ^Identifiable co))
  (let [^czlabclj.tardis.core.sys.Elmt
        ctr (.parent ^Hierarchial co)
        ^Server jetty (.getAttr co :jetty)
        ^File app (.getAttr ctr K_APPDIR)
        ^File rcpath (io/file app DN_PUBLIC)
        rcpathStr (io/as-url  rcpath)
        cfg (.getAttr co :emcfg)
        cp (:contextPath cfg)
        ctxs (ContextHandlerCollection.)
        c2 (ContextHandler.)
        c1 (ContextHandler.)
        r1 (ResourceHandler.)
        myHandler (proxy [AbstractHandler] []
                    (handle [target baseReq req rsp]
                      (serviceJetty co req rsp))) ]
    ;; static resources are based from resBase, regardless of context
    (-> r1
        (.setBaseResource (Resource/newResource rcpathStr)))
    (.setContextPath c1 (str "/" DN_PUBLIC))
    (.setHandler c1 r1)
    (.setClassLoader c2 ^ClassLoader (.getAttr co K_APP_CZLR))
    (.setContextPath c2 (strim cp))
    (.setHandler c2 myHandler)
    (.setHandlers ctxs (into-array Handler [c1 c2]))
    (.setHandler jetty ctxs)
    (.start jetty)
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/JettyIO

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "IOESStop: JettyIO: " (.id ^Identifiable co))
  (let [^Server svr (.getAttr co :jetty) ]
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
(defn- maybeGetCookies ""

  [^HttpServletRequest req]

  (with-local-vars [rc (transient {})]
    (if-let [cs (.getCookies req) ]
      (doseq [^Cookie c (seq cs) ]
        (var-set rc (assoc! @rc
                            (.getName c)
                            (cookie-to-javaCookie c)))))
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/JettyIO

  [co & args]

  (log/debug "OPESReifyEvent: JettyIO: " (.id ^Identifiable co))
  (let [^HTTPResult result (MakeHttpResult co)
        ^HttpServletRequest req (first args)
        impl (MakeMMap {:cookies (maybeGetCookies req)})
        ssl (= "https" (.getScheme req))
        eid (NextLong) ]
    (reify

      Identifiable
      (id [_] eid)

      HTTPEvent

      (getCookies [_] (vals (.getf impl :cookies)))
      (getCookie [_ nm]
        (when-let [cs (.getf impl :cookies)]
          (get cs nm)))

      (checkAuthenticity [_] false)
      (getId [_] eid)

      (bindSession [this s]
        (.setf! impl :ios s)
        (.handleEvent ^IOSession s this))

      (isKeepAlive [_] (isServletKeepAlive req))
      (getSession [_] (.getf impl :ios))
      (emitter [_] co)

      (hasData [_] false)
      (data [_] nil)

      (contentLength [_] (.getContentLength req))
      (contentType [_] (.getContentType req))
      (encoding [_] (.getCharacterEncoding req))
      (contextPath [_] (.getContextPath req))

      (getHeaderValues [this nm]
        (if (.hasHeader this nm)
          (vec (seq (.getHeaders req nm)))
          []))

      (hasHeader [_ nm] (notnil? (.getHeader req nm)))
      (getHeaderValue [_ nm] (.getHeader req nm))
      (getHeaders [_] (vec (seq (.getHeaderNames req))))

      (getParameterValue [_ nm] (.getParameter req nm))
      (hasParameter [_ nm]
        (.containsKey (.getParameterMap req) nm))

      (getParameterValues [this nm]
        (if (.hasParameter this nm)
          (vec (seq (.getParameterValues req nm)))
          []))

      (getParameters [_]
        (vec (seq (.getParameterNames req))))

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
        (let [^czlabclj.tardis.io.core.WaitEventHolder
              wevt
              (-> ^czlabclj.tardis.io.core.EmitAPI co
                  (.release this))
              ^IOSession mvs (.getSession this)
              code (.getStatus result) ]
          (cond
            (and (>= code 200)
                 (< code 400))
            (.handleResult mvs this result)
            :else nil)
          (when-not (nil? wevt)
            (.resumeOnResult wevt result))))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private jetty-eof nil)

