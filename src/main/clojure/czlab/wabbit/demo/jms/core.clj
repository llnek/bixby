;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^:no-doc
    ^{:author "Kenneth Leung"}

  czlab.wabbit.demo.jms.core

  (:require [czlab.xlib.logging :as log]
            [czlab.xlib.process :refer [delayExec]])

  (:use [czlab.flux.wflow.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.flux.wflow Job TaskDef]
           [czlab.wabbit.io JmsEvent]
           [javax.jms TextMessage]
           [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^AtomicInteger gint (AtomicInteger.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ncount "" [] (.incrementAndGet gint))

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
                (ncount) ") -> Message= " (.getText msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


