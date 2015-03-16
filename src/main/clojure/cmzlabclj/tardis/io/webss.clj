;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.io.webss

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr]
            [clojure.data.json :as json])

  (:use [cmzlabclj.nucleus.util.core
         :only [MubleAPI ConvLong notnil? juid ternary
                MakeMMap Stringify Bytesify] ]
        [cmzlabclj.nucleus.crypto.core :only [GenMac] ]
        [cmzlabclj.nucleus.util.str :only [nsb hgl? AddDelim!] ]
        ;;[cmzlabclj.nucleus.util.guids :only [NewUUid] ]
        [cmzlabclj.nucleus.net.comms :only [GetFormFields] ])

  (:import  [com.zotohlab.gallifrey.runtime ExpiredError AuthError]
            [org.apache.commons.lang3 StringUtils]
            [org.apache.commons.codec.net URLCodec]
            [org.apache.commons.codec.binary Base64 Hex]
            [com.zotohlab.frwk.util CoreUtils]
            [java.net HttpCookie URLDecoder URLEncoder]
            [com.zotohlab.gallifrey.io HTTPResult HTTPEvent IOSession Emitter]
            [com.zotohlab.gallifrey.core Container]
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
(def ^String ^:private NV_SEP "\u0000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol WebSession

  ""

  (setMaxInactiveInterval [_ idleSecs] )
  (setAttribute [_ k v] )
  (getAttribute [_ k] )
  (removeAttribute [_ k] )
  (clear! [_] )
  (listAttributes [_] )
  (isNew? [_] )
  (isNull? [_])
  (isSSL? [_] )
  (invalidate! [_] )
  (setNew! [_ flag maxAge] )
  (setXref [_ csrf])
  (getCreationTime [_]  )
  (getExpiryTime [_])
  (getId [_] )
  (getXref [_] )
  (getLastError [_])
  (getLastAccessedTime [_] )
  (getMaxInactiveInterval [_] ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resetFlags ""

  [^cmzlabclj.tardis.io.webss.WebSession mvs maxAge]

  (let [now (System/currentTimeMillis)
        mage (ternary maxAge 0) ]
    (.setAttribute mvs SSID_FLAG
                   (Hex/encodeHexString (Bytesify (juid))))
    (.setAttribute mvs ES_FLAG (if (> mage 0)
                                   (+ now (* mage 1000))
                                   mage))
    (.setAttribute mvs CS_FLAG now)
    (.setAttribute mvs LS_FLAG now)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeMacIt ""

  [^HTTPEvent evt
   ^Container ctr ^String data]

  (if (.checkAuthenticity evt)
    (str (GenMac (.getAppKeyBits ctr) data) "-" data)
    data
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- downstream ""

  [^HTTPEvent evt ^HTTPResult res ]

  (let [^cmzlabclj.tardis.io.webss.WebSession
        mvs (.getSession evt) ]
    (when-not (.isNull? mvs)
      (log/debug "Session appears to be kosher, about to set-cookie!")
      (let [^cmzlabclj.tardis.core.sys.Element
            src (.emitter evt)
            cfg (.getAttr src :emcfg)
            ctr (.container ^Emitter src)
            du2 (.setMaxInactiveInterval mvs
                                         (:maxIdleSecs cfg))
            du1 (if (.isNew? mvs)
                  (resetFlags mvs (:sessionAgeSecs cfg)))
            data (maybeMacIt evt ctr (nsb mvs))
            now (System/currentTimeMillis)
            est (.getExpiryTime mvs)
            ck (HttpCookie. SESSION_COOKIE data) ]
        (.setMaxAge ck (if (> est 0) (/ (- est now) 1000) est))
        (doto ck
          (.setDomain (nsb (:domain cfg)))
          (.setSecure (.isSSL? mvs))
          (.setHttpOnly (true? (:hidden cfg)))
          (.setPath (nsb (:domainPath cfg))))
        (.addCookie res ck)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeValidateCookie  ""

  [^HTTPEvent evt ^Container ctr
   ^String part1 ^String part2 ]

  (when-let [pkey (if (.checkAuthenticity evt)
                    (.getAppKeyBits ctr)
                    nil) ]
    (when (not= (GenMac pkey part2) part1)
      (log/error "Session cookie - broken.")
      (throw (AuthError. "Bad Session Cookie.")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- upstream ""

  [^HTTPEvent evt ]

  (let [^cmzlabclj.tardis.io.webss.WebSession
        mvs (.getSession evt)
        ^Emitter netty (.emitter evt)
        ck (.getCookie evt SESSION_COOKIE) ]
    (if (nil? ck)
      (do
        (log/debug "Request contains no session cookie, invalidate the session.")
        (.invalidate! mvs))
      (let [^cmzlabclj.tardis.core.sys.Element
            src netty
            cfg (.getAttr src :emcfg)
            cookie (nsb (.getValue ck))
             ;;cookie (-> (URLCodec. "utf-8")
                        ;;(decode (nsb (.getValue ck))))
            pos (.indexOf cookie (int \-))
            [rc1 rc2] (if (< pos 0)
                        ["" cookie]
                        [(.substring cookie 0 pos)
                            (.substring cookie (+ pos 1) )] ) ]
        (maybeValidateCookie evt (.container netty) rc1 rc2)
        (log/debug "Session attributes = " rc2)
        (try
          (doseq [^String nv (seq (.split ^String rc2 NV_SEP)) ]
            (let [ss (StringUtils/split nv ":" 2)
                  ^String s1 (aget ss 0)
                  ^String s2 (aget ss 1) ]
              (log/debug "Session attr name = " s1 ", value = " s2)
              (if (and (.startsWith s1 "__f")
                       (.endsWith s1 "n"))
                (.setAttribute mvs (keyword s1) (ConvLong s2 0))
                (.setAttribute mvs (keyword s1) s2))))
          (catch Throwable e#
            (throw (ExpiredError. "Corrupted cookie."))))
        (.setNew! mvs false 0)
        (let [ts (ternary (.getAttribute mvs LS_FLAG) -1)
              es (ternary (.getAttribute mvs ES_FLAG) -1)
              now (System/currentTimeMillis)
              mi (:maxIdleSecs cfg) ]
          (if (< es now)
            (throw (ExpiredError. "Session has expired.")))
          (if (and (> mi 0)
                   (< (+ ts (* 1000 mi)) now))
            (throw (ExpiredError. "Session has been inactive too long.")))
          (.setAttribute mvs LS_FLAG now))
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSSession ""

  ^IOSession
  [co ssl]

  (let [attrs (MakeMMap)
        impl (MakeMMap) ]
    (.setf! impl :maxIdleSecs 0)
    (.setf! impl :newOne true)
    (with-meta
      (reify WebSession

        (setAttribute [_ k v] (.setf! attrs k v))
        (getAttribute [_ k] (.getf attrs k) )
        (removeAttribute [_ k] (.clrf! attrs k) )
        (clear! [_] (.clear! attrs))
        (listAttributes [_] (.seq* attrs))

        (setMaxInactiveInterval [_ idleSecs]
          (when (number? idleSecs)
            (.setf! impl :maxIdleSecs idleSecs)))

        (isNull? [_] (= (count (.seq* impl)) 0))
        (isNew? [_] (.getf impl :newOne))
        (isSSL? [_] ssl)

        (invalidate! [_]
          (.clear! attrs)
          (.clear! impl))

        (setXref [_ csrf] (.setf! attrs :csrf csrf))
        (setNew! [this flag maxAge]
          (if flag
            (do
              (.clear! attrs)
              (.clear! impl)
              (resetFlags this maxAge)
              (.setf! impl :maxIdleSecs 0)
              (.setf! impl :newOne true))
            (.setf! impl :newOne false)))

        (getMaxInactiveInterval [_] (ternary (.getf impl :maxIdleSecs) 0))
        (getCreationTime [_] (ternary (.getf attrs CS_FLAG) 0))
        (getExpiryTime [_] (ternary (.getf attrs ES_FLAG) 0))
        (getXref [_] (.getf attrs :csrf))
        (getId [_] (.getf attrs SSID_FLAG))

        (getLastAccessedTime [_] (ternary (.getf attrs LS_FLAG) 0))
        (getLastError [_] (.getf impl :error))

        Object

        (toString [this]
          (nsb (reduce #(AddDelim! %1 NV_SEP
                                   (str (name (first %2))
                                        ":"
                                        (last %2)))
                       (StringBuilder.)
                       (.seq* attrs))))
          ;;(Base64/encodeBase64String (Bytesify (.toEDN attrs))))

        IOSession

        (handleResult [this evt res] (downstream evt res))
        (handleEvent [this evt] (upstream evt))
        (getImpl [_] nil))

        { :typeid :czc.tardis.io/WebSession }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private webss-eof nil)

