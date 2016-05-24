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
      :author "kenl" }

  czlab.skaro.io.http

  (:require
    [czlab.xlib.str :refer [lcase hgl? strim]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.core
     :refer [juid
             spos?
             nextLong
             toJavaInt
             subsVar
             mubleObj!
             test-cond
             stringify]]
    [czlab.net.comms :refer [parseBasicAuth]]
    [czlab.crypto.codec :refer [pwdify]]
    [czlab.net.routes :refer [loadRoutes]])

  (:use [czlab.skaro.core.consts]
        [czlab.crypto.ssl]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.core]
        [czlab.skaro.io.webss])

  (:import
    [javax.servlet.http Cookie HttpServletRequest]
    [org.apache.commons.codec.binary Base64]
    [org.apache.commons.lang3 StringUtils]
    [czlab.wflow.server Emitter Component]
    [java.net URL]
    [java.io File]
    [czlab.crypto PasswordAPI]
    [java.net HttpCookie]
    [czlab.xlib Muble
     XData
     Versioned
     Hierarchial
     Identifiable
     Disposable
     Startable]
    [javax.servlet.http HttpServletRequest
     HttpServletResponse]
    [czlab.skaro.io WebSockResult
     IOSession
     ServletEmitter
     HTTPResult
     HTTPEvent JettyUtils]
    [czlab.skaro.server Cocoon]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String AUTH "Authorization")
(def ^:private ^String BASIC "Basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn scanBasicAuth

  "Scan and parse if exists basic authentication"

  ;; returns map
  [^HTTPEvent evt]

  (when (.hasHeader evt AUTH)
    (parseBasicAuth (.getHeaderValue evt AUTH))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn httpBasicConfig

  "Basic http config"

  [^Muble co cfg]

  (let [{:keys [serverKey sockTimeOut
                sslType contextPath
                limitKB waitMillis
                port host workers appkey]}
        cfg
        kfile (subsVar serverKey)
        ssl (hgl? kfile)  ]

    (with-local-vars [cpy (transient cfg)]
      (when (empty? sslType)
        (var-set cpy (assoc! @cpy :sslType "TLS")))
      (when-not (spos? port)
        (var-set cpy (assoc! @cpy
                             :port
                             (if ssl 443 80))))
      (when (nil? host)
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
                               (pwdify (:passwd cfg) appkey))))
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
(defmethod compConfigure :czc.skaro.io/HTTP

  [^Muble co cfg0]

  (log/info "compConfigure: HTTP: %s" (.id ^Identifiable co))
  (->> (merge (.getv co :dftOptions) cfg0)
       (HttpBasicConfig co )
       (.setv co :emcfg ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn wsockResult

  "Create a WebSocket result object"

  ^WebSockResult
  [co]

  (let [impl (mubleObj! {:binary false
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
(defn httpResult

  "Create a HttpResult object"

  ^HTTPResult
  [co]

  (let [impl (mubleObj! {:version "HTTP/1.1"
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
(defn hasInHeader?

  "Returns true if header exists"

  ;; boolean
  [info ^String header]

  (if-some [h (:headers info) ]
    (and (> (count h) 0)
         (some? (get h (lcase header))))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasInParam?

  "true if parameter exists"

  ;; boolean
  [info ^String param]

  (if-some [p (:params info) ]
    (and (> (count p) 0)
         (some? (get p param)))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getInParameter

  "Get the named parameter"

  ^String
  [info ^String param]

  (if-some [arr (if (hasInParam? info param)
                  ((:params info) param)) ]
    (when (> (count arr) 0)
      (first arr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getInHeader

  "Get the named header"

  ^String
  [info ^String header]

  (if-some [arr (if (hasInHeader? info header)
                 ((:headers info) (lcase header))) ]
    (when (> (count arr) 0)
      (first arr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn maybeLoadRoutes

  [^Muble co]

  (let [^Cocoon ctr (.parent ^Hierarchial co)
        appDir (.getAppDir ctr)
        sf (io/file appDir DN_CONF "static-routes.conf")
        rf (io/file appDir DN_CONF "routes.conf") ]
    (.setv co
           :routes
           (vec (concat (if (.exists sf) (loadRoutes sf) [] )
                        (if (.exists rf) (loadRoutes rf) [] ))))
    (.getv co :routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


