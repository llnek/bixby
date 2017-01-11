;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:no-doc true
    :author "Kenneth Leung"}

  czlab.wabbit.demo.http.formpost

  (:require [czlab.xlib.process :refer [delayExec]]
            [czlab.xlib.logging :as log])

  (:use [czlab.flux.wflow.core]
        [czlab.convoy.net.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.convoy.net HttpResult ULFileItem ULFormItems]
           [czlab.flux.wflow Job TaskDef]
           [czlab.wabbit.io HttpEvent]
           [java.util ListIterator]
           [czlab.xlib XData]
           [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demo
  ""
  ^TaskDef
  []
  (script<>
    #(let
       [^HttpEvent ev (.event ^Job %2)
        res (httpResult<> (.socket ev)(.msgGist ev))
        data (.body ev)
        stuff (when (and (some? data)
                         (.hasContent data))
                (.content data))]
       (if-some [^ULFormItems
                 fis (cast? ULFormItems stuff)]
         (doseq [^ULFileItem fi (.intern fis)]
           (println "Fieldname : " (.getFieldName fi))
           (println "Name : " (.getName fi))
           (println "Formfield : " (.isFormField fi))
           (if (.isFormField fi)
             (println "Field value: " (.getString fi))
             (if-some [xs (.getFile fi)]
               (println "Field file = "
                        (.getCanonicalPath xs)))))
         ;;else
         (println "Error: data is not ULFormItems."))
       ;; associate this result with the orignal event
       ;; this will trigger the http response
       (replyResult (.socket ev) res))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


