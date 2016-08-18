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


(ns ^:no-doc
    ^{:author "Kenneth Leung"}

  czlab.skaro.demo.http.formpost

  (:require
    [czlab.xlib.process :refer [delayExec]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core :refer [cast?]]
    [czlab.xlib.str :refer [hgl?]])

  (:use [czlab.wflow.core])

  (:import
    [czlab.skaro.io HttpEvent HttpResult]
    [czlab.wflow Job TaskDef]
    [java.util ListIterator]
    [czlab.xlib XData]
    [czlab.net ULFileItem ULFormItems]
    [czlab.skaro.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demo

  ""
  ^TaskDef
  []

  (script<>
    #(let [^HttpEvent ev (.event ^Job %2)
           ^HttpResult
            res (.resultObj ev)
            data (.body ev)
            stuff (when (and (some? data)
                             (.hasContent data))
                    (.content data)) ]
        (if-some [^ULFormItems
                 fis (cast? ULFormItems stuff)]
          (doseq [^ULFileItem fi (.intern fis)]
            (println "Fieldname : " (.getFieldName fi))
            (println "Name : " (.getName fi))
            (println "Formfield : " (.isFormField fi))
            (if (.isFormField fi)
              (println "Field value: " (.getString fi))
              (when-some [xs (.fileData fi)]
                (println "Field file = " (.filePath xs)))))
          ;;else
          (println "Error: data is not ULFormItems."))
        (.setStatus res 200)
        ;; associate this result with the orignal event
        ;; this will trigger the http response
        (.replyResult ev))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


