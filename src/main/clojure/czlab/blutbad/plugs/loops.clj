;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.plugs.loops

  "Basic functions for loopable services."

  (:require [czlab.blutbad.core :as b]
            [czlab.basal.proc :as p]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.basal.dates :as dt]
            [czlab.basal.core :as c :refer [is?]])

  (:import [java.util Date Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- cfg-repeat

  ^TimerTask
  [^Timer timer [dw ds] ^long intv func]

  (when (c/spos? intv)
    (c/info "scheduling a *repeating* timer: %dms" intv)
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

  (c/info "scheduling a *one-shot* timer at %s" (i/fmt->edn [dw ds]))
  (c/do-with [tt (u/tmtask<> func)]
    (if (is? Date dw)
      (.schedule timer tt ^Date dw)
      (.schedule timer
                 tt
                 (long (if (c/spos? ds) ds 1000))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn cfg-timer

  ^TimerTask
  [timer wakeup {:keys [interval-secs
                        delay-when delay-secs]} repeat?]

  (let [d [delay-when (b/s2ms delay-secs)]]
    (if-not (and repeat?
                 (c/spos? interval-secs))
      (cfg-once timer d wakeup)
      (cfg-repeat timer d (b/s2ms interval-secs) wakeup))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn schedule-threaded-loop

  [{:keys [conf] :as plug} waker]

  (c/do-with [loopy (volatile! true)]
    (let [ms (b/s2ms (:interval-secs conf))]
      (cfg-timer (Timer. true)
                 (c/fn_0 (p/async!
                           #(while @loopy
                              (waker plug)
                              (u/pause ms)) {:daemon? true})) conf false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn stop-threaded-loop!

  [loopy] (vreset! loopy false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TimerMsg []
  c/Idable
  (id [_] (:id _))
  c/Hierarchical
  (parent [_] (:source _)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [co repeat?]

  (c/object<> TimerMsg
              :repeat? repeat?
              :source co
              :tstamp (u/system-time)
              :id (str "TimerMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TimerPlugin [server _id info conf repeat?]
  c/Hierarchical
  (parent [me] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (update-in me
               [:conf]
               #(-> (c/merge+ % arg)
                    b/expand-vars* b/prevar-cfg)))
  c/Finzable
  (finz [me] (c/stop me))
  c/Startable
  (stop [me]
    (u/cancel-timer-task! (:ttask me)) me)
  (start [_]
    (c/start _ nil))
  (start [me arg]
    (assoc me
           :ttask
           (cfg-timer (Timer. true)
                      #(b/dispatch
                         (evt<> me repeat?)) conf repeat?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def RepeatingTimerSpec
  {:info {:name "Repeating Timer"
          :version "1.0.0"}
   :conf {:$pluggable ::repeating-timer<>
          :$error nil
          :$action nil
          :delay-secs 0
          :interval-secs 300}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def OnceTimerSpec
  {:info {:name "One Shot Timer"
          :version "1.0.0"}
   :conf {:$pluggable ::once-timer<>
          :$error nil
          :$action nil
          :delay-secs 0}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn repeating-timer<>

  ([ctr id {:keys [info conf]}]
   (TimerPlugin. ctr id info conf true))

  ([_ id]
   (repeating-timer<> _ id RepeatingTimerSpec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn once-timer<>

  ([_ id]
   (once-timer<> _ id OnceTimerSpec))

  ([ctr id {:keys [info conf]}]
   (TimerPlugin. ctr id info conf false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

