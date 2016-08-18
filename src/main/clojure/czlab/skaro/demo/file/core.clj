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

  czlab.skaro.demo.file.core

  (:require
    [czlab.xlib.core :refer :all]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.str :refer [hgl?]])

  (:use [czlab.xlib.files]
        [czlab.wflow.core])

  (:import
    [czlab.skaro.server Container ServiceProvider Service]
    [java.util.concurrent.atomic AtomicInteger]
    [czlab.wflow Job TaskDef]
    [czlab.skaro.io FileEvent]
    [java.util Date]
    [java.io File IOException]))

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
(defn demoGen

  ""
  ^TaskDef
  []

  (script<>
    #(let [p (-> ^Container
                 (.server ^Job %2)
                 (.getService :default-sample))]
       (spitUTF8 (io/file (.getv (.getx p) :targetFolder)
                          (str "ts-" (ncount) ".txt"))
                 (str "Current time is " (Date.))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demoPick

  ""
  ^TaskDef
  []

  (script<>
    #(let [f (-> ^FileEvent (.event ^Job %2)
                  (.file)) ]
       (println "picked up new file: " f)
       (println "content: " (slurpUTF f)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


