;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Basic functions for loopable services."
    :author "Kenneth Leung"}

  czlab.wabbit.plugs.loops

  (:require [czlab.wabbit
             [core :as b]
             [xpis :as xp]]
            [czlab.basal
             [dates :as dt]
             [proc :as p]
             [util :as u]
             [io :as i]
             [log :as l]
             [xpis :as po]
             [core :as c :refer [is?]]]
            [czlab.wabbit.plugs.core :as pc])

  (:import [java.util Date Timer TimerTask]))

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
        (xp/gconf plug)]
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
              :source co
              :tstamp (u/system-time)
              :id (str "TimerMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- xxx-timer
  [plug _id spec repeat?]
  (let [impl (atom {:ttask nil
                    :conf (:conf spec)
                    :info (:info spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :$handler]))
      (err-handler [_] (get-in @impl [:conf :$error]))
      (gconf [_] (:conf @impl))
      po/Hierarchical
      (parent [me] plug)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (swap! impl
               update-in
               [:conf]
               #(-> (merge % arg)
                    b/expand-vars
                    b/prevar-cfg)) me)
      po/Finzable
      (finz [me] (po/stop me) me)
      po/Startable
      (stop [me]
        (u/cancel-timer-task! (:ttask @impl)) me)
      (start [_] (po/start _ nil))
      (start [me arg]
        (swap! impl
               assoc
               :ttask
               (cfg-timer (Timer. true)
                          #(pc/dispatch!
                             (evt<> me repeat?))
                          (:conf @impl) repeat?)) me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def RepeatingTimerSpec
  {:info {:name "Repeating Timer"
          :version "1.0.0"}
   :conf {:$pluggable ::RepeatingTimer
          :interval-secs 300
          :delay-secs 0
          :$handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def OnceTimerSpec
  {:info {:name "One Shot Timer"
          :version "1.0.0"}
   :conf {:$pluggable ::OnceTimer
          :delay-secs 0
          :$handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn repeating-timer<>
  ""
  ([_ id spec]
   (xxx-timer _ id spec true))
  ([_ id]
   (repeating-timer<> _ id RepeatingTimerSpec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn once-timer<>
  ""
  ([_ id spec]
   (xxx-timer _ id spec false))
  ([_ id]
   (once-timer<> _ id OnceTimerSpec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

