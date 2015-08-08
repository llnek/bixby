;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.io.http

  (:require
    [czlab.xlib.util.str :refer [lcase hgl? strim]]
    [czlab.xlib.util.core
    :refer [juid spos? NextLong
    ToJavaInt SubsVar MakeMMap test-cond Stringify]]
    [czlab.xlib.net.comms :refer [ParseBasicAuth]]
    [czlab.xlib.crypto.codec :refer [Pwdify]]
    [czlab.xlib.net.routes :refer [LoadRoutes]])

  (:require [czlab.xlib.util.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.xlib.crypto.ssl]
        [czlab.skaro.core.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.core]
        [czlab.skaro.io.webss]
        [czlab.skaro.io.triggers])

  (:import
    [java.util.concurrent ConcurrentHashMap]
    [java.net URL]
    [java.util List Map HashMap ArrayList]
    [java.io File]
    [com.zotohlab.frwk.crypto PasswordAPI]
    [com.zotohlab.frwk.util NCMap]
    [javax.servlet.http Cookie HttpServletRequest]
    [java.net HttpCookie]
    [com.google.gson JsonObject JsonArray]
    [com.zotohlab.frwk.server Emitter Component]
    [com.zotohlab.frwk.io XData]
    [com.zotohlab.frwk.core Versioned Hierarchial
    Identifiable
    Disposable Startable]
    [org.apache.commons.codec.binary Base64]
    [org.apache.commons.lang3 StringUtils]
    [javax.servlet.http HttpServletRequest
    HttpServletResponse]
    [com.zotohlab.skaro.io WebSockResult
    IOSession
    ServletEmitter
    HTTPResult
    HTTPEvent JettyUtils]
    [com.zotohlab.skaro.core Muble Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String AUTH "Authorization")
(def ^:private ^String BASIC "Basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ScanBasicAuth

  "Scan and parse if exists basic authentication"

  ;; returns map
  [^HTTPEvent evt]

  (when (.hasHeader evt AUTH)
    (ParseBasicAuth (str (.getHeaderValue evt AUTH)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HttpBasicConfig

  "Basic http config"

  [^Muble co cfg]

  (let [kfile (SubsVar (:serverKey cfg))
        socto (:sockTimeOut cfg)
        fv (:sslType cfg)
        cp (:contextPath cfg)
        kbs (:limitKB cfg)
        w (:waitMillis cfg)
        port (:port cfg)
        host (:host cfg)
        tds (:workers cfg)
        pkey (:app.pkey cfg)
        ssl (hgl? kfile) ]
    (with-local-vars [cpy (transient cfg)]
      (when (nil? fv)
        (var-set cpy (assoc! @cpy :sslType "TLS")))
      (when-not (spos? port)
        (var-set cpy (assoc! @cpy
                             :port
                             (if ssl 443 80))))
      (when (nil? host)
        (var-set cpy (assoc! @cpy :host "")))
      (when-not (hgl? cp)
        (var-set cpy (assoc! @cpy :contextPath "")))
      (when (hgl? kfile)
        (test-cond "server-key file url"
                   (.startsWith kfile "file:"))
        (var-set cpy (assoc! @cpy
                             :serverKey (URL. kfile)))
        (var-set cpy (assoc! @cpy
                             :passwd
                             (Pwdify (:passwd cfg) pkey))))
      (when-not (spos? socto)
        (var-set cpy (assoc! @cpy
                             :sockTimeOut 0)))
      ;; always async *NIO*
      (var-set cpy (assoc! @cpy :async true))

      (when-not (spos? tds)
        (var-set cpy (assoc! @cpy
                             :workers 2)))

      ;; 4Meg threshold for payload in memory
      (when-not (spos? kbs)
        (var-set cpy (assoc! @cpy
                             :limitKB
                             (* 1024 4))))
      ;; 5 mins
      (when-not (spos? w)
        (var-set cpy (assoc! @cpy
                             :waitMillis
                             (* 1000 300))))
      (persistent! @cpy))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/HTTP

  [^Muble co cfg0]

  (log/info "compConfigure: HTTP: %s" (.id ^Identifiable co))
  (let [cfg (merge (.getv co :dftOptions) cfg0)
        c2 (HttpBasicConfig co cfg) ]
    (.setv co :emcfg c2)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSockResult

  "Create a WebSocket result object"

  ^WebSockResult
  [co]

  (let [impl (MakeMMap {:binary false
                        :data nil}) ]
    (reify

      Muble

      (setv [_ k v] (.setv impl k v) )
      (seq [_] (.seq impl))
      (toEDN [_] (.toEDN impl))
      (getv [_ k] (.getv impl k) )
      (unsetv [_ k] (.unsetv impl k) )
      (clear [_] (.clear impl))

      WebSockResult

      (isBinary [_] (true? (.getv impl :binary)))
      (isText [this] (not (.isBinary this)))
      (getData [_] (XData. (.getv impl :data)))
      (emitter [_] co) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpResult

  "Create a HttpResult object"

  ^HTTPResult
  [co]

  (let [impl (MakeMMap {:version "HTTP/1.1"
                        :cookies []
                        :code -1
                        :hds {} })]
    (reify

      Muble

      (setv [_ k v] (.setv impl k v) )
      (seq [_] (.seq impl))
      (getv [_ k] (.getv impl k) )
      (unsetv [_ k] (.unsetv impl k) )
      (toEDN [_] (.toEDN impl))
      (clear [_] (.clear impl))

      HTTPResult

      (setProtocolVersion [_ ver]  (.setv impl :version ver))
      (setStatus [_ code] (.setv impl :code code))
      (getStatus [_] (.getv impl :code))
      (emitter [_] co)

      (setRedirect [_ url] (.setv impl :redirect url))

      (addCookie [_ c]
        (when (some? c)
          (let [a (.getv impl :cookies) ]
            (.setv impl :cookies (conj a c)))))

      (containsHeader [_ nm]
        (let [m (.getv impl :hds)
              a (get m (lcase nm)) ]
          (and (some? a)
               (> (count a) 0))))

      (removeHeader [_ nm]
        (let [m (.getv impl :hds)]
          (.setv impl :hds (dissoc m (lcase nm)))))

      (clearHeaders [_]
        (.setv impl :hds {}))

      (addHeader [_ nm v]
        (let [m (.getv impl :hds)
              a (or (get m (lcase nm))
                         []) ]
          (.setv impl
                 :hds
                 (assoc m (lcase nm) (conj a v)))))

      (setHeader [_ nm v]
        (let [m (.getv impl :hds) ]
          (.setv impl
                 :hds
                 (assoc m (lcase nm) [v]))))

      (setChunked [_ b] (.setv impl :chunked b))

      (setContent [_ data]
        (if (some? data)
          (.setv impl :data data)) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasInHeader?

  "Returns true if header exists"

  ;; boolean
  [info ^String header]

  (if-some [h (:headers info) ]
    (and (> (count h) 0)
         (some? (get h (lcase header))))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasInParam?

  "true if parameter exists"

  ;; boolean
  [info ^String param]

  (if-some [p (:params info) ]
    (and (> (count p) 0)
         (some? (get p param)))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetInParameter

  "Get the named parameter"

  ^String
  [info ^String param]

  (if-some [arr (if (HasInParam? info param)
                  ((:params info) param)) ]
    (when (> (count arr) 0)
      (first arr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetInHeader

  "Get the named header"

  ^String
  [info ^String header]

  (if-some [arr (if (HasInHeader? info header)
                 ((:headers info) (lcase header))) ]
    (when (> (count arr) 0)
      (first arr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeLoadRoutes

  [^Muble co]

  (let [^Container ctr (.parent ^Hierarchial co)
        appDir (.getAppDir ctr)
        sf (io/file appDir DN_CONF "static-routes.conf")
        rf (io/file appDir DN_CONF "routes.conf") ]
    (.setv co
           :routes
           (vec (concat (if (.exists sf) (LoadRoutes sf) [] )
                        (if (.exists rf) (LoadRoutes rf) [] ))))
    (.getv co :routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

