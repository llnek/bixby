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


(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.core.wfs

  (:require
    [czlab.xlib.scheduler :refer [mkScheduler]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [cast? do->nil mubleObj! nextLong]])

  (:use [czlab.xlib.consts])

  (:import
    [czlab.wflow FlowDot
     Activity
     CounterExpr
     BoolExpr
     Nihil
     ChoiceExpr
     Job
     WorkFlow
     WorkFlowEx
     WHandler
     FlowError
     PTask
     Work]
    [czlab.wflow.server Event
     ServerLike
     NonEvent
     NulEmitter
     ServiceHandler]
    [czlab.xlib Schedulable
     Debuggable
     Muble
     Activable
     Disposable]
    [czlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn newJob

  "Create a new job for downstream processing"

  ^Job
  [^ServerLike par & [evt]]

  (let [^Event evt (or evt (NonEvent. par))
        impl (mubleObj!)
        jid (nextLong) ]
    (reify

      Debuggable
      (dbgStr [_] (.toEDN impl))
      (dbgShow [me out] (->> (.dbgStr me)
                             (.println out)))

      Job

      (setLastResult [_ v] (.setv impl JS_LAST v))
      (getLastResult [_] (.getv impl JS_LAST))
      (clrLastResult [_] (.unsetv impl JS_LAST))
      (wflow [_] (.getv impl :wflow))
      (clear [_] (.clear impl))
      (setv [_ k v] (.setv impl k v))
      (unsetv [_ k] (.unsetv impl k))
      (getv [_ k] (.getv impl k))
      (container [_] par)
      (event [_] evt)
      (id [_] jid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mkNihilDot

  "Create a Nihil FlowDot"

  ^FlowDot
  [^Job job]

  (-> (Nihil/apply) (.reify job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn defPTask

  "Given a function(arity 2), return a PTask"

  (^PTask
    [func]
    (defPTask "" func))

  (^PTask
    [^String nm func]
    {:pre [(fn? func)]}
    (PTask. nm
            (reify Work
              (on [_ fw job]
                (func fw job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn simPTask

  "Given a function(arity 1), return a PTask"

  (^PTask
    [func]
    (simPTask "" func))

  (^PTask
    [^String nm func]
    {:pre [(fn? func)]}
    (PTask. nm (reify Work (on [_ fw job] (func job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn defBoolExpr

  "Given a function(arity 1), return a BoolExpr"

  ^BoolExpr
  [func]

  {:pre [(fn? func)]}

  (reify BoolExpr (ptest [_ j] (func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn defChoiceExpr

  "Given a function(arity 1), return a ChoiceExpr"

  ^ChoiceExpr
  [func]

  {:pre [(fn? func)]}

  (reify ChoiceExpr (choice [_ j] (func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn defCounterExpr

  "Given a function(arity 1), return a CounterExpr"

  ^CounterExpr
  [func]

  {:pre [(fn? func)]}

  (reify CounterExpr (gcount [_ j] (func j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn wrapPTask

  "Wrap a PTask around this WHandler"

  ^Activity
  [^WHandler wf]

  (simPTask (fn [j] (.run wf j (object-array 0)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn toWorkFlow

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
      (startWith [_] (simPTask arg)))

    :else
    (do->nil
      (log/warn "unknown object type %s%s"
                (type arg)
                ", cannot cast to WorkFlow"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc flowServer

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
        (let [w (toWorkFlow arg)
              j (newJob this)
              opts (or opts {})]
          (doseq [[k v] opts]
            (.setv j k v))
          (.setv j :wflow w)
          (->> (-> (Nihil/apply)
                   (.reify j))
               (.reify (.startWith w))
               (.run cpu))))

      (handleError [_ e]
        (with-local-vars [ret nil]
          (when-some [^FlowError
                     fe (cast? FlowError e)]
            (when-some [n (.getLastDot fe)]
              (when-some [w (cast? WorkFlowEx
                                  (-> (.job n)
                                      (.wflow)))]
                (->> (.onError ^WorkFlowEx
                               w (.getCause fe))
                     (var-set ret)))))
          @ret))

      Disposable

      (dispose [_] (.dispose cpu))

      ServerLike

      (core [_] cpu))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


