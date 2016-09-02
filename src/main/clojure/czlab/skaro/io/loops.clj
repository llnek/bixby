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

(ns ^{:doc "Basic functions for loopable services."
      :author "Kenneth Leung" }

  czlab.skaro.io.loops

  (:require
    [czlab.xlib.process :refer [async! safeWait]]
    [czlab.xlib.dates :refer [parseDate]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.core
     :refer [test-nonil
             throwUOE
             tmtask<>
             inst?
             spos?
             try!!
             try!
             seqint2]])

  (:use [czlab.skaro.sys.core]
        [czlab.skaro.io.core])

  (:import
    [java.util Date Timer TimerTask]
    [clojure.lang APersistentMap]
    [czlab.skaro.server Service]
    [czlab.skaro.io TimerEvent]
    [czlab.xlib Muble Identifiable Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;; service hierarchy
(derive ::RepeatingTimer :czlab.skaro.io.core/Service)
(derive ::OnceTimer :czlab.skaro.io.core/Service)
(derive ::ThreadedTimer ::RepeatingTimer)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These 2 fn(s) together dictates how a loopable shall run
(defmulti loopableSchedule "" meta???)
(defmulti loopableWakeup "" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkEventObj

  ^TimerEvent
  [^Service co repeat?]

  (log/debug "ioevent: %s: %s" (gtid co) (.id co))
  (let [eeid (seqint2)]
    (with-meta
      (reify
        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (session [_] nil)
        (id [_] eeid)
        (source [_] co)
        (isRepeating [_] repeat?))

      {:typeid ::TimerEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configRepeat

  "Configure a repeating timer"
  [^Timer tm delays ^long intv func]

  (log/debug "Scheduling a repeating timer: %dms" intv)
  (let [tt (tmtask<> func)
        [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule tm tt ^Date dw intv)
      (spos? ds)
      (.schedule tm tt ^long ds intv)
      :else nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configOnce

  "Configure a *one-time* timer"
  [^Timer tm delays func]

  (log/debug "Scheduling a *single-shot* timer")
  (let [ tt (tmtask<> func)
        [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule tm tt ^Date dw)
      (spos? ds)
      (.schedule tm tt ^long ds)
      :else nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask

  [^Service co & [repeat?]]

  (let [tm (.getv (.getx co) :timer)
        {:keys [intervalSecs
                delayWhen
                delaySecs]}
        (.config co)
        d [delayWhen (s2ms delaySecs)]
        func #(loopableWakeup co)]
    (test-nonil "java-timer" tm)
    (if (and repeat?
             (spos? intervalSecs))
      (configRepeat tm d (s2ms intervalSecs) func)
      (configOnce tm d func))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startTimer

  ""
  [^Service co & [repeat?]]

  (log/debug "service %s: created a java-timer" (.id co))
  (.setv (.getx co) :timer (Timer. true))
  (loopableSchedule co repeat?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- killTimer

  ""
  [^Service co]

  (when-some [t (.getv (.getx co) :timer)]
    (try! (.cancel ^Timer t))
    (.unsetv (.getx co) :timer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<> ::RepeatingTimer [^Service co & _] (mkEventObj co true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::RepeatingTimer
  [^Service co & args]

  (log/info "iostart: %s: %s" (gtid co) (.id co))
  (startTimer co true)
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::RepeatingTimer
  [^Service co & args]

  (log/info "io->stop %s: %s" (gtid co) (.id co))
  (killTimer co)
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::RepeatingTimer
  [^Service co & args]

  ;;(log/debug "loopableWakeup %s: %s" (gtid co) (.id co))
  (.dispatch co (ioevent<> co) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  :default
  [^Service co & [repeat?]]

  (configTimerTask co repeat?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<> ::OnceTimer [^Service co & _] (mkEventObj false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::OnceTimer
  [^Service co & args]

  (log/info "io->start %s: %s" (gtid co) (.id co))
  (startTimer co false)
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::OnceTimer
  [^Service co & args]

  (log/info "io->stop %s: %s" (gtid co) (.id co))
  (killTimer co)
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::OnceTimer
  [^Service co & args]

  (.dispatch co (ioevent<> co))
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer
(defmethod loopableSchedule

  ::ThreadedTimer
  [^Service co & [intervalMillis]]

  (log/debug "%s timer @interval = %d" (gtid co) intervalMillis)
  (let [loopy (volatile! true)]
    (.setv (.getx co) :loopy loopy)
    (async!
      #(while @loopy
         (loopableWakeup co intervalMillis))
      {:cl (getCldr)} )
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::ThreadedTimer
  [^Service co & [waitMillis]]

  ;;(log/debug "loopableWakeup %s: %s" (gtid co) (.id co))
  (.dispatch co (ioevent<> co))
  (safeWait waitMillis)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::ThreadedTimer
  [^Service co & args]

  (log/info "io->start: %s: %s" (gtid co) (.id co))
  (let [{:keys [intervalSecs
                delaySecs delayWhen]}
        (.config co)
        func #(loopableSchedule co (s2ms intervalSecs))]
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
  [^Service co & args]

  (log/info "io->stop %s: %s" (gtid co) (.id co))
  (when-some [loopy (.getv (.getx co) :loopy) ]
    (vreset! loopy false))
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


