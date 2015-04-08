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

  czlabclj.tardis.core.wfs

  (:require [clojure.tools.logging :as log :only [warn error info debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.scheduler :only [MakeScheduler]]
        [czlabclj.xlib.util.core :only [MakeMMap]])

  (:import  [com.zotohlab.wflow If BoolExpr FlowNode Activity
             ForLoopCountExpr BoolExpr
             SwitchChoiceExpr
             Pipeline PDelegate PTask Work]
            [com.zotohlab.frwk.server ServerLike]
            [com.zotohlab.gallifrey.io HTTPEvent HTTPResult]
            [com.zotohlab.wflow Pipeline PDelegate Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PseudoServer

  ^ServerLike
  []

  (let [^czlabclj.xlib.util.scheduler.SchedulerAPI
        cpu (MakeScheduler nil)]
    (.activate cpu { :threads 1 })
    (reify ServerLike
      (hasService [_ s] )
      (getService [_ s] )
      (core [_] cpu))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PseudoJob

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

  ^PTask
  [func]

  (PTask. (reify Work
            (exec [_ fw job arg]
              (apply func [fw job arg])))
  ))

(defn SimPTask ""

  ^PTask
  [func]

  (PTask. (reify Work
            (exec [_ fw job arg]
              (apply func [job])))
  ))

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

  ^SwitchChoiceExpr
  [func]

  (reify SwitchChoiceExpr (getChoice [_ job] (apply func [job]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefFLCountExpr

  ^ForLoopCountExpr
  [func]

  (reify ForLoopCountExpr (getCount [_ job] (apply func [job]))
  ))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private wfs-eof nil)


