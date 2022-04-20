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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns czlab.bixby.demo.http.formpost

  (:require [czlab.bixby.core :as b]
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


