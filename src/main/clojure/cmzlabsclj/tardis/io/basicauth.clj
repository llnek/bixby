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
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.util.core :only [notnil? ] ])
  (:use [cmzlabsclj.util.str :only [nsb hgl? ] ])
  (:use [cmzlabsclj.tardis.io.http :only [ScanBasicAuth] ])
  (:use [cmzlabsclj.net.comms :only [GetFormFields] ])
  (:import (com.zotohlabs.gallifrey.io HTTPEvent))
  (:import (com.zotohlabs.frwk.net ULFormItems ULFileItem)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^String ^:private PWD_PARAM "password")
(def ^String ^:private EMAIL_PARAM "email")
(def ^String ^:private USER_PARAM "user")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetSignupInfo ""

  [^HTTPEvent evt]

  (with-local-vars [ user nil pwd nil email nil ]
    (if-let [ data (.data evt) ]
      (if (instance? ULFormItems data)
        (doseq [ ^ULFileItem
                 x (GetFormFields data) ]
          (let [ fm (.getFieldName x)
                 fv (.getString x)]
            (log/debug "Form field: " fm " = " fv)
            (case fm
              EMAIL_PARAM (var-set email fv)
              PWD_PARAM (var-set pwd fv)
              USER_PARAM (var-set user fv)
              nil)))
        (do
          (var-set email (.getParameterValue evt EMAIL_PARAM))
          (var-set pwd (.getParameterValue evt PWD_PARAM))
          (var-set user (.getParameterValue evt USER_PARAM))) ))

    { :principal @user :credential @pwd  :email @email }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginInfo ""

  [^HTTPEvent evt]

  (with-local-vars [user nil pwd nil]
    (let [ ba (ScanBasicAuth evt)
           data (.data evt) ]
      (if (notnil? ba)
        (do
          (var-set user (first ba))
          (var-set pwd (last ba)))
        (if (instance? ULFormItems data)
          (doseq [ ^ULFileItem
                   x (GetFormFields data) ]
            (let [ fm (.getFieldName x)
                   fv (.getString x) ]
              (log/debug "Form field: " fm " = " fv)
              (case fm
                USER_PARAM (var-set user fv)
                PWD_PARAM (var-set pwd fv)
                nil)))
          (do
            (var-set user (.getParameterValue evt USER_PARAM))
            (var-set pwd (.getParameterValue evt PWD_PARAM))))))
    { :principal @user :credential @pwd }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private basicauth-eof nil)

