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
      :author "kenl" }

  cmzlabclj.tardis.core.wfs

  (:import  [com.zotohlab.wflow If BoolExpr FlowNode Activity
                                ForLoopCountExpr BoolExpr
                                SwitchChoiceExpr
                                Pipeline PipelineDelegate PTask Work]
            [com.zotohlab.gallifrey.io HTTPEvent HTTPResult]
            [com.zotohlab.wflow.core Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(defmacro DefWFTask [ & exprs ] `(PTask. (reify Work ~@exprs )))
(defn DefWFTask ""

  ^PTask
  [func]

  (PTask. (reify Work
            (exec [_ fw job arg]
              (func fw job arg)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(defmacro DefPredicate [ & exprs ] `(reify BoolExpr ~@exprs))
(defn DefBoolExpr

  ^BoolExpr
  [func]

  (reify BoolExpr (evaluate [_ job] (func job))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefChoiceExpr

  ^SwitchChoiceExpr
  [func]

  (reify SwitchChoiceExpr (getChoice [_ job] (func job))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefFLCountExpr

  ^ForLoopCountExpr 
  [func]

  (reify ForLoopCountExpr (getCount [_ job] (func job))
  ))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private wfs-eof nil)


