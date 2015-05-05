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

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.scheduler
         :only [NulScheduler MakeScheduler]]
        [czlabclj.xlib.util.consts]
        [czlabclj.xlib.util.core
         :only [Cast? Muble MakeMMap NextLong]])

  (:import  [com.zotohlab.wflow If FlowNode Activity
             CounterExpr BoolExpr Nihil
             ChoiceExpr Job WorkFlow WorkFlowEx
             WHandler FlowError
             PTask Work]
            [com.zotohlab.frwk.server Event ServerLike
             NonEvent NulEmitter
             ServiceHandler]
            [com.zotohlab.frwk.util Schedulable]
            [com.zotohlab.frwk.core Activable Disposable]
            [com.zotohlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewJob ""

  (^Job [^ServerLike parObj ^WorkFlow wf]
        (NewJob parObj wf (NonEvent. parObj)))

  (^Job [^ServerLike parObj
         ^WorkFlow wfw
         ^Event evt]
    (let [impl (MakeMMap)
          jid (NextLong) ]
      (reify

        Muble

        (setf! [_ k v] (.setf! impl k v))
        (clear! [_] (.clear! impl))
        (seq* [_] (.seq* impl))
        (toEDN [_] (.toEDN impl))
        (getf [_ k] (.getf impl k))
        (clrf! [_ k] (.clrf! impl k))

        Job

        (setLastResult [this v] (.setf! this JS_LAST v))
        (getLastResult [this] (.getf this JS_LAST))
        (clrLastResult [this] (.clrf! this JS_LAST))
        (finz [_] )
          ;;(log/debug "Job##" jid " has been served.")
        (setv [this k v] (.setf! this k v))
        (unsetv [this k] (.clrf! this k))
        (getv [this k] (.getf this k))
        (container [_] parObj)
        (wflow [_] wfw)
        (event [_] evt)
        (id [_] jid)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefPTask ""

  (^PTask [func] (DefPTask "" func))

  (^PTask [^String nm func]
    (PTask. nm (reify Work
                 (on [_ fw job]
                   (apply func fw job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SimPTask ""

  (^PTask [func] (SimPTask "" func))

  (^PTask [^String nm func]
    (PTask. nm (reify Work (on [_ fw job] (apply func job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefBoolExpr

  ^BoolExpr
  [func]

  (reify BoolExpr (ptest [_ j] (apply func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefChoiceExpr

  ^ChoiceExpr
  [func]

  (reify ChoiceExpr (choice [_ j] (apply func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefCounterExpr

  ^CounterExpr
  [func]

  (reify CounterExpr (gcount [_ j] (apply func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WrapPTask ""

  ^Activity
  [^WHandler wf]

  (SimPTask (fn [^Job j] (.run wf j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToWorkFlow

  ^WorkFlow
  [arg]

  (cond
    (instance? WHandler arg)
    (reify WorkFlow
      (startWith [_] (WrapPTask arg)))

    (instance? WorkFlow arg)
    arg

    (instance? Work arg)
    (reify WorkFlow
      (startWith [_] (PTask/apply ^Work arg)))

    (instance? Activity arg)
    (reify WorkFlow
      (startWith [_] arg))

    (fn? arg)
    (reify WorkFlow
      (startWith [_] (SimPTask arg)))

    :else
    (do
      (log/warn "unknown object type "
                (type arg) ", cannot cast to WorkFlow.")
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FlowServer ""

  ^ServerLike
  [^Schedulable cpu options]

  (let [options (or options {})]
    (-> ^Activable cpu
        (.activate {:threads 1
                    :trace false }))
    (reify

      ServiceHandler

      (handle [this arg opts]
        (let [w (ToWorkFlow arg)
              j (NewJob this w)
              opts (or opts {})]
          (doseq [[k v] (seq opts)]
            (.setv j k v))
          (.run cpu (.reify (.startWith w)
                            (-> (Nihil/apply)
                                (.reify j))))))

      (handleError [_ e]
        (with-local-vars [done false]
          (when-let [^FlowError fe (Cast? FlowError e)]
          (when-let [n (.getLastNode fe)]
          (when-let [w (Cast? WorkFlowEx (-> (.job n)(.wflow)))]
            (var-set done true)
            (.onError ^WorkFlowEx w (.getCause fe)))))
          (when-not @done nil)))

      Disposable

      (dispose [_] (.dispose cpu))

      ServerLike

      (core [_] cpu))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private wfs-eof nil)

