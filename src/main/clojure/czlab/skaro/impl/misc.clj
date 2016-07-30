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

  czlab.skaro.impl.misc

  (:require
    [czlab.wflow.core :refer :all]
    [czlab.xlib.core :refer [inst? trap!]])

  (:import
    [czlab.wflow TaskDef Job StepError]
    [czlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal flows
(defn- mkWork

  ""
  [s]

  (script<>
    (fn [_ ^Job job]
      (let [^HTTPEvent evt (.event job)
            ^HTTPResult
            res (.getResultObj evt) ]
        (.setStatus res s)
        (.replyResult evt)
        nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkInternalFlow

  ""
  ^TaskDef
  [^Job j s]

  (let [evt (.event j)]
    (if (inst? HTTPEvent evt)
      (mkWork s)
      (trap! StepError
             (format "unhandled event, type=\"%s\""
                     (:typeid (meta evt)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fatalErrorFlow<>

  ^TaskDef
  [^Job job]

  (mkInternalFlow job 500))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn orphanFlow<>

  ""
  ^TaskDef
  [^Job job]

  (mkInternalFlow job 501))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


