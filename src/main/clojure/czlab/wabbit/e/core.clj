;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Core functions for all IO services."
      :author "Kenneth Leung"}

  czlab.wabbit.io.core

  (:require [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.flux.wflow.core])

  (:import [czlab.wabbit.io IoTrigger IoService IoEvent]
           [czlab.flux.wflow WorkStream Job TaskDef]
           [java.util Timer TimerTask]
           [czlab.wabbit.server
            Cljshim
            Container]
           [czlab.xlib
            Context
            XData
            Versioned
            Hierarchial
            Muble
            Disposable
            Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti processOrphan
  "Handle unhandled events"
  {:tag WorkStream} (fn [j] (class (.event ^Job j))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod processOrphan
  IoEvent
  [_]
  (workStream<>
    (script<>
      #(let [^Job job %2
             evt (.event job)]
         (log/error "event '%s' {job:#s} dropped"
                    (:typeid (meta evt)) (.id job))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro s2ms
  "Convert seconds to milliseconds"
  {:no-doc true}
  [s]
  `(let [t# ~s] (if (spos?  t#) (* 1000 t#) 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->dispose
  "Dispose a io-service" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->start
  "Start a io-service" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->stop
  "Stop a io-service" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->error!
  "Handle io-service error" (fn [a b c] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioevent<>
  "Create an event" (fn [a args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  :czlab.wabbit.io.core/Service
  [^IoService co args]
  (logcomp "comp->init" co)
  (if (and (not-empty args)
           (map? args))
    (->> (merge (.config co) args)
         (.setv (.getx co) :emcfg )))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn io<started>
  ""
  [^IoService co]
  (log/info "service '%s' config:" (.id co))
  (log/info "%s" (pr-str (.config co)))
  (log/info "service '%s' started - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn io<stopped>
  ""
  [^IoService co]
  (log/info "service '%s' stopped - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->dispose
  :czlab.wabbit.io.core/Service
  [^IoService co]
  (log/info "service '%s' disposed - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->error!
  :czlab.wabbit.io.core/Service
  [co ^Job job ^Throwable e]
  (log/exception e)
  (some-> (processOrphan job)
          (.execWith job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- job<+>
  ""
  ^Job
  [co evt]
  (with-meta (job<> co nil evt)
             {:typeid :czlab.wabbit.io.core/Job}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent
  ""
  [^IoService src evt args]
  (log/debug "service '%s' onevent called" (.id src))
  (let
    [cfg (.config src)
     c1 (:router args)
     c0 (:handler cfg)
     ctr (.server src)
     rts (.cljrt ctr)
     cb (stror c1 c0)
     job (job<+> ctr evt)
     wf (try! (.call rts cb))]
    (log/debug (str "event type = %s\n"
                    "event opts = %s\n"
                    "event router = %s\n"
                    "io-handler = %s")
               (:typeid (meta src)) args c1 c0)
    (try
      (log/debug "job#%s => %s" (.id job) (.id src))
      (.setv job evt-opts args)
      (cond
        (inst? WorkStream wf)
        (do->nil
          (.execWith ^WorkStream wf job))
        (fn? wf)
        (do->nil
          (wf job))
        :else
        (throwBadArg "Want WorkStream, got %s" (class wf)))
      (catch Throwable _
        (io->error! src job _)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn service<>
  "Create a IO/Service"
  ^IoService
  [^Container parObj emAlias
   emType {:keys [info conf] :as spec}]
  (let [impl (-> (.cljrt parObj)
                 (.callEx (strKW emType)
                          (vargs* Object spec)))
        cfgdata (atom {})
        timer (atom nil)]
    (with-meta
      (reify IoService

        (isEnabled [_]
          (not (false? (:enabled? @cfgdata))))

        (config [_] @cfgdata)

        (server [this] (.parent this))

        (dispatch [this ev]
          (.dispatchEx this ev nil))

        (dispatchEx [this ev arg]
          (try! (onEvent this ev arg)))

        (hold [_ trig millis]
          (if (and (some? @timer)
                   (spos? millis))
            (let [k (tmtask<>
                      #(.fire trig nil))]
              (.schedule ^Timer @timer k millis)
              (.setTrigger trig k))))

        (version [_] (str (:version info)))
        (getx [_]
          (throwUOE "can't getx"))
        (id [_] emAlias)

        (parent [_] parObj)
        (setParent [_ p]
          (throwUOE "can't setParent"))

        (dispose [this]
          (some-> ^Timer @timer (.cancel))
          (reset! timer nil)
          ((or (:dispose impl)
               (constantly nil))))

        (init [this cfg0]
          (let [cfg (merge conf cfg0)
                cfg ((or (:init impl)
                         (constantly cfg)) cfg0)]
            (reset! cfgdata cfg)))

        (start [this _]
          (reset! timer (Timer. true))
          (log/info "")
          ((or (:start impl)
               (constantly nil)) _)
          (log/info ""))

        (stop [this]
          (some-> ^Timer @timer (.cancel ))
          (reset! timer nil)
          (log/info "")
          ((or (:stop impl)
               (constantly nil)) _)
          (log/info "")))

      {:typeid emType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


