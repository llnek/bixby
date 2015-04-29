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

  czlabclj.tardis.io.basicauth

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.data.json :as json]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.str :only [lcase strim nsb hgl? ]]
        [czlabclj.xlib.util.core
         :only
         [NormalizeEmail Stringify notnil? TryC]]
        [czlabclj.tardis.io.http :only [ScanBasicAuth]]
        [czlabclj.xlib.crypto.codec :only [CaesarDecrypt]]
        [czlabclj.xlib.net.comms :only [GetFormFields]])

  (:import  [org.apache.commons.codec.binary Base64]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.skaro.io HTTPEvent Emitter]
            [com.zotohlab.skaro.core Container]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String NONCE_PARAM "nonce_token")
(def ^:private ^String CSRF_PARAM "csrf_token")
(def ^:private ^String PWD_PARAM "credential")
(def ^:private ^String EMAIL_PARAM "email")
(def ^:private ^String USER_PARAM "principal")
(def ^:private ^String CAPTCHA_PARAM "captcha")

;; hard code the shift position, the encrypt code
;; should match this value.
(def ^:private CAESAR_SHIFT 13)

(def ^:private PMS {EMAIL_PARAM [ :email #(NormalizeEmail %) ]
                    CAPTCHA_PARAM [ :captcha #(strim %) ]
                    USER_PARAM [ :principal #(strim %) ]
                    PWD_PARAM [ :credential #(strim %) ]
                    CSRF_PARAM [ :csrf #(strim %) ]
                    NONCE_PARAM [ :nonce #(notnil? %) ] })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse a standard login-like form with userid,password,email
(defn- crackFormFields ""

  [^HTTPEvent evt]

  (let [data (if (.hasData evt) (.content (.data evt)) nil) ]
    (cond
      (instance? ULFormItems data)
      (with-local-vars [rc (transient {})]
        (doseq [^ULFileItem x (seq (GetFormFields data)) ]
          (let [fm (.getFieldNameLC x)
                fv (nsb (.getString x)) ]
            (log/debug "form-field=" fm ", value=" fv)
            (when-let [v (get PMS fm) ]
              (var-set rc (assoc! @rc (first v)
                                  (apply (last v) [fv]))))))
        (persistent! @rc))
      :else nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackBodyContent "Parse a JSON body."

  [^HTTPEvent evt]

  (when-let [^XData xs (if (.hasData evt) (.data evt) nil) ]
    (when-let [json (json/read-str (if (.hasContent xs)
                                     (.stringify xs)
                                     "{}")
                                   :key-fn #(lcase %)) ]
      (with-local-vars [rc (transient {})]
        (doseq [[k v] (seq PMS)]
          (when-let [fv (get json k) ]
            (var-set rc (assoc! @rc
                                (first v)
                                (apply (last v) [fv])))))
        (persistent! @rc)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackUrlParams "Parse form fields in the Url."

  [^HTTPEvent evt]

  (with-local-vars [rc (transient {})]
    (doseq [[k v] (seq PMS)]
      (when (.hasParameter evt k)
        (var-set rc (assoc! @rc
                            (first v)
                            (apply (last v)
                                   [(.getParameterValue evt k)])))))
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeGetAuthInfo "Attempt to parse and get authentication info."

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

  [info fld shiftCount]

  (if (:nonce info)
    (TryC
      (let [decr (CaesarDecrypt (get info fld) shiftCount)
            bits (Base64/decodeBase64 decr)
            s (Stringify bits) ]
        (log/debug "info = " info)
        (log/debug "decr = " decr)
        (log/debug "val = " s)
        (assoc info fld s)))
    info
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAppKey ""

  ^bytes
  [^HTTPEvent evt]

  (-> ^Emitter (.emitter evt) (.container) (.getAppKeyBits)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetSignupInfo ""

  [^HTTPEvent evt]

  (-> (MaybeGetAuthInfo evt)
      (maybeDecodeField :principal CAESAR_SHIFT)
      (maybeDecodeField :credential CAESAR_SHIFT)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginInfo ""

  [^HTTPEvent evt]

  (-> (MaybeGetAuthInfo evt)
      (maybeDecodeField :principal CAESAR_SHIFT)
      (maybeDecodeField :credential CAESAR_SHIFT)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private basicauth-eof nil)

