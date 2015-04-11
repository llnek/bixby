;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.util.wfs

  (:require [clojure.tools.logging :as log :only [warn error info debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.scheduler :only [MakeScheduler]]
        [czlabclj.xlib.util.core :only [MakeMMap]])

  (:import  [com.zotohlab.wflow If FlowNode Activity
             CounterExpr BoolExpr
             ChoiceExpr Job
             Pipeline PDelegate PTask Work]
            [com.zotohlab.frwk.server ServerLike]
            [com.zotohlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PseudoServer ""

  ^ServerLike
  []

  (let [^czlabclj.xlib.util.scheduler.SchedulerAPI
        cpu (MakeScheduler nil)]
    (.activate cpu { :threads 1, :trace false })
    (reify ServerLike
      (hasService [_ s] )
      (getService [_ s] )
      (core [_] cpu))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PseudoJob ""

  ^Job
  [parObj]

  (let [impl (MakeMMap)]
    (reify Job
      (container [_] parObj)
      (event [_] )
      (id [_] "heeloo")
      (unsetv [_ k] (.clrf! impl k))
      (setv [_ k v] (.setf! impl k v))
      (getv [_ k] (.getf impl k))
      (setLastResult [_ v] (.setf! impl :last v))
      (clrLastResult [_] (.clrf! impl :last))
      (getLastResult [_] (.getf impl :last)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(defmacro DefPTask [ & exprs ] `(PTask. (reify Work ~@exprs )))
(defn DefPTask ""

  (^PTask [func] (DefPTask "" func))

  (^PTask [^String nm func]
    (PTask. nm (reify Work
                 (exec [_ fw job]
                   (apply func [fw job]))))))

(defn SimPTask ""

  (^PTask [func] (SimPTask "" func))

  (^PTask [^String nm func]
    (PTask. nm (reify Work (exec [_ fw job] (apply func [job]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(defmacro DefPredicate [ & exprs ] `(reify BoolExpr ~@exprs))
(defn DefBoolExpr

  ^BoolExpr
  [func]

  (reify BoolExpr (evaluate [_ job] (apply func [job]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefChoiceExpr

  ^ChoiceExpr
  [func]

  (reify ChoiceExpr (getChoice [_ job] (apply func [job]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefCounterExpr

  ^CounterExpr
  [func]

  (reify CounterExpr (getCount [_ job] (apply func [job]))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private wfs-eof nil)


