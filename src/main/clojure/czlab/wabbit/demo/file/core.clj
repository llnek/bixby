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

(ns ^{:no-doc true
      :author "Kenneth Leung"}

  czlab.wabbit.demo.file.core

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.flux.wflow.core])

  (:import [czlab.wabbit.server Container ServiceProvider Service]
           [java.util.concurrent.atomic AtomicInteger]
           [czlab.flux.wflow Job TaskDef]
           [czlab.wabbit.io FileEvent]
           [java.util Date]
           [java.io File IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private GINT (AtomicInteger.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ncount "" [] (.incrementAndGet ^AtomicInteger GINT))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demoGen
  ""
  ^TaskDef
  []
  (script<>
    #(let [p (-> ^Container
                 (.server ^Job %2)
                 (.service :default-sample))]
       (-> (.getv (.getx p) :targetFolder)
           (io/file (str "ts-" (ncount) ".txt"))
           (spitUtf8 (str "Current time is " (Date.)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demoPick
  ""
  ^TaskDef
  []
  (script<>
    #(let [f (-> ^FileEvent
                 (.event ^Job %2)
                 (.file))]
       (println "picked up new file: " f)
       (println "content: " (slurpUtf8 f)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


