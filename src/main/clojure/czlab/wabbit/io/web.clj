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
      :author "Kenneth Leung"}

  czlab.wabbit.io.web

  (:require [czlab.twisty.core :refer [genMac]]
            [czlab.xlib.io :refer [hexify]]
            [czlab.xlib.logging :as log])

  (:use [czlab.convoy.netty.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.convoy.net HttpSession HttpResult RouteInfo]
           [czlab.wabbit.etc ExpiredError AuthError]
           [czlab.wabbit.server Container]
           [java.net HttpCookie]
           [czlab.xlib Muble CU]
           [czlab.wabbit.io
            HttpEvent
            IoService]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^String SESSION_COOKIE "__ss117")
(def ^:private SSID_FLAG :__f01es)
(def ^:private CS_FLAG :__f184n ) ;; creation time
(def ^:private LS_FLAG :__f384n ) ;; last access time
(def ^:private ES_FLAG :__f484n ) ;; expiry time
(def ^:private ^String NV_SEP "\u0000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resetFlags
  "A negative value means that the cookie is not stored persistently and will be deleted when the Web browser exits. A zero value causes the cookie to be deleted."
  [^HttpSession mvs maxAgeSecs]
  (let [maxAgeSecs (or maxAgeSecs -1)
        now (now<>)]
    (doto mvs
      (.setAttr SSID_FLAG
                (hexify (bytesify (juid))))
      (.setAttr ES_FLAG
                (if (spos? maxAgeSecs)
                  (+ now (* maxAgeSecs 1000))
                  maxAgeSecs))
      (.setAttr CS_FLAG now)
      (.setAttr LS_FLAG now))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn wsession<>
  ""
  ^HttpSession
  [^bytes pkey ssl?]
  (let [impl (muble<> {:maxIdleSecs 0
                       :newOne true})
        _attrs (muble<>)]
    (with-meta
      (reify HttpSession

        (removeAttr [_ k] (.unsetv _attrs k))
        (setAttr [_ k v] (.setv _attrs k v))
        (attr [_ k] (.getv _attrs k))
        (clear [_] (.clear _attrs))
        (attrs [_] (.seq _attrs))

        (setMaxIdleSecs [_ idleSecs]
          (if (number? idleSecs)
            (.setv impl :maxIdleSecs idleSecs)))

        (isNull [_] (empty? (.impl impl)))
        (isNew [_] (.getv impl :newOne))
        (isSSL [_] ssl?)

        (invalidate [_]
          (.clear _attrs)
          (.clear impl))

        (signer [_] pkey)

        (setXref [_ csrf]
          (.setv _attrs :csrf csrf))

        (setNew [this flag maxAge]
          (when flag
            (.clear _attrs)
            (.clear impl)
            (resetFlags this maxAge)
            (.setv impl :maxIdleSecs 0))
          (.setv impl :newOne flag))

        (maxIdleSecs [_] (or (.getv impl :maxIdleSecs) 0))
        (creationTime [_] (or (.getv _attrs CS_FLAG) 0))
        (expiryTime [_] (or (.getv _attrs ES_FLAG) 0))
        (xref [_] (.getv _attrs :csrf))
        (id [_] (.getv _attrs SSID_FLAG))

        (lastError [_] (.getv impl :error))

        (lastAccessedTime [_]
          (or (.getv _attrs LS_FLAG) 0))

        Object

        (toString [this]
          (str
            (reduce
              #(let [[k v] %2]
                 (addDelim! %1
                            NV_SEP
                            (str (name k) ":" v)))
              (strbf<>)
              (.seq _attrs)))))

        {:typeid ::HttpSession })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeMacIt
  ""
  [gist pkey ^String data]
  (if
    (boolean
      (some-> ^RouteInfo
              (get-in gist [:route :info])
              (.isSecure)))
    (str (genMac pkey data) "-" data)
    data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn downstream
  ""
  [^HttpEvent evt ^HttpResult res]
  (let
    [{:keys [sessionAgeSecs
             domainPath
             domain
             hidden
             maxIdleSecs]}
     (.. evt source config)
     mvs (.session evt)
     gist (.msgGist evt)]
    (when-not (.isNull mvs)
      (log/debug "session ok, about to set-cookie!")
      (if (.isNew mvs)
        (->> (or sessionAgeSecs 3600)
             (resetFlags mvs )))
      (let
        [ck (->> (maybeMacIt gist
                             (.signer mvs)
                             (str mvs))
                 (HttpCookie. SESSION_COOKIE))
         est (.expiryTime mvs)]
        (->> (long (or maxIdleSecs -1))
             (.setMaxIdleSecs mvs))
        (doto ck
          (.setMaxAge (if (spos? est)
                        (/ (- est (now<>)) 1000) 0))
          (.setDomain (str domain))
          (.setSecure (.isSSL mvs))
          (.setHttpOnly (true? hidden))
          (.setPath (str domainPath)))
        (.addCookie res ck)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testCookie
  ""
  [^bytes pkey ^RouteInfo ri p1 p2]
  (if (boolean (some-> ri (.isSecure)))
    (when (not= (genMac pkey p2) p1)
      (log/error "session cookie - broken")
      (trap! AuthError "Bad Session Cookie")))
  true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn upstream
  ""
  ^HttpSession
  [gist pkey maxIdleSecs]
  (let [^RouteInfo
        ri (get-in gist [:route :info])
        [^HttpSession mvs
         ^HttpCookie ck]
        (if (.wantSession ri)
          [(wsession<> pkey (:ssl? gist))
           (-> (:cookies gist)
               (get SESSION_COOKIE))])]
    (cond
      (some? ck)
      (let
        [cookie (str (.getValue ck))
         pos (.indexOf cookie (int \-))
         [^String p1 ^String p2]
         (if (< pos 0)
           ["" cookie]
           [(.substring cookie 0 pos)
            (.substring cookie (inc pos))])
         ok (testCookie pkey ri p1 p2)]
        (log/debug "session attrs= %s" p2)
        (try
          (doseq [^String nv (.split p2 NV_SEP)
                  :let [ss (.split nv ":" 2)
                        ^String s1 (aget ss 0)
                        k1 (keyword s1)
                        ^String s2 (aget ss 1)]]
            (log/debug "s-attr n=%s, v=%s" s1 s2)
            (if (and (.startsWith s1 "__f")
                     (.endsWith s1 "n"))
              (->> (convLong s2 0)
                   (.setAttr mvs k1))
              (.setAttr mvs k1 s2)))
          (catch Throwable _
            (trap! ExpiredError "malformed cookie")))
        (.setNew mvs false 0)
        (let [ts (or (.attr mvs LS_FLAG) -1)
              es (or (.attr mvs ES_FLAG) -1)
              mi (or maxIdleSecs 0)
              now (now<>)]
          (if (or (< es now)
                  (and (spos? mi)
                       (< (+ ts (* 1000 mi)) now)))
            (trap! ExpiredError "Session has expired"))
          (.setAttr mvs LS_FLAG now)
          mvs))

      (some? mvs)
      (do
        (log/warn "no s-cookie found, invalidate!")
        (.invalidate mvs)))
    mvs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


