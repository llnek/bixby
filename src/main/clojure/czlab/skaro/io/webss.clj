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

  czlab.skaro.io.webss

  (:require
    [czlab.xlib.util.core
    :refer [ConvLong juid MakeMMap Stringify Bytesify]]
    [czlab.xlib.util.str :refer [hgl? AddDelim!]]
    [czlab.xlib.crypto.core :refer [GenMac]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.net.comms :refer [GetFormFields]])

  (:import
    [com.zotohlab.skaro.runtime ExpiredError AuthError]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.codec.net URLCodec]
    [org.apache.commons.codec.binary Base64 Hex]
    [com.zotohlab.frwk.util CU]
    [java.net HttpCookie URLDecoder URLEncoder]
    [com.zotohlab.frwk.server Emitter]
    [com.zotohlab.skaro.io HTTPResult
    HTTPEvent WebSS IOSession]
    [com.zotohlab.skaro.core Container Muble]
    [com.zotohlab.frwk.net ULFormItems ULFileItem]))

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
        mage (or maxAge 0) ]
    (.setAttribute mvs SSID_FLAG
                   (Hex/encodeHexString (Bytesify (juid))))
    (.setAttribute mvs ES_FLAG (if (> mage 0)
                                   (+ now (* mage 1000))
                                   mage))
    (.setAttribute mvs CS_FLAG now)
    (.setAttribute mvs LS_FLAG now)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeMacIt ""

  [^HTTPEvent evt ^String data
   ^Container ctr]

  (if (.checkAuthenticity evt)
    (str (GenMac (.getAppKeyBits ctr) data) "-" data)
    data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- downstream ""

  [^HTTPEvent evt ^HTTPResult res ]

  (let [^WebSS mvs (.getSession evt) ]
    (when-not (.isNull mvs)
      (log/debug "session appears to be kosher, about to set-cookie!")
      (let [^Muble src (.emitter evt)
            cfg (.getv src :emcfg)
            ctr (.container ^Emitter src)
            du2 (.setMaxInactiveInterval mvs
                                         (:maxIdleSecs cfg))
            du1 (when (.isNew mvs)
                  (resetFlags mvs (:sessionAgeSecs cfg)))
            data (maybeMacIt evt (str mvs) ctr)
            now (System/currentTimeMillis)
            est (.getExpiryTime mvs)
            ck (HttpCookie. SESSION_COOKIE data) ]
        (.setMaxAge ck (if (> est 0) (/ (- est now) 1000) est))
        (doto ck
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
    (when (not= (GenMac pkey part2) part1)
      (log/error "session cookie - broken")
      (throw (AuthError. "Bad Session Cookie")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- upstream ""

  [^HTTPEvent evt ]

  (let [ck (.getCookie evt SESSION_COOKIE)
        ^WebSS mvs (.getSession evt)
        ^Emitter netty (.emitter evt) ]
    (if (nil? ck)
      (do
        (log/debug "request contains no session cookie, invalidate the session")
        (.invalidate mvs))
      (let [cookie (str (.getValue ck))
            ^Muble src netty
            cfg (.getv src :emcfg)
            pos (.indexOf cookie (int \-))
            [^String rc1 ^String rc2]
            (if (< pos 0)
              ["" cookie]
              [(.substring cookie 0 pos)
               (.substring cookie (+ pos 1))] ) ]
        (maybeValidateCookie evt rc1 rc2 (.container netty))
        (log/debug "session attributes = %s" rc2)
        (try
          (doseq [^String nv (.split rc2 NV_SEP) ]
            (let [ss (StringUtils/split nv ":" 2)
                  ^String s1 (aget ss 0)
                  ^String s2 (aget ss 1) ]
              (log/debug "session attr name = %s, value = %s" s1 s2)
              (if (and (.startsWith s1 "__f")
                       (.endsWith s1 "n"))
                (.setAttribute mvs (keyword s1) (ConvLong s2 0))
                (.setAttribute mvs (keyword s1) s2))))
          (catch Throwable _
            (throw (ExpiredError. "Corrupted cookie"))))
        (.setNew mvs false 0)
        (let [ts (or (.getAttribute mvs LS_FLAG) -1)
              es (or (.getAttribute mvs ES_FLAG) -1)
              now (System/currentTimeMillis)
              mi (:maxIdleSecs cfg) ]
          (if (< es now)
            (throw (ExpiredError. "Session has expired")))
          (if (and (> mi 0)
                   (< (+ ts (* 1000 mi)) now))
            (throw (ExpiredError. "Session has been inactive too long")))
          (.setAttribute mvs LS_FLAG now))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSSession ""

  ^IOSession
  [co ssl]

  (let [impl (MakeMMap {:maxIdleSecs 0
                        :newOne true})
        attrs (MakeMMap)]
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
          (str (reduce #(AddDelim! %1 NV_SEP
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

