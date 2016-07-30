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

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.core])

  (:import
    [java.util Date Timer TimerTask]
    [czlab.server EventEmitter]
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
(defn- configRepeatTimer

  ""
  [^Timer tm delays ^long intv func]

  (let [tt (tmtask<> func)
        [dw ds] delays]
    (when (inst? Date dw)
      (.schedule tm tt ^Date dw intv))
    (when (spos? ds)
      (.schedule tm tt ^long ds intv))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimer

  ""
  [^Timer tm delays func]

  (let [ tt (tmtask<> func)
        [dw ds] delays]
    (when (inst? Date dw)
      (.schedule tm tt ^Date dw) )
    (when (spos? ds)
      (.schedule tm tt ^long ds))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask

  ""
  [^Context co]

  (let [t (.getv (.getx co) :timer)
        {:keys [intervalMillis
                delayWhen
                delayMillis]}
        (.getv (.getx co) :emcfg)
        func #(loopableWakeup co) ]
    (if (spos? intervalMillis)
      (configRepeatTimer
        t
        [delayWhen delayMillis] intervalMillis func)
      (configTimer t [delayWhen delayMillis] func))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cfgLoopable

  ""
  [^Context co cfg]

  (let [{:keys [intervalSecs
                delaySecs delayWhen]} cfg ]
    (with-local-vars [cpy (transient cfg)]
      (if (inst? Date delayWhen)
        (var-set cpy (assoc! @cpy :delayWhen delayWhen))
        (var-set cpy (assoc! @cpy
                             :delayMillis
                             (* 1000
                                (if (spos? delaySecs) delaySecs 3)))))
      (when (spos? intervalSecs)
        (var-set cpy (assoc! @cpy
                             :intervalMillis (* 1000 intervalSecs))))
      (-> (persistent! @cpy)
          (dissoc :delaySecs :intervalSecs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startTimer

  ""
  [^Context co]

  (.setv (.getx co) :timer (Timer. true))
  (loopableSchedule co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- killTimer

  ""
  [^Context co]

  (when-some [t (.getv (.getx co) :timer)]
    (try! (.cancel ^Timer t))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::RepeatingTimer
  [^EventEmitter co & args]

  (log/info "ioevent: RepeatingTimer: %s" (.id ^Identifiable co))
  (let [eeid (seqint2)]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (id [_] eeid)
        (emitter [_] co)
        (isRepeating [_] true))

      {:typeid ::TimerEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::RepeatingTimer
  [^Context co cfg0]

  (log/info "comp->configure: RepeatingTimer: %s" (.id ^Identifiable co))
  (->> (merge (.getv (.getx co) :dftOptions) cfg0)
       (cfgLoopable co )
       (.setv (.getx co) :emcfg ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::RepeatingTimer
  [co & args]

  (log/info "iostart: RepeatingTimer: %s" (.id ^Identifiable co))
  (startTimer co)
  (io->started co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::RepeatingTimer
  [co & args]

  (log/info "iostop RepeatingTimer: %s" (.id ^Identifiable co))
  (killTimer co)
  (io->stopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::RepeatingTimer
  [^EventEmitter co & args]

  (.dispatch co (ioevent<> co) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  :default
  [co & args]

  (configTimerTask co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::OnceTimer
  [^EventEmitter co & args]

  (log/info "ioevent: OnceTimer: %s" (.id ^Identifiable co))
  (let [eeid (seqint2) ]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (id [_] eeid)
        (emitter [_] co)
        (isRepeating [_] false))

      {:typeid ::TimerEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::OnceTimer
  [^Context co cfg0]

  (log/info "comp->configure: OnceTimer: %s" (.id ^Identifiable co))
  ;; get rid of interval millis field, if any
  (let [cfg (merge (.getv (.getx co) :dftOptions) cfg0)
        c2 (cfgLoopable co cfg) ]
    (->> (dissoc c2 :intervalMillis)
         (.setv (.getx co) :emcfg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::OnceTimer
  [co & args]

  (log/info "ioStart OnceTimer: %s" (.id ^Identifiable co))
  (startTimer co)
  (io->started co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::OnceTimer
  [co & args]

  (log/info "ioStop OnceTimer: %s" (.id ^Identifiable co))
  (killTimer co)
  (io->stopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::OnceTimer
  [^EventEmitter co & args]

  (.dispatch co (ioevent<> co) {} )
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  ::ThreadedTimer
  [^Context co & args]

  (let [{:keys [intervalMillis]}
        (.getv (.getx co) :emcfg)
        loopy (atom true) ]

    (log/info "Threaded one timer - interval = %s" intervalMillis)
    (.setv (.getx co) :loopy loopy)
    (async!
      #(while @loopy
         (loopableWakeup co intervalMillis))
      (getCldr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::ThreadedTimer
  [co & args]

  (try! (loopableOneLoop co) )
  (safeWait (first args) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::ThreadedTimer
  [^Context co & args]

  (log/info "iostart: ThreadedTimer: %s" (.id ^Identifiable co))
  (let [{:keys [intervalMillis delayMillis delayWhen]}
        (.getv (.getx co) :emcfg)
        loopy (atom true)
        func #(loopableSchedule co) ]
    (.setv (.getx co) :loopy loopy)
    (if (or (spos? delayMillis)
            (inst? Date delayWhen))
      (configTimer (Timer.) [delayWhen delayMillis] func)
      (func))
    (io->started co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::ThreadedTimer
  [^Context co & args]

  (log/info "ioStop ThreadedTimer: %s" (.id ^Identifiable co))
  (let [loopy (.getv (.getx co) :loopy) ]
    (reset! loopy false)
    (io->stopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


