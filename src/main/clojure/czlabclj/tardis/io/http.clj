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

  czlabclj.tardis.io.http

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.core :as ccore]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.str :only [lcase hgl? nsb strim]]
        [czlabclj.xlib.util.core
         :only
         [MubleAPI notnil? juid TryC spos? NextLong
          ToJavaInt SubsVar ternary
          MakeMMap test-cond Stringify]]
        [czlabclj.xlib.crypto.ssl]
        [czlabclj.xlib.crypto.codec :only [Pwdify]]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.io.webss]
        [czlabclj.tardis.io.triggers])

  (:import  [java.util.concurrent ConcurrentHashMap]
            [java.net URL]
            [java.util List Map HashMap ArrayList]
            [java.io File]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [com.zotohlab.frwk.util NCMap]
            [javax.servlet.http Cookie HttpServletRequest]
            [java.net HttpCookie]
            [com.google.gson JsonObject JsonArray]
            [com.zotohlab.frwk.server Component]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.frwk.core Versioned Hierarchial
             Identifiable
             Disposable Startable]
            [org.apache.commons.codec.binary Base64]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.skaro.io IOSession
             ServletEmitter Emitter]
            [javax.servlet.http HttpServletRequest
             HttpServletResponse]
            [com.zotohlab.skaro.io WebSockResult
             HTTPResult
             HTTPEvent JettyUtils]
            [com.zotohlab.skaro.core Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String AUTH "Authorization")
(def ^:private ^String BASIC "Basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ScanBasicAuth ""

  [^HTTPEvent evt]

  (if (.hasHeader evt AUTH)
    (let [s (StringUtils/split (nsb (.getHeaderValue evt AUTH)))]
      (cond
        (and (== 2 (count s))
             (= "Basic" (first s))
             (hgl? (last s)))
        (let [tail (Base64/decodeBase64 ^String (last s))
              rc (StringUtils/split tail ":" 1) ]
          (if (== 2 (count rc))
            {:principal (first rc)
             :credential (last rc) }
            nil))
        :else
        nil))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HttpBasicConfig ""

  [^czlabclj.tardis.core.sys.Element co cfg]

  (let [kfile (SubsVar (nsb (:serverKey cfg)))
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
      (persistent! @cpy))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/HTTP

  [^czlabclj.tardis.core.sys.Element co cfg0]

  (log/info "CompConfigure: HTTP: " (.id ^Identifiable co))
  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        c2 (HttpBasicConfig co cfg) ]
    (.setAttr! co :emcfg c2)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSockResult ""

  ^WebSockResult
  [co]

  (let [impl (MakeMMap) ]
    (.setf! impl :binary false)
    (.setf! impl :data nil)
    (reify

      MubleAPI

      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (toEDN [_] (.toEDN impl))
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

  (let [impl (MakeMMap) ]
    (.setf! impl :version "HTTP/1.1" )
    (.setf! impl :cookies [])
    (.setf! impl :code -1)
    (.setf! impl :hds {})
    (reify

      MubleAPI

      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (toEDN [_] (.toEDN impl))
      (clear! [_] (.clear! impl))

      HTTPResult

      (setProtocolVersion [_ ver]  (.setf! impl :version ver))
      (setStatus [_ code] (.setf! impl :code code))
      (getStatus [_] (.getf impl :code))
      (emitter [_] co)

      (setRedirect [_ url] (.setf! impl :redirect url))

      (addCookie [_ c]
        (when-not (nil? c)
          (let [a (.getf impl :cookies) ]
            (.setf! impl :cookies (conj a c)))))

      (containsHeader [_ nm]
        (let [m (.getf impl :hds)
              a (get m (lcase nm)) ]
          (and (notnil? a)
               (> (count a) 0))))

      (removeHeader [_ nm]
        (let [m (.getf impl :hds)]
          (.setf! impl :hds (dissoc m (lcase nm)))))

      (clearHeaders [_]
        (.setf! impl :hds {}))

      (addHeader [_ nm v]
        (let [m (.getf impl :hds)
              a (ternary (get m (lcase nm))
                         []) ]
          (.setf! impl
                  :hds
                  (assoc m (lcase nm) (conj a v)))))

      (setHeader [_ nm v]
        (let [m (.getf impl :hds) ]
          (.setf! impl
                  :hds
                  (assoc m (lcase nm) [v]))))

      (setChunked [_ b] (.setf! impl :chunked b))

      (setContent [_ data]
        (if-not (nil? data)
          (.setf! impl :data data)) )

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasHeader? ""

  ;; boolean
  [info ^String header]

  (if-let [h (:headers info) ]
    (and (> (count h) 0)
         (notnil? (get h (lcase header))))
    false
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasParam? ""

  ;; boolean
  [info ^String param]

  (if-let [p (:params info) ]
    (and (> (count p) 0)
         (notnil? (get p param)))
    false
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetParameter ""

  ^String
  [info ^String param]

  (if-let [arr (if (HasParam? info param)
                 ((:params info) param)
                 nil) ]
    (if (> (count arr) 0)
      (first arr)
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHeader ""

  ^String
  [info ^String header]

  (if-let [arr (if (HasHeader? info header)
                 ((:headers info) (lcase header))
                 nil) ]
    (if (> (count arr) 0)
      (first arr)
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private http-eof nil)

