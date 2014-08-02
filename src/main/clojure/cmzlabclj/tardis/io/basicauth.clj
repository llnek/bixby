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

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.io.basicauth

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.data.json :as json]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [NormalizeEmail Stringify notnil? ] ]
        [cmzlabclj.nucleus.util.str :only [strim nsb hgl? ] ]
        [cmzlabclj.tardis.io.http :only [ScanBasicAuth] ]
        [cmzlabclj.nucleus.crypto.codec :only [CaesarDecrypt] ]
        [cmzlabclj.nucleus.net.comms :only [GetFormFields] ])

  (:import  [org.apache.commons.codec.binary Base64]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.gallifrey.io HTTPEvent Emitter]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^String ^:private NONCE_PARAM "nonce_token")
(def ^String ^:private CSRF_PARAM "csrf_token")
(def ^String ^:private PWD_PARAM "credential")
(def ^String ^:private EMAIL_PARAM "email")
(def ^String ^:private USER_PARAM "principal")
(def ^String ^:private CAPTCHA_PARAM "captcha")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse a standard login-like form with userid,password,email
(defn- crackFormFields ""

  [^HTTPEvent evt]

  (when-let [^XData xs (if (.hasData evt) (.data evt) nil) ]
    (with-local-vars [csrf nil nonce false captcha nil
                      user nil pwd nil email nil
                      data (.content xs) ]
      (when (instance? ULFormItems @data)
        (doseq [^ULFileItem x (seq (GetFormFields @data)) ]
          (let [fm (cstr/lower-case (.getFieldName x))
                fv (nsb (.getString x)) ]
            (case fm
              CAPTCHA_PARAM (var-set captcha fv)
              EMAIL_PARAM (var-set email fv)
              PWD_PARAM (var-set pwd fv)
              USER_PARAM (var-set user fv)
              CSRF_PARAM (var-set csrf fv)
              NONCE_PARAM (var-set nonce true)
              nil)))
        {:email (NormalizeEmail (strim @email))
         :principal (strim @user)
         :credential (strim @pwd)
         :csrf (strim @csrf)
         :captcha (strim @captcha)
         :nonce @nonce }
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackBodyContent ""

  [^HTTPEvent evt]

  (when-let [^XData xs (if (.hasData evt) (.data evt) nil) ]
    (let [data (if (.hasContent xs) (.stringify xs) "")
          json (json/read-str data) ]
      (when-not (nil? json)
        {:nonce (hgl? (strim (get json NONCE_PARAM)))
         :principal (strim (get json USER_PARAM))
         :credential (strim (get json PWD_PARAM))
         :captcha (strim (get json CAPTCHA_PARAM))
         :csrf (strim (get json CSRF_PARAM))
         :email (NormalizeEmail (strim (get json EMAIL_PARAM))) }))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackUrlParams ""

  [^HTTPEvent evt]

  {:nonce (hgl? (strim (.getParameterValue evt NONCE_PARAM)))
   :csrf (strim (.getParameterValue evt CSRF_PARAM))
   :email (NormalizeEmail (strim (.getParameterValue evt EMAIL_PARAM)))
   :captcha (strim (.getParameterValue evt CAPTCHA_PARAM))
   :credential (strim (.getParameterValue evt PWD_PARAM))
   :principal (strim (.getParameterValue evt USER_PARAM)) })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeGetAuthInfo ""

  [^HTTPEvent evt]

  (let [ct (.contentType evt) ]
    (cond
      (or (> (.indexOf ct "form-urlencoded") 0)
          (> (.indexOf ct "form-data") 0))
      (crackFormFields evt)

      (> (.indexOf ct "/json") 0)
      (crackBodyContent evt)

      :else
      (crackUrlParams evt))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeDecodeField ""

  [info fld]

  (if (:nonce info)
    (try
      (let [decr (CaesarDecrypt (get info fld) 13)
            bits (Base64/decodeBase64 decr)
            s (Stringify bits) ]
        (log/debug "info = " info)
        (log/debug "decr = " decr)
        (log/debug "val = " s)
        (assoc info fld s))
      (catch Throwable e#
        (log/error e# "")
        nil))
    info
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAppKey ""

  ^bytes
  [^HTTPEvent evt]

  (-> (.emitter evt) (.container) (.getAppKeyBits)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetSignupInfo ""

  [^HTTPEvent evt]

  (-> (MaybeGetAuthInfo evt)
      (maybeDecodeField :principal )
      (maybeDecodeField :credential)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginInfo ""

  [^HTTPEvent evt]

  (-> (MaybeGetAuthInfo evt)
      (maybeDecodeField :principal )
      (maybeDecodeField :credential)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private basicauth-eof nil)

