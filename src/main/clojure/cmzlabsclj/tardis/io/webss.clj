;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabsclj.tardis.io.webss

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core
         :only [MubleAPI ConvLong notnil? ternary MakeMMap Bytesify] ])
  (:use [cmzlabsclj.nucleus.crypto.core :only [GenMac] ])
  (:use [cmzlabsclj.nucleus.util.str :only [nsb hgl? AddDelim!] ])
  (:use [cmzlabsclj.nucleus.util.guids :only [NewUUid] ])
  (:use [cmzlabsclj.nucleus.net.comms :only [GetFormFields] ])

  (:import (com.zotohlabs.gallifrey.runtime ExpiredError AuthError))
  (:import (org.apache.commons.lang3 StringUtils))

  (:import (com.zotohlabs.frwk.util CoreUtils))
  (:import (java.net HttpCookie URLDecoder URLEncoder))
  (:import (com.zotohlabs.gallifrey.io HTTPResult HTTPEvent IOSession Emitter))
  (:import (com.zotohlabs.gallifrey.core Container))
  (:import (com.zotohlabs.frwk.net ULFormItems ULFileItem)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private SESSION_COOKIE "__ssid" )
(def ^:private SSID_FLAG "__f01ec")
(def ^:private TS_FLAG "__f684f" )
(def ^:private NV_SEP "\u0000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol WebSession

  ""

  (setMaxInactiveInterval [_ idleSecs] )
  (setAttribute [_ k v] )
  (getAttribute [_ k] )
  (removeAttribute [_ k] )
  (isEncrypted? [_ ])
  (clear [_] )
  (listAttributes [_] )
  (isNew? [_] )
  (isSSL? [_] )
  (invalidate [_] )
  (yield [_] )
  (getCreationTime [_]  )
  (getId [_] )
  (getLastError [_])
  (getLastAccessedTime [_] )
  (getMaxInactiveInterval [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Realign! ""

  [^HTTPEvent evt acctObj roles]

  (let [ ^cmzlabsclj.tardis.io.webss.WebSession
         mvs (.getSession evt)
         ^cmzlabsclj.tardis.core.sys.Element
         src (.emitter evt)
         idleSecs (.getAttr src :cacheMaxAgeSecs) ]
    (doto mvs
      (.invalidate )
      (.setAttribute TS_FLAG
                     (+ (System/currentTimeMillis)
                        (* idleSecs 1000)))
      (.setAttribute (keyword SSID_FLAG) (NewUUid))
      (.yield ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeMacIt ""

  [^cmzlabsclj.tardis.io.webss.WebSession mvs
   ^Container ctr ^String data]

  (if (.isEncrypted? mvs)
      (let [ pkey (-> ctr (.getAppKey)(Bytesify)) ]
        (str (GenMac pkey data) "-" data))
      data
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hibernate ""

  [ ^HTTPEvent evt ^HTTPResult res ]

  (let [ ctr (.container ^Emitter (.emitter res))
         ^cmzlabsclj.tardis.io.webss.WebSession
         mvs (.getSession evt)
         idleSecs (.getMaxInactiveInterval mvs)
         s (maybeMacIt mvs ctr (nsb mvs))
         data (URLEncoder/encode s "utf-8")
         ck (HttpCookie. SESSION_COOKIE data) ]
    (doto ck
          (.setSecure (.isSSL? mvs))
          (.setHttpOnly true)
          (.setPath "/"))
    (when (> idleSecs 0) (.setMaxAge ck idleSecs))
    (.addCookie res ck)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeValidateCookie  ""

  [^cmzlabsclj.tardis.io.webss.WebSession mvs
   ^Container ctr
   ^String part1 ^String part2]

  (if-let [ pkey (if (.isEncrypted? mvs)
                     (-> ctr (.getAppKey) (Bytesify))
                     nil) ]
    (when (not= (GenMac pkey part2) part1)
      (log/error "Session cookie - broken.")
      (throw (AuthError. "Bad Session Cookie.")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resurrect2 ""

  [ ^HTTPEvent evt ]

  (let [ ^cmzlabsclj.tardis.io.webss.WebSession
         mvs (.getSession evt)
         ck (.getCookie evt SESSION_COOKIE)
         cookie (nsb (.getValue ck))
         netty (.emitter evt)
         pos (.indexOf cookie (int \-))
         [rc1 rc2] (if (< pos 0)
                       ["" cookie]
                       [(.substring cookie 0 pos)
                        (.substring cookie (+ pos 1) )] ) ]
    (maybeValidateCookie mvs (.container ^Emitter netty) rc1 rc2)
    (let [ ss (CoreUtils/splitNull (URLDecoder/decode rc2 "utf-8"))
           idleSecs (.getAttr
                     ^cmzlabsclj.tardis.core.sys.Element
                     netty :cacheMaxAgeSecs) ]
      (doseq [ ^String s (seq ss) ]
        (let [ [n v] (StringUtils/split s ":") ]
          (log/debug "session attribute name:value = " s)
          (.setAttribute mvs (keyword n) v)))
      (let [ ts (ConvLong (nsb (.getAttribute mvs TS_FLAG)) -1) ]
        (if (or (< ts 0)
                (< ts (System/currentTimeMillis)))
          (throw (ExpiredError. "Session has expired.")))
        (.setAttribute mvs
                       (keyword TS_FLAG)
                       (+ (System/currentTimeMillis)
                          (* idleSecs 1000)))
        (.setAttribute mvs :lastTS (System/currentTimeMillis)))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resurrect ""

  [ ^HTTPEvent evt ]

  (let [ ck (.getCookie evt SESSION_COOKIE) ]
    (if (nil? ck)
        nil
        (resurrect2 evt))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSession ""

  ^IOSession
  [co ssl encryptFlag]

  (let [ attrs (MakeMMap)
         impl (MakeMMap)
         fc (fn []
              (.setf! impl :maxIdleSecs 3600)
              (.setf! impl :newOne true)
              (.setf! attrs :createTS  0)
              (.setf! attrs :lastTS  0)) ]
    (fc)
    (with-meta
      (reify WebSession

        (setAttribute [_ k v]
          (when (= :lastTS k) (.setf! impl :newOne false))
          (.setf! attrs k v))

        (getAttribute [_ k] (.getf attrs k) )
        (removeAttribute [_ k] (.clrf! attrs k) )
        (clear [_] (.clear! attrs))
        (listAttributes [_] (.seq* attrs))

        (setMaxInactiveInterval [_ idleSecs]
          (when (and (number? idleSecs)
                     (> idleSecs 0))
            (.setf! impl :maxIdleSecs idleSecs)))

        (isNew? [_] (.getf impl :newOne))
        (isSSL? [_] ssl)
        (isEncrypted? [_] encryptFlag)

        (invalidate [_]
          (.clear! attrs)
          (.clear! impl)
          (fc))

        (yield [_]
          (.setf! attrs :createTS  (System/currentTimeMillis))
          (.setf! attrs :lastTS  (System/currentTimeMillis))
          (.setf! impl :maxIdleSecs 3600)
          (.setf! impl :newOne true))

        (getMaxInactiveInterval [_] (.getf impl :maxIdleSecs))
        (getCreationTime [_]  (.getf attrs :createTS))
        (getId [_] (.getf attrs SSID_FLAG))

        (getLastAccessedTime [_] (.getf attrs :lastTS))
        (getLastError [_] (.getf impl :error))

        Object

        (toString [this]
          (nsb (reduce (fn [sum en]
                           (AddDelim! sum NV_SEP
                                      (str (name (first en))
                                           ":"
                                           (last en))))
                       (StringBuilder.)
                       (seq (.listAttributes this)))))

        IOSession

        (handleResult [this evt res] (hibernate evt res))
        (handleEvent [this evt]
          (try
            (resurrect evt)
            (catch Throwable e#
              (.setf! impl :error e#))))

        (getImpl [_] nil))

        { :typeid :czc.tardis.io/WebSession }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSSession ""

  [co ssl ]

  (MakeSession co ssl false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private webss-eof nil)

