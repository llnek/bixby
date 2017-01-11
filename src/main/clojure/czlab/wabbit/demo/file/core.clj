;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

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
(def ^:private gint (AtomicInteger.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ncount "" [] (.incrementAndGet ^AtomicInteger gint))

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


