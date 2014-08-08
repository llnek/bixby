;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{:doc ""
      :author "kenl"}

  cmzlabclj.tardis.demo.fork.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [Try!] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.tardis.core.sys :only [DefWFTask]])


  (:import  [com.zotohlab.wflow FlowNode PTask Split
                               PipelineDelegate]
            [java.util StringBuilder]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow.core Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fib ""

  [n]

  (if (< n 3)
    1
    (+ (fib (- n 2))
       (fib (- n 1)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;   parent(s1) --> split&nowait
;;                  |-------------> child(s1)----> split&wait --> grand-child
;;                  |                              |                    |
;;                  |                              |<-------------------+
;;                  |                              |---> child(s2) -------> end
;;                  |
;;                  |-------> parent(s2)----> end


(deftype Demo [] PipelineDelegate

    ;; split but no wait
    ;; parent continues;

  (getStartActivity [_ pipe]
    (require 'cmzlabclj.tardis.demo.fork.core)
    (let [a1 (DefWFTask
               (fn [cur job arg]
                 (println "I am the *Parent*")
                 (println "I am programmed to fork off a parallel child process, "
                          "and continue my business.")
                 nil))
          a2 (Split/fork (DefWFTask
                           (fn [cur job arg]
                             (println "*Child*: will create my own child (blocking)")
                             (.setv job "rhs" 60)
                             (.setv job "lhs" 5)
                             (-> (Split/applyAnd
                                   (DefWFTask
                                     (fn [cur job arg]
                                       (println "*Child*: the result for (5 * 60) according to "
                                                "my own child is = "
                                                (.getv job "result"))
                                       (println "*Child*: done.")
                                       nil)))
                                 (.include
                                   (DefWFTask
                                     (fn [cur job arg]
                                       (println "*Child->child*: taking some time to do "
                                                "this task... ( ~ 6secs)")
                                       (dotimes [n 7]
                                         (Thread/sleep 1000)
                                         (print "..."))
                                       (println "")
                                       (println "*Child->child*: returning result back to *Child*.")
                                       (.setv job "result"
                                              (* (.getv job "rhs")
                                                 (.getv job "lhs")))
                                       (println "*Child->child*: done.")
                                       nil))))))) ]
      (-> (.chain a1 a2)
          (.chain (DefWFTask
                    (fn [cur job arg]
                      (let [b (StringBuilder. "*Parent*: ")]
                        (println "*Parent*: after fork, continue to calculate fib(6)...")
                        (dotimes [n 7]
                          (.append b (str (fib i) " "))A)
                        (println b "\n" "*Parent*: done.")
                        nil)))))))

  (onStop [_ p] )
  (onError [_ e c] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoMain [] cmzlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ c])

  (initialize [_]
    (println "Demo fork(split)/join of tasks..." ))

  (configure [_ cfg] )

  (start [_])
  (stop [_])

  (dispose [_]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

