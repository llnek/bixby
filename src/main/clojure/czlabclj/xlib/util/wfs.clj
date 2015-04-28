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
        [czlabclj.xlib.util.consts]
        [czlabclj.xlib.util.core
         :only [MubleAPI MakeMMap NextLong]])

  (:import  [com.zotohlab.wflow If FlowNode Activity
             CounterExpr BoolExpr Nihil
             ChoiceExpr Job
             PTask Work]
            [com.zotohlab.frwk.server Event ServerLike
             ServiceHandler]
            [com.zotohlab.frwk.util Schedulable]
            [com.zotohlab.frwk.core Disposable]
            [com.zotohlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FlowServer ""

  ^ServerLike
  []

  (let [cpu (MakeScheduler nil)]
    (-> ^czlabclj.xlib.util.scheduler.SchedulerAPI
        cpu
        (.activate { :threads 1, :trace false }))
    (reify

      ServiceHandler

      (handleError [_ e] )
      (handle [_  arg]
        (let [^Activity a (:activity arg)
              ^Job j (:job arg)]
          (.run cpu (.reify a
                            (-> (Nihil/apply)
                                (.reify j))))))

      Disposable
      (dispose [_] (.dispose cpu))

      ServerLike

      (hasService [_ s] )
      (getService [_ s] )
      (core [_] cpu))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeJob ""

  (^Job [parObj] (MakeJob parObj nil))

  (^Job [^ServerLike parObj
         ^Event evt]
    (let [impl (MakeMMap)
          jid (NextLong) ]
      (reify

        MubleAPI

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
        (finz [_]
          (log/debug "Job##" jid " has been served.")
          (when (and (.getf impl JS_FLATLINE)
                     (instance? Disposable parObj))
            (-> ^Disposable parObj
                (.dispose))))
        (setv [this k v] (.setf! this k v))
        (unsetv [this k] (.clrf! this k))
        (getv [this k] (.getf this k))
        (handleError [_ ex] nil)
        (container [_] parObj)
        (event [_] evt)
        (id [_] jid)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro DefMorphable ""

  [typesym morphFunc]

  `(deftype ~typesym [] com.zotohlab.frwk.core.Morphable
     (morph [_]
       (require '~(ns-name *ns*))
       (~morphFunc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DefPTask ""

  (^PTask [func] (DefPTask "" func))

  (^PTask [^String nm func]
    (PTask. nm (reify Work
                 (exec [_ fw job]
                   (apply func [fw job]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SimPTask ""

  (^PTask [func] (SimPTask "" func))

  (^PTask [^String nm func]
    (PTask. nm (reify Work (exec [_ fw job] (apply func [job]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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


