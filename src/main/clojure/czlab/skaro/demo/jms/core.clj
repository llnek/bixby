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

  czlab.skaro.demo.jms.core

  (:require
    [czlab.xlib.logging :as log]
    [czlab.xlib.process :refer [delayExec]])

  (:use [czlab.wflow.core])

  (:import
    [java.util.concurrent.atomic AtomicInteger]
    [czlab.wflow Job TaskDef]
    [czlab.skaro.io JmsEvent]
    [javax.jms TextMessage]
    [czlab.skaro.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(let [ctr (AtomicInteger.)]
  (defn- ncount ""
    []
    (.incrementAndGet ctr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demo

  ""
  ^TaskDef
  []

  (script<>
    #(let [^JmsEvent ev (.event ^Job %2)
            ^TextMessage msg (.message ev)]
        (println "-> Correlation ID= " (.getJMSCorrelationID msg))
        (println "-> Msg ID= " (.getJMSMessageID msg))
        (println "-> Type= " (.getJMSType msg))
        (println "("
                 (ncount)
                 ") -> Message= "
                 (.getText msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


