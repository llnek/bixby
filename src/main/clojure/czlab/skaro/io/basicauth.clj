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
      :author "Kenneth Leung" }

  czlab.skaro.io.basicauth

  (:require
    [czlab.xlib.str :refer [embeds? lcase strim hgl?]]
    [czlab.xlib.format :refer [readJson writeJson]]
    [czlab.skaro.io.http :refer [scanBasicAuth]]
    [czlab.crypto.codec :refer [caesarDecrypt]]
    [czlab.netty.util :refer [filterFormFields]]
    [czlab.netty.core :refer :all]
    [czlab.xlib.core
     :refer [normalizeEmail
             when-some+
             cast?
             stringify trylet!]]
    [czlab.xlib.logging :as log])

  (:import
    [java.util Base64 Base64$Decoder]
    [czlab.skaro.server Container]
    [czlab.skaro.io HttpEvent]
    [czlab.xlib XData]
    [czlab.net ULFormItems ULFileItem]))

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

(def ^:private PMS
  {EMAIL_PARAM [ :email #(normalizeEmail %) ]
   CAPTCHA_PARAM [ :captcha #(strim %) ]
   USER_PARAM [ :principal #(strim %) ]
   PWD_PARAM [ :credential #(strim %) ]
   CSRF_PARAM [ :csrf #(strim %) ]
   NONCE_PARAM [ :nonce #(some? %) ] })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackFormFields

  "Parse a standard login-like form with userid,password,email"
  [^HttpEvent evt]

  (when-some
    [itms (some-> evt
                  (.body) (.content))]
    (persistent!
      (reduce
        #(let [fm (.getFieldNameLC ^ULFileItem %2)
               fv (str %2)]
           (log/debug "form-field=%s, value=%s" fm fv)
           (if-some [[k v] (get PMS fm)]
             (assoc! %1 k (v fv))
             %1))
        (transient {})
        (filterFormFields itms)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackBodyContent

  "Parse a JSON body"
  [^HttpEvent evt]

  (let
    [^XData xs (some-> evt (.body))
     json (-> (if (and (some? xs)
                       (.hasContent xs))
                (stringify xs) "{}")
              (readJson #(lcase %)))]
    (persistent!
      (reduce
        #(let [[k [a1 a2]] %2]
           (if-some [fv (get json k)]
            (assoc! %1 a1 (a2 fv ))
            %1))
        (transient {})
        PMS))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackUrlParams

  "Parse form fields in the Url"
  [^HttpEvent evt]

  (let [gist (.msgGist evt)]
    (persistent!
      (reduce
        #(let [[^String k [a1 a2]]  PMS]
           (if (gistParam? gist k)
             (assoc! %1
                     a1
                     (a2 (gistParam gist k)))
             %1))
        (transient {})
        PMS))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn maybeGetAuthInfo

  "Attempt to parse and get authentication info"
  [^HttpEvent evt]

  (let [gist (.msgGist evt)]
    (when-some+ [ct (:contentType gist)]
      (cond
        (or (embeds? ct "form-urlencoded")
            (embeds? ct "form-data"))
        (crackFormFields evt)

        (embeds? ct "/json")
        (crackBodyContent evt)

        :else
        (crackUrlParams evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeDecodeField

  ""
  [info fld shiftCount]

  (if (:nonce info)
    (trylet!
      [decr (->> (get info fld)
                 (caesarDecrypt shiftCount))
       s (->> decr
              (.decode (Base64/getMimeDecoder))
              (stringify))]
      (log/debug "info = %s" info)
      (log/debug "decr = %s" decr)
      (log/debug "val = %s" s)
      (assoc info fld s))
    info))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAppKey

  ""
  ^bytes
  [^HttpEvent evt]

  (-> (.server (.source evt))
      (.appKeyBits)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getSignupInfo

  ""
  [^HttpEvent evt]

  (-> (maybeGetAuthInfo evt)
      (maybeDecodeField :principal CAESAR_SHIFT)
      (maybeDecodeField :credential CAESAR_SHIFT)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLoginInfo

  ""
  [^HttpEvent evt]

  (-> (maybeGetAuthInfo evt)
      (maybeDecodeField :principal CAESAR_SHIFT)
      (maybeDecodeField :credential CAESAR_SHIFT)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


