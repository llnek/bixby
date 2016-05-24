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

  czlab.skaro.io.webss

  (:require
    [czlab.xlib.core
     :refer [convLong
             juid
             trap!
             mubleObj!
             stringify
             bytesify]]
    [czlab.xlib.str :refer [hgl? addDelim!]]
    [czlab.crypto.core :refer [genMac]]
    [czlab.xlib.logging :as log]
    [czlab.net.comms :refer [getFormFields]])

  (:import
    [org.apache.commons.codec.binary Base64 Hex]
    [czlab.skaro.runtime ExpiredError AuthError]
    [java.net HttpCookie URLDecoder URLEncoder]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.codec.net URLCodec]
    [czlab.xlib Muble CU]
    [czlab.wflow.server Emitter]
    [czlab.skaro.io HTTPResult
     HTTPEvent
     WebSS
     IOSession]
    [czlab.skaro.server Cocoon]
    [czlab.net ULFormItems ULFileItem]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private SESSION_COOKIE "__ss004" )
(def ^:private SSID_FLAG :__f01es)
(def ^:private CS_FLAG :__f184n ) ;; creation time
(def ^:private LS_FLAG :__f384n ) ;; last access time
(def ^:private ES_FLAG :__f484n ) ;; expiry time
(defonce ^String ^:private NV_SEP "\u0000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resetFlags ""

  [^WebSS mvs maxAge]

  (let [now (System/currentTimeMillis)
        maxAge (or maxAge 0) ]
    (doto mvs
      (.setAttribute SSID_FLAG
                     (Hex/encodeHexString (bytesify (juid))))
      (.setAttribute ES_FLAG (if (> maxAge 0)
                                 (+ now (* maxAge 1000))
                                 maxAge))
      (.setAttribute CS_FLAG now)
      (.setAttribute LS_FLAG now))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeMacIt ""

  [^HTTPEvent evt ^String data
   ^Container ctr]

  (if
    (.checkAuthenticity evt)
    (str (-> (.getAppKeyBits ctr)
             (genMac data))
         "-" data)
    data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- downstream ""

  [^HTTPEvent evt ^HTTPResult res ]

  (let [^WebSS mvs (.getSession evt) ]
    (when-not (.isNull mvs)
      (log/debug "session appears to be kosher, about to set-cookie!")
      (let [src (.emitter evt)
            cfg (-> ^Muble
                    src
                    (.getv :emcfg))
            ctr (.container src)
            du2 (.setMaxInactiveInterval mvs
                                         (long (or (:maxIdleSecs cfg) 0)))
            du1 (when (.isNew mvs)
                  (resetFlags mvs (long (or (:sessionAgeSecs cfg) 3600))))
            data (maybeMacIt evt (str mvs) ctr)
            now (System/currentTimeMillis)
            est (.getExpiryTime mvs)
            ck (HttpCookie. SESSION_COOKIE data) ]

        (doto ck
          (.setMaxAge (if (> est 0) (/ (- est now) 1000) est))
          (.setDomain (str (:domain cfg)))
          (.setSecure (.isSSL mvs))
          (.setHttpOnly (true? (:hidden cfg)))
          (.setPath (str (:domainPath cfg))))
        (.addCookie res ck)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeValidateCookie  ""

  [^HTTPEvent evt ^String part1 ^String part2
   ^Container ctr]

  (when-some [pkey (if (.checkAuthenticity evt)
                    (.getAppKeyBits ctr)
                    nil) ]
    (when (not= (genMac pkey part2) part1)
      (log/error "session cookie - broken")
      (trap! AuthError "Bad Session Cookie"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- upstream ""

  [^HTTPEvent evt ]

  (let [ck (.getCookie evt SESSION_COOKIE)
        ^WebSS mvs (.getSession evt)
        netty (.emitter evt) ]
    (if (nil? ck)
      (do
        (log/debug "request has no session-cookie, invalidate session")
        (.invalidate mvs))
      (let [cookie (str (.getValue ck))
            cfg (-> ^Muble
                    netty
                    (.getv :emcfg))
            pos (.indexOf cookie (int \-))
            [^String rc1 ^String rc2]
            (if (< pos 0)
              ["" cookie]
              [(.substring cookie 0 pos)
               (.substring cookie (+ pos 1))] ) ]
        (maybeValidateCookie evt rc1 rc2 (.container netty))
        (log/debug "session attributes = %s" rc2)
        (try
          (doseq [^String nv (.split rc2 NV_SEP)
                 :let [ss (StringUtils/split nv ":" 2)
                       ^String s1 (aget ss 0)
                       ^String s2 (aget ss 1) ]]
            (log/debug "session attr name=%s, value=%s" s1 s2)
            (if (and (.startsWith s1 "__f")
                     (.endsWith s1 "n"))
              (.setAttribute mvs (keyword s1) (ConvLong s2 0))
              (.setAttribute mvs (keyword s1) s2)))
          (catch Throwable _
            (trap! ExpiredError "Corrupted cookie")))
        (.setNew mvs false 0)
        (let [ts (or (.getAttribute mvs LS_FLAG) -1)
              es (or (.getAttribute mvs ES_FLAG) -1)
              now (System/currentTimeMillis)
              mi (or (:maxIdleSecs cfg) 0) ]
          (if (< es now)
            (trap! ExpiredError "Session has expired"))
          (if (and (> mi 0)
                   (< (+ ts (* 1000 mi)) now))
            (trap! ExpiredError "Session has been inactive too long"))
          (.setAttribute mvs LS_FLAG now))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mkWSSession ""

  ^IOSession
  [co ssl]

  (let [impl (mubleObj! {:maxIdleSecs 0
                         :newOne true})
        attrs (mubleObj!)]
    (with-meta
      (reify WebSS

        (removeAttribute [_ k] (.unsetv attrs k) )
        (setAttribute [_ k v] (.setv attrs k v))
        (getAttribute [_ k] (.getv attrs k) )
        (clear [_] (.clear attrs))
        (listAttributes [_] (.seq attrs))

        (setMaxInactiveInterval [_ idleSecs]
          (when (number? idleSecs)
            (.setv impl :maxIdleSecs idleSecs)))

        (isNull [_] (== (count (.seq impl)) 0))
        (isNew [_] (.getv impl :newOne))
        (isSSL [_] ssl)

        (invalidate [_]
          (.clear attrs)
          (.clear impl))

        (setXref [_ csrf] (.setv attrs :csrf csrf))
        (setNew [this flag maxAge]
          (if flag
            (do
              (.clear attrs)
              (.clear impl)
              (resetFlags this maxAge)
              (.setv impl :maxIdleSecs 0)
              (.setv impl :newOne true))
            (.setv impl :newOne false)))

        (getMaxInactiveInterval [_] (or (.getv impl :maxIdleSecs) 0))
        (getCreationTime [_] (or (.getv attrs CS_FLAG) 0))
        (getExpiryTime [_] (or (.getv attrs ES_FLAG) 0))
        (getXref [_] (.getv attrs :csrf))
        (getId [_] (.getv attrs SSID_FLAG))

        (getLastAccessedTime [_] (or (.getv attrs LS_FLAG) 0))
        (getLastError [_] (.getv impl :error))

        Object

        (toString [this]
          (str (reduce #(addDelim! %1 NV_SEP
                                   (str (name (first %2))
                                        ":"
                                        (last %2)))
                       (StringBuilder.)
                       (.seq attrs))))
          ;;(Base64/encodeBase64String (Bytesify (.toEDN attrs))))

        IOSession

        (handleResult [this evt res] (downstream evt res))
        (handleEvent [this evt] (upstream evt))
        (getImpl [_] nil))

        {:typeid :czc.skaro.io/WebSS })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


