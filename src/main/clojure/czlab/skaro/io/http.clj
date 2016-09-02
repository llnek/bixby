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

(ns ^{:doc "Common HTTP functions."
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
             muble<>
             test-cond
             stringify]]
    [czlab.netty.util :refer [parseBasicAuth]]
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.netty.routes :refer [loadRoutes]])

  (:use [czlab.skaro.sys.core]
        [czlab.crypto.ssl]
        [czlab.skaro.io.core]
        [czlab.skaro.io.webss])

  (:import
    [clojure.lang APersistentMap]
    [czlab.skaro.server
     Component
     Service
     Container]
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
     IoSession
     HttpResult
     HttpEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(derive ::HTTP :czlab.skaro.io.core/Service)
(def ^:private ^String AUTH "authorization")
(def ^:private ^String BASIC "basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scanBasicAuth

  "Scan and parse if exists basic authentication"
  ^APersistentMap
  [^HttpEvent evt]

  (let [gist (.msgGist evt)]
    (if (gistHeader? gist AUTH)
      (parseBasicAuth (gistHeader gist AUTH)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn httpBasicConfig

  "Basic http config"
  ^APersistentMap
  [^Service co & [cfg0]]

  (let [{:keys [serverKey sockTimeOut
                limitKB waitMillis
                port passwd workers]
         :as cfg}
        (merge (.config co) cfg0)
        kfile (expandVars serverKey)
        ^Container ctr (.server co)
        ssl? (hgl? kfile)]
    (if ssl?
      (test-cond "server-key file url"
                 (.startsWith kfile "file:")))
    (->>
      {:port
       (if-not (spos? port)
         (if ssl? 443 80) port)
       :passwd
       (->> (.appKey ctr)
            (passwd<> passwd)
            (.text))
       :serverKey
       (if ssl? (URL. kfile) nil)
       :sockTimeOut
       (if-not (spos? sockTimeOut) 0 sockTimeOut)
       :workers
       (if-not (spos? workers) 0 workers)
       :limitKB
       (if-not (spos? limitKB) (* 1024 4) limitKB)
       :waitMillis
       (if-not (spos? waitMillis)
         (* 1000 300) waitMillis)}
      (merge cfg ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn wsockResult<>

  "Create a WebSocket result object"
  ^WebSockResult
  [^Service co ^Object body & [binary?]]

  (let [impl (muble<>)]
    (if (true? binary?)
      (.setv impl :binary body)
      (.setv impl :text (str body)))
    (reify WebSockResult
      (isText [this] (not (.isBinary this)))
      (isBinary [_] (true? binary?))
      (isEmpty [_] (nil? body))
      (getx [_] impl)
      (source [_] co) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn httpResult<>

  "Create a HttpResult object"
  ^HttpResult
  [^Service co]

  (let [impl (muble<> {:version "HTTP/1.1"
                       :cookies []
                       :code -1
                       :headers {} })]
    (reify HttpResult

      (setRedirect [_ url] (.setv impl :redirect url))
      (setVersion [_ ver]  (.setv impl :version ver))
      (setStatus [_ code] (.setv impl :code code))
      (status [_] (.getv impl :code))
      (source [_] co)

      (addCookie [_ c]
        (when (some? c)
          (let [a (.getv impl :cookies) ]
            (.setv impl :cookies (conj a c)))))

      (containsHeader [_ nm]
        (let [m (.getv impl :headers)
              a (get m (lcase nm)) ]
          (and (some? a)
               (> (count a) 0))))

      (removeHeader [_ nm]
        (let [m (.getv impl :headers)]
          (->> (dissoc m (lcase nm))
               (.setv impl :headers))))

      (clearHeaders [_]
        (.setv impl :headers {}))

      (addHeader [_ nm v]
        (let [m (.getv impl :headers)
              a (or (get m (lcase nm))
                         [])]
          (.setv impl
                 :headers
                 (assoc m (lcase nm) (conj a v)))))

      (setHeader [_ nm v]
        (let [m (.getv impl :headers)]
          (.setv impl
                 :headers
                 (assoc m (lcase nm) [v]))))

      (setChunked [_ b] (.setv impl :chunked? b))

      (isEmpty [_] (nil? (.getv impl :body)))

      (setContent [_ data]
          (.setv impl :body data)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn maybeLoadRoutes

  ^APersistentMap
  [^Service co]

  (let [^Container ctr (.server co)
        appDir (.appDir ctr)
        ctx (.getx co)
        rf (io/file appDir DN_CONF "routes.conf")]
    (.setv ctx
           :routes
           (if (.exists rf) (loadRoutes rf) []))
    (.getv ctx :routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


