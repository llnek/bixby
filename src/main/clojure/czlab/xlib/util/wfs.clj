;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.util.wfs

  (:require
    [czlab.xlib.util.scheduler :refer [NulScheduler MakeScheduler]]
    [czlab.xlib.util.core :refer [Cast? MakeMMap NextLong]])

  (:require [czlab.xlib.util.logging :as log])

  (:use [czlab.xlib.util.consts])

  (:import
    [com.zotohlab.wflow FlowDot Activity
     CounterExpr BoolExpr Nihil
     ChoiceExpr Job WorkFlow WorkFlowEx
     WHandler FlowError
     PTask Work]
    [com.zotohlab.frwk.server Event ServerLike
     NonEvent NulEmitter
     ServiceHandler]
    [com.zotohlab.frwk.util Schedulable]
    [com.zotohlab.frwk.core Activable Disposable]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewJob

  "Create a new job for downstream processing"

  ^Job
  [^ServerLike par ^WorkFlow wfw & [evt]]

  (let [^Event evt (or evt (NonEvent. par))
        impl (MakeMMap)
        jid (NextLong) ]
    (reify

      Job

      (setLastResult [this v] (.setv impl JS_LAST v))
      (getLastResult [this] (.getv impl JS_LAST))
      (clrLastResult [this] (.unsetv impl JS_LAST))
      (clear [_] (.clear impl))
      (setv [this k v] (.setv impl k v))
      (unsetv [this k] (.unsetv impl k))
      (getv [this k] (.getv impl k))
      (container [_] par)
      (wflow [_] wfw)
      (event [_] evt)
      (id [_] jid))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NihilDot

  "Create a Nihil FlowDot"

  ^FlowDot
  [^Job job]

  (-> (Nihil/apply) (.reify job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefPTask

  "Given a function(arity 2), return a PTask"

  (^PTask
    [func]
    (DefPTask "" func))

  (^PTask
    [^String nm func]
    {:pre [(fn? func)]}
    (PTask. nm
            (reify Work
              (on [_ fw job]
                (func fw job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SimPTask

  "Given a function(arity 1), return a PTask"

  (^PTask
    [func]
    (SimPTask "" func))

  (^PTask
    [^String nm func]
    {:pre [(fn? func)]}
    (PTask. nm (reify Work (on [_ fw job] (func job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefBoolExpr

  "Given a function(arity 1), return a BoolExpr"

  ^BoolExpr
  [func]

  {:pre [(fn? func)]}

  (reify BoolExpr (ptest [_ j] (func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefChoiceExpr

  "Given a function(arity 1), return a ChoiceExpr"

  ^ChoiceExpr
  [func]

  {:pre [(fn? func)]}

  (reify ChoiceExpr (choice [_ j] (func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefCounterExpr

  "Given a function(arity 1), return a CounterExpr"

  ^CounterExpr
  [func]

  {:pre [(fn? func)]}

  (reify CounterExpr (gcount [_ j] (func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WrapPTask

  "Wrap a PTask around this WHandler"

  ^Activity
  [^WHandler wf]

  (SimPTask (fn [j] (.run wf j (object-array 0)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToWorkFlow

  "Coerce argument into a WorkFlow object"

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
      (log/warn "unknown object type %s%s"
                (type arg)
                ", cannot cast to WorkFlow")
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc FlowServer

  "A server that handles WorkFlows"

  ^ServerLike
  [^Schedulable cpu & [options]]

  (let [options (or options {})]
    (-> ^Activable
        cpu
        (.activate options))
    (reify

      ServiceHandler

      (handle [this arg opts]
        (let [w (ToWorkFlow arg)
              j (NewJob this w)
              opts (or opts {})]
          (doseq [[k v] opts]
            (.setv j k v))
          (->> (-> (Nihil/apply)
                   (.reify j))
               (.reify (.startWith w))
               (.run cpu))))

      (handleError [_ e]
        (with-local-vars [ret nil]
          (when-let [^FlowError
                     fe (Cast? FlowError e)]
            (when-let [n (.getLastDot fe)]
              (when-let [w (Cast? WorkFlowEx
                                  (-> (.job n)
                                      (.wflow)))]
                (->> (.onError ^WorkFlowEx
                               w (.getCause fe))
                     (var-set ret)))))
          @ret))

      Disposable

      (dispose [_] (.dispose cpu))

      ServerLike

      (core [_] cpu))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

