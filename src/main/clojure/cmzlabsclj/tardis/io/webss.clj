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
  (:use [cmzlabsclj.util.core :only
                                    [MubleAPI ConvLong notnil?  MakeMMap Bytesify] ])
  (:use [cmzlabsclj.crypto.core :only [GenMac] ])
  (:use [cmzlabsclj.util.str :only [nsb hgl? AddDelim!] ])
  (:use [cmzlabsclj.util.guids :only [NewUUid] ])
  (:use [cmzlabsclj.net.comms :only [GetFormFields] ])

  (:import (com.zotohlabs.gallifrey.runtime AuthError))
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
(def ^:private SESSION_COOKIE "skaro_ssid" )
(def ^:private SSID_FLAG "f_01ec")
(def ^:private TS_FLAG "f_684f" )
(def ^:private NV_SEP "\u0000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol WebSession

  ""

  (setAttribute [_ k v] )
  (getAttribute [_ k] )
  (removeAttribute [_ k] )
  (clear [_] )
  (listAttributes [_] )
  (setMaxInactiveInterval [_ idleSecs] )
  (isNew [_] )
  (isSSL [_] )
  (invalidate [_] )
  (yield [_] )
  (getCreationTime [_]  )
  (getId [_] )
  (getLastAccessedTime [_] )
  (getMaxInactiveInterval [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Realign! ""

  [^HTTPEvent evt acctObj roles]

  (let [ ^cmzlabsclj.tardis.io.webss.WebSession mvs (.getSession evt)
         ^cmzlabsclj.tardis.core.sys.Element src (.emitter evt)
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
(defn- hibernate ""

  [ ^HTTPEvent evt ^HTTPResult res ]

  (let [ ^cmzlabsclj.tardis.io.webss.WebSession mvs (.getSession evt)
         ctr (.container ^Emitter (.emitter res))
         pkey (-> ctr (.getAppKey)(Bytesify))
         s (reduce (fn [sum en]
                     (AddDelim! sum NV_SEP (str (name (first en)) ":" (last en))))
                   (StringBuilder.)
                   (seq (.listAttributes mvs)))
         data (URLEncoder/encode (nsb s) "utf-8")
         idleSecs (.getMaxInactiveInterval mvs)
         mac (GenMac pkey data)
         ck (HttpCookie. SESSION_COOKIE (str mac "-" data)) ]
    (doto ck
          (.setSecure (.isSSL mvs))
          (.setHttpOnly true)
          (.setPath "/"))
    (when (> idleSecs 0) (.setMaxAge ck idleSecs))
    (.addCookie res ck)

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resurrect ""

  [ ^HTTPEvent evt ]

  (let [ ck (.getCookie evt SESSION_COOKIE)
         ^Emitter netty (.emitter evt)
         ctr (.container netty)
         pkey (-> ctr (.getAppKey)
                      (Bytesify))
         cookie (nsb (if-not (nil? ck)
                             (.getValue ck)))
         pos (.indexOf cookie (int \-))
         [rc1 rc2] (if (< pos 0)
                       ["" ""]
                       [(.substring cookie 0 pos)
                        (.substring cookie (+ pos 1) )] ) ]
    (when-not (and (hgl? rc1)
                   (hgl? rc2)
                   (= (GenMac pkey rc2) rc1))
      (log/error "Session token - broken.")
      (throw (AuthError. "Bad Session.")))
    (let [ ss (CoreUtils/splitNull (URLDecoder/decode rc2 "utf-8"))
           idleSecs (.getAttr
                     ^cmzlabsclj.tardis.core.sys.Element
                     netty :cacheMaxAgeSecs)
           ^cmzlabsclj.tardis.io.webss.WebSession mvs (.getSession evt) ]
      (doseq [ s (seq ss) ]
        (let [ [n v] (StringUtils/split ^String s ":") ]
          (.setAttribute mvs (keyword n) v)))
      (let [ ts (ConvLong (nsb (.getAttribute mvs TS_FLAG)) -1) ]
        (if (or (< ts 0)
                (< ts (System/currentTimeMillis)))
          (throw (AuthError. "Expired Session.")))
        (.setAttribute mvs
                       (keyword TS_FLAG)
                       (+ (System/currentTimeMillis)
                          (* idleSecs 1000)))
        (.setAttribute mvs :lastTS (System/currentTimeMillis)))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSession ""

  [co ssl]

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

        (isNew [_] (.getf impl :newOne))
        (isSSL [_] ssl)

        (invalidate [_]
          (.clear! attrs)
          (.clear! impl)
          (fc))

        (yield [_]
          (.setf! attrs :createTS  (System/currentTimeMillis))
          (.setf! attrs :lastTS  (System/currentTimeMillis))
          (.setf! impl :maxIdleSecs 3600)
          (.setf! impl :newOne true))

        (getCreationTime [_]  (.getf attrs :createTS))
        (getId [_] (.getf attrs SSID_FLAG))
        (getLastAccessedTime [_] (.getf attrs :lastTS))
        (getMaxInactiveInterval [_] (.getf impl :maxIdleSecs))

      IOSession

      (handleResult [this evt res] (hibernate evt res))
      (handleEvent [this evt] (resurrect evt))
      (getImpl [_] nil))

      { :typeid :czc.tardis.io/WebSession }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSSession ""

  [co ssl]

  (MakeSession co ssl))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private webss-eof nil)

