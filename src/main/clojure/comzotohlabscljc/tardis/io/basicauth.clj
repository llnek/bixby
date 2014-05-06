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

  comzotohlabscljc.tardis.io.basicauth

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only
                                    [MubleAPI ConvLong notnil?  MakeMMap Bytesify] ])
  (:use [comzotohlabscljc.crypto.core :only [GenMac] ])
  (:use [comzotohlabscljc.util.str :only [nsb hgl? AddDelim!] ])
  (:use [comzotohlabscljc.util.guids :only [NewUUid] ])
  (:use [comzotohlabscljc.tardis.io.http :only [ScanBasicAuth] ])
  (:use [comzotohlabscljc.tardis.io.webss])
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
(defn GetSignupInfo ""

  [^HTTPEvent evt]

  (let [ data (.data evt) ]
    (with-local-vars [user nil pwd nil email nil]
      (cond
        (instance? ULFormItems data)
        (doseq [ ^ULFileItem x (GetFormFields data) ]
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
(defn GetLoginInfo ""

  [^HTTPEvent evt]

  (let [ ba (ScanBasicAuth evt)
         data (.data evt) ]
    (with-local-vars [user nil pwd nil]
      (cond
        (instance? ULFormItems data)
        (doseq [ ^ULFileItem x (GetFormFields data) ]
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
(def ^:private basicauth-eof nil)

