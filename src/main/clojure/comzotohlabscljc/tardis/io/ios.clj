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

  comzotohlabscljc.tardis.io.ios

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only
                                    [MubleAPI ConvLong notnil?  MakeMMap Bytesify] ])
  (:use [comzotohlabscljc.crypto.core :only [GenMac] ])
  (:use [comzotohlabscljc.util.str :only [nsb hgl? AddDelim!] ])
  (:use [comzotohlabscljc.util.guids :only [NewUUid] ])
  (:use [comzotohlabscljc.tardis.io.http :only [ScanBasicAuth] ])
  (:use [comzotohlabscljc.net.comms :only [GetFormFields] ])

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
(defn getSignupInfo ""

  [^HTTPEvent evt]

  (let [ data (.data evt) ]
    (with-local-vars [user nil pwd nil email nil]
      (cond
        (instance? ULFormItems data)
        (doseq [ ^ULFileItem x (getFormFields data) ]
          (log/debug "Form field: " (.getFieldName x) " = " (.getString x))
          (case (.getFieldName x)
            "password" (var-set pwd  (.getString x))
            "user" (var-set user (.getString x))
            "email" (var-set email (.getString x))
            nil))

        :else
        (do
          (var-set pwd (.getParameterValue evt "password"))
          (var-set email (.getParameterValue evt "email"))
          (var-set user (.getParameterValue evt "user"))) )

      { :principal @user :credential @pwd  :email @email }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLoginInfo ""

  [^HTTPEvent evt]

  (let [ ba (ScanBasicAuth evt)
         data (.data evt) ]
    (with-local-vars [user nil pwd nil]
      (cond
        (instance? ULFormItems data)
        (doseq [ ^ULFileItem x (getFormFields data) ]
          (log/debug "Form field: " (.getFieldName x) " = " (.getString x))
          (case (.getFieldName x)
            "password" (var-set pwd  (.getString x))
            "user" (var-set user (.getString x))
            nil))

        (notnil? ba)
        (do
          (var-set user (first ba))
          (var-set pwd (last ba)))

        :else
        (do
          (var-set pwd (.getParameterValue evt "password"))
          (var-set user (.getParameterValue evt "user"))) )

      { :principal @user :credential @pwd }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Realign! ""

  [^HTTPEvent evt acctObj roles]

  (let [ ^comzotohlabscljc.tardis.io.ios.WebSession mvs (.getSession evt)
         ^comzotohlabscljc.tardis.core.sys.Element netty (.emitter evt)
         idleSecs (.getAttr netty :cacheMaxAgeSecs) ]
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

  (let [ ^comzotohlabscljc.tardis.io.ios.WebSession mvs (.getSession evt)
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

  (let [ ^Emitter netty (.emitter evt)
         ctr (.container netty)
         pkey (-> ctr (.getAppKey)(Bytesify))
         ck (.getCookie evt SESSION_COOKIE)
         cookie (nsb (if-not (nil? ck) (.getValue ck)))
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
                     ^comzotohlabscljc.tardis.core.sys.Element
                     netty :cacheMaxAgeSecs)
           ^comzotohlabscljc.tardis.io.ios.WebSession mvs (.getSession evt) ]
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
        (removeAttribute [_ k] (.clr! attrs k) )
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
(def ^:private ios-eof nil)

