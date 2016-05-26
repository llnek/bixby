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
    ^{:author "kenl"}

  czlab.skaro.demo.timer.core


  (:require
    [czlab.xlib.process :refer [delayExec]]
    [czlab.xlib.logging :as log]
    [czlab.skaro.core.wfs :refer [simPTask]])

  (:import
    [czlab.wflow.dsl WHandler Job FlowDot PTask]
    [java.util.concurrent.atomic AtomicInteger]
    [java.util Date]
    [czlab.skaro.io TimerEvent]
    [czlab.skaro.server Cocoon]))

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
(defn demo ""

  ^WHandler
  []

  (reify WHandler
    (run [_  j _]
      (let [^TimerEvent ev (.event ^Job j) ]
        (if (.isRepeating ev)
          (println "-----> (" (ncount) ") repeating-update: " (Date.))
          (println "-----> once-only!!: " (Date.)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


