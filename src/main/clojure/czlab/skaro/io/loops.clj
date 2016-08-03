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
      :author "Kenneth Leung" }

  czlab.skaro.io.loops

  (:require
    [czlab.xlib.core :refer [inst? seqint2 spos? try!]]
    [czlab.xlib.process :refer [async! safeWait]]
    [czlab.xlib.dates :refer [parseDate]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hgl? strim]])

  (:use [czlab.skaro.sys.core]
        [czlab.skaro.io.core])

  (:import
    [java.util Date Timer TimerTask]
    [clojure.lang APersistentMap]
    [czlab.server Emitter]
    [czlab.skaro.io TimerEvent]
    [czlab.xlib Muble Identifiable Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defn- meta??? "" [a & args] (:typeid (meta a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti loopableSchedule "" meta???)
(defmulti loopableWakeup "" meta???)
(defmulti loopableOneLoop "" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configRTimer

  ""
  [^Timer tm delays ^long intv func]

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
(defn- configTimer

  ""
  [^Timer tm delays func]

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

  ""
  [^Service co & [repeat?]]

  (let [tm (.getv (.getx co) :timer)
        {:keys [intervalSecs
                delayWhen
                delaySecs]}
        d [delayWhen (s2ms delaySecs)]
        func #(loopableWakeup co)]
    (if (and repeat?
             (spos? intervalSecs))
      (configRTimer tm d (s2ms intervalSecs) func)
      (configTimer tm d func))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startTimer

  ""
  [^Service co & [repeat?]]

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
(defmethod ioevent<>

  ::RepeatingTimer
  [^Service co & args]

  (log/info "ioevent: RepeatingTimer: %s" (.id co))
  (let [eeid (seqint2)]
    (with-meta
      (reify
        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (session [_] nil)
        (id [_] eeid)
        (emitter [_] co)
        (isRepeating [_] true))

      {:typeid ::TimerEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::RepeatingTimer
  [^Service co & [cfg0]]

  (log/info "comp->initialize: RepeatingTimer: %s" (.id co))
  (let [c2 (merge (.config co) cfg0)]
    (.setv (.getx co) :emcfg c2)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::RepeatingTimer
  [co & args]

  (log/info "iostart: RepeatingTimer: %s" (.id co))
  (startTimer co true)
  (io->started co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::RepeatingTimer
  [co & args]

  (log/info "io->stop RepeatingTimer: %s" (.id co))
  (killTimer co)
  (io->stopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::RepeatingTimer
  [^Service co & args]

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
(defmethod ioevent<>

  ::OnceTimer
  [^Service co & args]

  (log/info "ioevent: OnceTimer: %s" (.id co))
  (let [eeid (seqint2) ]
    (with-meta
      (reify
        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (session [_] nil)
        (id [_] eeid)
        (emitter [_] co)
        (isRepeating [_] false))

      {:typeid ::TimerEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::OnceTimer
  [^Service co & [cfg0]]

  (log/info "comp->initialize: OnceTimer: %s" (.id co))
  (let [c2 (merge (.config co) cfg0)]
    (.setv (.getx co) :emcfg c2)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::OnceTimer
  [co & args]

  (log/info "io->start OnceTimer: %s" (.id co))
  (startTimer co false)
  (io->started co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::OnceTimer
  [co & args]

  (log/info "io->stop OnceTimer: %s" (.id co))
  (killTimer co)
  (io->stopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::OnceTimer
  [^Service co & args]

  (.dispatch co (ioevent<> co))
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  ::ThreadedTimer
  [^Service co & [intervalMillis]]

  (log/info "Threaded timer - interval = %d" intervalMillis)
  (let [loopy (atom true)]
    (.setv (.getx co) :loopy loopy)
    (async!
      #(while @loopy
         (loopableWakeup co intervalMillis))
      (getCldr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::ThreadedTimer
  [^Service co & args]

  (throwIOE "comp->initialize for threaded-timer called!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::ThreadedTimer
  [co & [waitMillis]]

  (try! (loopableOneLoop co))
  (safeWait waitMillis)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::ThreadedTimer
  [^Service co & args]

  (log/info "io->start: ThreadedTimer: %s" (.id co))
  (let [{:keys [intervalSecs
                delaySecs delayWhen]}
        (.config co)
        func #(loopableSchedule co (s2ms intervalSecs))]
    (if (or (spos? delaySecs)
            (inst? Date delayWhen))
      (configTimer (Timer.)
                   [delayWhen (s2ms delaySecs)] func)
      (func))
    (io->started co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::ThreadedTimer
  [^Service co & args]

  (log/info "io->stop ThreadedTimer: %s" (.id co))
  (let [loopy (.getv (.getx co) :loopy) ]
    (reset! loopy false)
    (io->stopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


