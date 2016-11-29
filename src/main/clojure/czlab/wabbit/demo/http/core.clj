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

  czlab.wabbit.demo.http.core

  (:require
    [czlab.xlib.process :refer [delayExec]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core :refer [try!]]
    [czlab.xlib.str :refer [hgl?]])

  (:use [czlab.wflow.core])

  (:import
    [czlab.wflow Job TaskDef]
    [czlab.wabbit.io HttpEvent HttpResult]
    [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private
  FX
  (str "<?xml version = \"1.0\" encoding = \"utf-8\"?>"
       "<hello xmlns=\"http://simple/\">"
       "<world>"
       "  Holy Batman!"
       "</world>"
       "</hello>"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demo

  ""
  ^TaskDef
  []

  (script<>
    #(let [^HttpEvent ev (.event ^Job %2)
           ^HttpResult
            res (.resultObj ev) ]
        ;; construct a simple html page back to caller
        ;; by wrapping it into a stream data object
        (doto res
          (.setHeader "content-type" "text/xml")
          (.setContent FX)
          (.setStatus 200))
        ;; associate this result with the orignal event
        ;; this will trigger the http response
        (.replyResult ev))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


