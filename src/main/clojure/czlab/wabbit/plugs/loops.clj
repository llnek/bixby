;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Basic functions for loopable services."
      :author "Kenneth Leung"}

  czlab.wabbit.plugs.loops

  (:require [czlab.basal.dates :as dt]
            [czlab.basal.proc :as p]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.log :as l]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.core :as c :refer [is?]]
            [czlab.basal.str :as s]
            [czlab.basal.proto :as po]
            [czlab.wabbit.plugs.core :as pc])

  (:import [java.util Date Timer TimerTask]
           [clojure.lang APersistentMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- cfg-repeat
  ^TimerTask
  [^Timer timer [dw ds] ^long intv func]

  (when (c/spos? intv)
    (l/info "scheduling a *repeating* timer: %dms" intv)
    (c/do-with [tt (u/tmtask<> func)]
      (if (is? Date dw)
        (.schedule timer tt ^Date dw intv)
        (.schedule timer
                   tt
                   (long (if (c/spos? ds) ds 1000)) intv)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- cfg-once
  ^TimerTask
  [^Timer timer [dw ds] func]

  (l/info "scheduling a *one-shot* timer at %s" (i/fmt->edn [dw ds]))
  (c/do-with [tt (u/tmtask<> func)]
    (if (is? Date dw)
      (.schedule timer tt ^Date dw)
      (.schedule timer
                 tt
                 (long (if (c/spos? ds) ds 1000))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn cfg-timer
  "" ^TimerTask
  [timer wakeup {:keys [interval-secs
                        delay-when delay-secs]} repeat?]
  (let [d [delay-when (pc/s2ms delay-secs)]]
    (if-not (and repeat?
                 (c/spos? interval-secs))
      (cfg-once timer d wakeup)
      (cfg-repeat timer d (pc/s2ms interval-secs) wakeup))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn schedule-threaded-loop
  "" [plug waker]
  (let [{:keys [interval-secs] :as cfg}
        (xp/get-conf plug)]
    (c/do-with
      [loopy (volatile! true)]
      (let [ms (pc/s2ms interval-secs)
            w (c/fn_0 (p/async!
                        #(while @loopy
                           (waker plug) (u/pause ms))))]
        (cfg-timer (Timer. true) w cfg false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn stop-threaded-loop!
  "" [loopy] (vreset! loopy false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TimerMsg []
  po/Idable
  (id [_] (:id _))
  xp/PlugletMsg
  (get-pluglet [_] (:source _)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>
  [co repeat?]
  (c/object<> TimerMsg
              :id (str "TimerMsg#" (u/seqint2))
              :source co
              :tstamp (u/system-time)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- xxx-timer
  [plug _id spec repeat?]
  (let [impl (atom {:ttask nil
                    :conf (:conf spec)
                    :info (:info spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :handler]))
      (get-conf [_] (:conf @impl))
      (err-handler [_]
        (or (get-in @impl
                    [:conf :error]) (:error spec)))
      po/Hierarchical
      (parent [me] plug)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (swap! impl
               (c/fn_1 (update-in ____1
                                  [:conf]
                                  #(b/prevar-cfg (merge % arg))))))
      po/Finzable
      (finz [me] (po/stop me))
      po/Startable
      (stop [me]
        (u/cancel-timer-task! (:ttask @impl)))
      (start [_] (po/start _ nil))
      (start [me arg]
        (swap! impl
               (c/fn_1 (assoc ____1
                              :ttask
                              (cfg-timer (Timer. true)
                                         #(pc/dispatch!
                                            (evt<> me repeat?))
                                         (:conf @impl) repeat?))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def RepeatingTimerSpec {:info {:name "Repeating Timer"
                                :version "1.0.0"}
                         :conf {:$pluggable ::RepeatingTimer
                                :interval-secs 300
                                :delay-secs 0
                                :handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def OnceTimerSpec {:info {:name "One Shot Timer"
                           :version "1.0.0"}
                    :conf {:$pluggable ::OnceTimer
                           :delay-secs 0
                           :handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn repeating-timer<>
  ""
  ([_ id]
   (repeating-timer<> _ id RepeatingTimerSpec))
  ([_ id spec]
   (xxx-timer _ id (update-in spec [:conf] b/expand-vars-in-form) true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn once-timer<>
  ""
  ([_ id]
   (once-timer<> _ id OnceTimerSpec))
  ([_ id spec]
   (xxx-timer _ id (update-in spec [:conf] b/expand-vars-in-form) false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

