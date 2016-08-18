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

  czlab.skaro.demo.http.websock

  (:require
    [czlab.xlib.process :refer [delayExec]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core :refer :all]
    [czlab.xlib.str :refer :all]
    [czlab.xlib.meta :refer [isBytes?]])

  (:use [czlab.wflow.core])

  (:import
    [czlab.skaro.io WebSockEvent WebSockResult]
    [czlab.wflow Job TaskDef]
    [czlab.xlib XData]
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
    #(let [^WebSockEvent ev (.event ^Job %2)
            res (.resultObj ev)
            data (.body ev)
            stuff (when (and (some? data)
                             (.hasContent data))
                    (.content data)) ]
        (cond
          (instance? String stuff)
          (println "Got poked by websocket-text: " stuff)

          (isBytes? (class stuff))
          (println "Got poked by websocket-bin: len = " (alength ^bytes stuff))

          :else
          (println "Funky data from websocket????")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


