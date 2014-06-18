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

  cmzlabsclj.tardis.io.basicauth

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [Stringify notnil? ] ])
  (:use [cmzlabsclj.nucleus.util.str :only [strim nsb hgl? ] ])
  (:use [cmzlabsclj.tardis.io.http :only [ScanBasicAuth] ])
  (:use [cmzlabsclj.nucleus.net.comms :only [GetFormFields] ])
  (:import (org.apache.commons.codec.binary Base64))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (com.zotohlab.gallifrey.io HTTPEvent))
  (:import (com.zotohlab.frwk.io XData))
  (:import (com.zotohlab.frwk.net ULFormItems ULFileItem)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^String ^:private PWD_PARAM "credential")
(def ^String ^:private EMAIL_PARAM "email")
(def ^String ^:private USER_PARAM "principal")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackFormFields ""

  [^HTTPEvent evt]

  (when-let [^XData xs (if (.hasData evt) (.data evt) nil) ]
    (with-local-vars [ user nil pwd nil email nil
                       data (.content xs) ]
      (when (instance? ULFormItems @data)
        (doseq [ ^ULFileItem x (GetFormFields @data) ]
          (let [ fm (.getFieldName x)
                 fv (nsb (.getString x)) ]
            ;;(log/debug "Form field: " fm " = " fv)
            (case fm
              EMAIL_PARAM (var-set email fv)
              PWD_PARAM (var-set pwd fv)
              USER_PARAM (var-set user fv)
              nil)))
        { :principal (strim @user) :credential (strim @pwd)  :email (strim @email) }))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackBodyContent ""

  [^HTTPEvent evt]

  (when-let [^XData xs (if (.hasData evt) (.data evt) nil) ]
    (let [ data (if (.hasContent xs) (.stringify xs) "")
           json (json/read-str data) ]
      (when-not (nil? json)
        { :principal (strim (get json USER_PARAM))
          :credential (strim (get json PWD_PARAM))
          :email (strim (get json EMAIL_PARAM)) }))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackUrlParams ""

  [^HTTPEvent evt]

  { :email (strim (.getParameterValue evt EMAIL_PARAM))
    :credential (strim (.getParameterValue evt PWD_PARAM))
    :principal (strim (.getParameterValue evt USER_PARAM)) })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetAuthInfo ""

  [^HTTPEvent evt]

  (let [ ct (.contentType evt) ]
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
(defn GetSignupInfo ""

  [^HTTPEvent evt]

  (let [ info (maybeGetAuthInfo evt) ]
    (if (nil? info)
      nil
      (assoc info :email (:principal info)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginInfo ""

  [^HTTPEvent evt]

  (maybeGetAuthInfo evt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private basicauth-eof nil)

