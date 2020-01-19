;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.demo.http.formpost

  (:require [czlab.blutbad.core :as b]
            [czlab.niou.core :as cc]
            [czlab.basal.core :as c]
            [czlab.niou.upload :as cu])

  (:import [org.apache.commons.fileupload FileItem]
           [java.io File]
           [czlab.basal XData]
           [czlab.niou.upload ULFormItems]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn demo-formpost

  [evt]

  (let [^XData data (:body evt)
        res (cc/http-result evt)
        stuff (some-> data .content)]
    (if (c/is? ULFormItems stuff)
      (doseq [^FileItem fi (cu/get-all-items stuff)]
        (c/prn!! "Fieldname: %s" (.getFieldName fi))
        (c/prn!! "Name: %s" (.getName fi))
        (c/prn!! "Formfield: %s" (.isFormField fi))
        (c/prn!! "Field-size: %s" (.getSize fi))
        (if (.isFormField fi)
          (c/prn!! "Field value: %s" (.getString fi))
          (if-some [^File xs (cu/get-field-file fi)]
            (c/prn!! "Field file = %s"
                     (.getCanonicalPath xs)))))
      (c/prn!! "Error: data is not ULFormItems."))
    ;; associate this result with the orignal event
    ;; this will trigger the http response
    (cc/reply-result res)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


