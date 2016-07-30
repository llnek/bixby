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

  czlab.skaro.io.http

  (:require
    [czlab.xlib.str :refer [lcase hgl? strim]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.core
     :refer [when-some+
             juid
             spos?
             seqint2
             toJavaInt
             subsVar
             muble<>
             test-cond
             stringify]]
    [czlab.netty.util :refer [parseBasicAuth]]
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.netty.routes :refer [loadRoutes]])

  (:use [czlab.skaro.core.consts]
        [czlab.crypto.ssl]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.core]
        [czlab.skaro.io.webss])

  (:import
    [czlab.skaro.server Component]
    [clojure.lang APersistentMap]
    [czlab.server EventEmitter]
    [java.net URL]
    [java.io File]
    [czlab.crypto PasswordAPI]
    [java.net HttpCookie]
    [czlab.xlib
     Muble
     XData
     Versioned
     Hierarchial
     Identifiable
     Disposable
     Startable]
    [czlab.skaro.io
     WebSockResult
     IOSession
     HTTPResult
     HTTPEvent]
    [czlab.skaro.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String AUTH "Authorization")
(def ^:private ^String BASIC "Basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scanBasicAuth

  "Scan and parse if exists basic authentication"
  ^APersistentMap
  [^HTTPEvent evt]

  (when (.hasHeader evt AUTH)
    (parseBasicAuth (.getHeaderValue evt AUTH))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn httpBasicConfig

  "Basic http config"
  [^Context co cfg]

  (let [{:keys [serverKey sockTimeOut
                sslType contextPath
                limitKB waitMillis
                port host workers appkey]}
        cfg
        kfile (subsVar serverKey)
        ssl (hgl? kfile)]
    (with-local-vars [cpy (transient cfg)]
      (when-not (hgl? sslType)
        (var-set cpy (assoc! @cpy :sslType "TLS")))
      (when-not (spos? port)
        (var-set cpy (assoc! @cpy
                             :port
                             (if ssl 443 80))))
      (when-not (hgl? host)
        (var-set cpy (assoc! @cpy :host "")))
      (when-not (hgl? contextPath)
        (var-set cpy (assoc! @cpy :contextPath "")))
      (if (hgl? kfile)
        (do
          (test-cond "server-key file url"
                     (.startsWith kfile "file:"))
          (var-set cpy (assoc! @cpy
                               :serverKey (URL. kfile)))
          (var-set cpy (assoc! @cpy
                               :passwd
                               (-> (:passwd cfg)
                                   (passwd<> appkey)
                                   (.text)))))
        (do
          (var-set cpy (assoc! @cpy
                               :serverKey nil))))

      (when-not (spos? sockTimeOut)
        (var-set cpy (assoc! @cpy
                             :sockTimeOut 0)))
      ;; always async *NIO*
      (var-set cpy (assoc! @cpy :async true))

      (when-not (spos? workers)
        (var-set cpy (assoc! @cpy
                             :workers 2)))

      ;; 4Meg threshold for payload in memory
      (when-not (spos? limitKB)
        (var-set cpy (assoc! @cpy
                             :limitKB
                             (* 1024 4))))
      ;; 5 mins
      (when-not (spos? waitMillis)
        (var-set cpy (assoc! @cpy
                             :waitMillis
                             (* 1000 300))))
      (persistent! @cpy))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::HTTP
  [^Context co cfg0]

  (log/info "comp->configure: HTTP: %s" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn wsockResult<>

  "Create a WebSocket result object"
  ^WebSockResult
  [^EventEmitter co]

  (let [impl (muble<> {:binary false :data nil})]
    (reify

      Context

      (getx [_] impl)

      WebSockResult

      (isBinary [_] (true? (.getv impl :binary)))
      (isText [this] (not (.isBinary this)))
      (getData [_] (xdata<> (.getv impl :data)))
      (emitter [_] co) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn httpResult<>

  "Create a HttpResult object"

  ^HTTPResult
  [^EventEmitter co]

  (let [impl (muble<> {:version "HTTP/1.1"
                       :cookies []
                       :code -1
                       :hds {} })]
    (reify

      Context

      (getx [_] impl)

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
(defn hasInHeader?

  "Returns true if header exists"
  [gist header]

  (if-some [h (:headers gist)]
    (and (> (count h) 0)
         (some? (get h (lcase header))))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasInParam?

  "true if parameter exists"
  [gist param]

  (if-some [p (:params gist)]
    (and (> (count p) 0)
         (some? (get p param)))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getInParameter

  "Get the named parameter"
  ^String
  [gist param]

  (when-some+ [arr (if (hasInParam? gist param)
                     (get (:params gist) param))]
    (first arr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getInHeader

  "Get the named header"
  ^String
  [gist header]

  (when-some+ [arr (if (hasInHeader? gist header)
                     (get (:headers gist) (lcase header)))]
    (first arr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn maybeLoadRoutes

  ^APersistentMap
  [^Context co]

  (let [^Container ctr (.parent ^Hierarchial co)
        appDir (.getAppDir ctr)
        ctx (.getx co)
        sf (io/file appDir DN_CONF "static-routes.conf")
        rf (io/file appDir DN_CONF "routes.conf")]
    (.setv ctx
           :routes
           (vec (concat (if (.exists sf) (loadRoutes sf) [] )
                        (if (.exists rf) (loadRoutes rf) [] ))))
    (.getv ctx :routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


