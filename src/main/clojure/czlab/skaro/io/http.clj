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

(def ^:private ^String AUTH "authorization")
(def ^:private ^String BASIC "basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scanBasicAuth

  "Scan and parse if exists basic authentication"
  ^APersistentMap
  [^HttpEvent evt]

  (let [{:keys [headers]}
        (.msgGist evt)]
    (when (contains? headers AUTH)
      (parseBasicAuth (first (get headers AUTH))))))

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
        ^Container ctr (.server co)
        kfile (expandVars serverKey)
        ssl? (hgl? kfile)]
    (with-local-vars [cpy (transient cfg)]
      (when-not (spos? port)
        (var-set cpy (assoc! @cpy
                             :port
                             (if ssl? 443 80))))
      (if ssl?
        (do
          (test-cond "server-key file url"
                     (.startsWith kfile "file:"))
          (var-set cpy (assoc! @cpy
                               :serverKey (URL. kfile)))
          (var-set cpy (assoc! @cpy
                               :passwd
                               (->> (.appKey ctr)
                                    (passwd<> passwd)
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
                             :workers 0)))

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
(defn wsockResult<>

  "Create a WebSocket result object"
  ^WebSockResult
  [^Service co ^XData body & [binary?]]

  (let [impl (muble<> {:body body})]
    (reify

      WebSockResult

      (isText [this] (not (.isBinary this)))
      (isBinary [_] (true? binary?))
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
    (reify

      HttpResult

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

      (setContent [_ data]
        (if (some? data)
          (.setv impl :body data)) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasInHeader?

  "Returns true if header exists"
  [gist header]

  (if-some [h (:headers gist)]
    (and (not (empty? h))
         (not (empty? (get h (lcase header)))))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasInParam?

  "true if parameter exists"
  [gist param]

  (if-some [p (:params gist)]
    (and (not (empty? p))
         (not (empty? (get p param))))
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
  [^Service co]

  (let [^Container ctr (.server co)
        appDir (.appDir ctr)
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


