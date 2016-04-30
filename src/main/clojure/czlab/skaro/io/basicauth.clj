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

  czlab.skaro.io.basicauth

  (:require
    [czlab.xlib.util.format :refer [ReadJson WriteJson]]
    [czlab.xlib.util.core
    :refer [Cast? NormalizeEmail Stringify tryletc]]
    [czlab.xlib.util.str :refer [lcase strim hgl?]]
    [czlab.skaro.io.http :refer [ScanBasicAuth]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.crypto.codec :refer [CaesarDecrypt]]
    [czlab.xlib.net.comms :refer [GetFormFields]])

  (:import
    [org.apache.commons.codec.binary Base64]
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.skaro.io HTTPEvent]
    [com.zotohlab.frwk.server Emitter]
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
                    NONCE_PARAM [ :nonce #(some? %) ] })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackFormFields

  "Parse a standard login-like form with userid,password,email"

  [^HTTPEvent evt]

  (when-some [itms (some-> evt
                          (.data) (.content))]
    (with-local-vars
      [rc (transient {})]
      (doseq [^ULFileItem
              x (GetFormFields itms)
             :let [fm (.getFieldNameLC x)
                   fv (str x)]]
        (log/debug "form-field=%s, value=%s" fm fv)
        (when-some [[k v] (get PMS fm)]
          (var-set rc (assoc! @rc
                              k
                              (v fv)))))
      (persistent! @rc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackBodyContent

  "Parse a JSON body"

  [^HTTPEvent evt]

  (when-some [^XData
              xs (some-> evt (.data)) ]
    (when-some [json (ReadJson
                       (if (.hasContent xs)
                         (.stringify xs)
                         "{}")
                       #(lcase %)) ]
      (with-local-vars
        [rc (transient {})]
        (doseq [[k [a1 a2]] PMS]
          (when-some [fv (get json k) ]
            (var-set rc (assoc! @rc
                                a1
                                (a2 fv )))))
        (persistent! @rc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackUrlParams

  "Parse form fields in the Url"

  [^HTTPEvent evt]

  (with-local-vars
    [rc (transient {})]
    (doseq [[k [a1 a2]]  PMS]
      (when (.hasParameter evt k)
        (var-set rc (assoc! @rc
                            a1
                            (a2 (.getParameterValue evt k) )))))
    (persistent! @rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeGetAuthInfo

  "Attempt to parse and get authentication info"

  [^HTTPEvent evt]

  (when-some [ct (.contentType evt) ]
    (cond
      (or (> (.indexOf ct "form-urlencoded") 0)
          (> (.indexOf ct "form-data") 0))
      (crackFormFields evt)

      (> (.indexOf ct "/json") 0)
      (crackBodyContent evt)

      :else
      (crackUrlParams evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeDecodeField ""

  [info fld shiftCount]

  (if (:nonce info)
    (tryletc
      [decr (-> (get info fld)
                (CaesarDecrypt shiftCount))
       bits (Base64/decodeBase64 decr)
       s (Stringify bits) ]
      (log/debug "info = %s" info)
      (log/debug "decr = %s" decr)
      (log/debug "val = %s" s)
      (assoc info fld s))
    info))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAppKey ""

  ^bytes
  [^HTTPEvent evt]

  (-> ^Container (.container (.emitter evt))
      (.getAppKeyBits)))

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
;;EOF

