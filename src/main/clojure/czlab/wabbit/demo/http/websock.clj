;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^:no-doc
    ^{:author "Kenneth Leung"}

  czlab.wabbit.demo.http.websock

  (:require [czlab.xlib.process :refer [delayExec]]
            [czlab.xlib.logging :as log]
            [czlab.xlib.meta :refer [instBytes?]])

  (:use [czlab.flux.wflow.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.flux.wflow Job TaskDef]
           [czlab.wabbit.io WSockEvent]
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
       [^WSockEvent ev (.event ^Job %2)
        data (.body ev)
        stuff (when (and (some? data)
                         (.hasContent data))
                (.content data))]
       (cond
         (instance? String stuff)
         (println "Got poked by websocket-text: " stuff)

         (instBytes? stuff)
         (println "Got poked by websocket-bin: len = " (alength ^bytes stuff))

         :else
         (println "Funky data from websocket????")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


