;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Basic functions for loopable services."
      :author "Kenneth Leung"}

  czlab.wabbit.io.loops

  (:require [czlab.xlib.dates :refer [parseDate]]
            [czlab.xlib.process :refer [async!]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.io.core])

  (:import [czlab.wabbit.io IoService TimerEvent]
           [java.util Date Timer TimerTask]
           [clojure.lang APersistentMap]
           [czlab.xlib Muble Identifiable Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- timerEvent<>
  ^TimerEvent
  [^IoService co repeat?]
  (let [eeid (str "event#"
                  (seqint2))]
    (reify
      TimerEvent
      (checkAuthenticity [_] false)
      (id [_] eeid)
      (source [_] co)
      (isRepeating [_] repeat?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configRepeat
  "Configure a repeating timer"
  [^Timer tm delays ^long intv func]
  (log/info "Scheduling a repeating timer: %dms" intv)
  (let [tt (tmtask<> func)
        [dw ds] delays]
    (if (spos? intv)
      (cond
        (inst? Date dw)
        (.schedule tm tt ^Date dw intv)
        :else
        (.schedule tm
                   tt
                   (long (if (> ds 0) ds 1000)) intv)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configOnce
  "Configure a *one-time* timer"
  [^Timer tm delays func]
  (log/info "Scheduling a *single-shot* timer at %s" delays)
  (let [tt (tmtask<> func)
        [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule tm tt ^Date dw)
      :else
      ;;wait at least 1 sec
      (.schedule tm
                 tt
                 (long (if (> ds 0) ds 1000))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask
  [^Context co cfg repeat?]
  (let [{:keys [intervalSecs
                delayWhen
                delaySecs]} cfg
        d [delayWhen (s2ms delaySecs)]
        tm (.getv (.getx co) :timer)
        func #(.wakeup ^Loopable co nil)]
    (test-some "java-timer" tm)
    (if (and repeat?
             (spos? intervalSecs))
      (configRepeat tm d (s2ms intervalSecs) func)
      (configOnce tm d func))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer
(defmethod loopableSchedule
  ::ThreadedTimer
  [^IoService co {:keys [intervalMillis]}]

  (log/info "%s timer @interval = %d" (gtid co) intervalMillis)
  (let [loopy (volatile! true)]
    (.setv (.getx co) :loopy loopy)
    (async!
      #(while @loopy
         (loopableWakeup co {:waitMillis intervalMillis}))
      {:cl (getCldr)})
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup
  ::ThreadedTimer
  [^IoService co {:keys [waitMillis]}]

  ;;(log/debug "loopableWakeup %s: %s" (gtid co) (.id co))
  (.dispatch co (ioevent<> co nil))
  (safeWait waitMillis)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start
  ::ThreadedTimer
  [^IoService co]

  (logcomp "io->start" co)
  (let [{:keys [intervalSecs
                delaySecs delayWhen]}
        (.config co)
        func #(loopableSchedule co {:intervalMillis
                                    (s2ms intervalSecs)})]
    (if (or (spos? delaySecs)
            (inst? Date delayWhen))
      (configOnce (Timer.)
                   [delayWhen (s2ms delaySecs)] func)
      (func))
    (io<started> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop
  ::ThreadedTimer
  [^IoService co]

  (logcomp "io->stop" co)
  (if-some [loopy (.getv (.getx co) :loopy)]
    (vreset! loopy false))
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xxxTimer<>
  ""
  [^IOService co repeat?]
  (let [impl (muble<>)]
    (reify
      Loopable
      (schedule [_ arg]
        (configTimerTask this cfg repeat?))
      (wakeup [_ arg]
        (.dispatch co (timerEvent<> co repeat?))
        (if-not repeat? (.stop this)))
      Initable
      (init [_ arg] )
      Startable
      (start [_ arg]
        (.setv impl :timer (Timer. true))
        (.schedule this repeat?))
      (stop [_]
        (let [t (.getv impl :timer)]
          (try! (.cancel ^Timer t))
          (.unsetv impl :timer))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RepeatingTimer
  ""
  [^IoService co]
  (xxxTimer<> co true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnceTimer
  ""
  [^IoService co]
  (xxxTimer<> co false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


