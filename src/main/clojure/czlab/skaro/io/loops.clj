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
    [czlab.xlib.process :refer [async! safeWait]]
    [czlab.xlib.core :refer [seqint2 spos? try!]]
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
(defn- config-repeat-timer

  ""
  [^Timer tm delays
   ^long intv
   func]

  (let [[^Date dw ^long ds]
        delays
        tt (proxy [TimerTask][]
             (run []
               (tryc (func)))) ]

    (when (instance? Date dw)
      (.schedule tm tt dw intv))
    (when (number? ds)
      (.schedule tm tt ds intv))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimer ""

  [^Timer tm delays func]

  (let [[^Date dw ^long ds]
        delays
        tt (proxy [TimerTask][]
             (run []
               (func))) ]

    (when (instance? Date dw)
      (.schedule tm tt dw) )
    (when (number? ds)
      (.schedule tm tt ds))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask ""

  [^Muble co]

  (let [{:keys [intervalMillis
                delayWhen
                delayMillis]}
        (.getv co :emcfg)
        t (.getv co :timer)
        func #(loopableWakeup co) ]
    (if (number? intervalMillis)
      (config-repeat-timer t
                           [delayWhen delayMillis] intervalMillis func)
      (configTimer t [delayWhen delayMillis] func))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cfgLoopable ""

  [^Muble co cfg]

  (let [{:keys [intervalSecs
                delaySecs delayWhen]}
        cfg ]
    (with-local-vars [cpy (transient cfg)]
      (if (instance? Date delayWhen)
        (var-set cpy (assoc! @cpy :delayWhen delayWhen))
        (var-set cpy (assoc! @cpy
                             :delayMillis
                             (* 1000
                                (if (spos? delaySecs) delaySecs 3)))))
      (when (spos? intervalSecs)
        (var-set cpy (assoc! @cpy
                             :intervalMillis (* 1000 intervalSecs))))
      (-> (persistent! @cpy)
          (dissoc :delaySecs)
          (dissoc :intervalSecs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- start-timer ""

  [^Muble co]

  (.setv co :timer (Timer. true))
  (loopableSchedule co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- kill-timer ""

  [^Muble co]

  (let [^Timer t (.getv co :timer) ]
    (tryc
      (when (some? t) (.cancel t)) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioReifyEvent :czc.skaro.io/RepeatingTimer

  [co & args]

  (log/info "ioReifyEvent: RepeatingTimer: %s" (.id ^Identifiable co))
  (let [eeid (nextLong) ]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (emitter [_] co)
        (isRepeating [_] true))

      {:typeid :czc.skaro.io/TimerEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/RepeatingTimer

  [^Muble co cfg0]

  (log/info "compConfigure: RepeatingTimer: %s" (.id ^Identifiable co))
  (->> (merge (.getv co :dftOptions) cfg0)
       (cfgLoopable co )
       (.setv co :emcfg )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStart :czc.skaro.io/RepeatingTimer

  [co & args]

  (log/info "ioStart: RepeatingTimer: %s" (.id ^Identifiable co))
  (start-timer co)
  (ioStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStop :czc.skaro.io/RepeatingTimer

  [co & args]

  (log/info "ioStop RepeatingTimer: %s" (.id ^Identifiable co))
  (kill-timer co)
  (ioStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup :czc.skaro.io/RepeatingTimer

  [^Emitter co & args]

  (.dispatch co (ioReifyEvent co) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule :default

  [co & args]

  (configTimerTask co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioReifyEvent :czc.skaro.io/OnceTimer

  [co & args]

  (log/info "ioReifyEvent: OnceTimer: %s" (.id ^Identifiable co))
  (let [eeid (nextLong) ]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (emitter [_] co)
        (isRepeating [_] false))

      {:typeid :czc.skaro.io/TimerEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/OnceTimer

  [^Muble co cfg0]

  (log/info "compConfigure: OnceTimer: %s" (.id ^Identifiable co))
  ;; get rid of interval millis field, if any
  (let [cfg (merge (.getv co :dftOptions) cfg0)
        c2 (cfgLoopable co cfg) ]
    (.setv co :emcfg (dissoc c2 :intervalMillis))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStart :czc.skaro.io/OnceTimer

  [co & args]

  (log/info "ioStart OnceTimer: %s" (.id ^Identifiable co))
  (start-timer co)
  (ioStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStop :czc.skaro.io/OnceTimer

  [co & args]

  (log/info "ioStop OnceTimer: %s" (.id ^Identifiable co))
  (kill-timer co)
  (ioStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup :czc.skaro.io/OnceTimer

  [^Emitter co & args]

  (.dispatch co (ioReifyEvent co) {} )
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule :czc.skaro.io/ThreadedTimer

  [^Muble co & args]

  (let [{:keys [intervalMillis]}
        (.getv co :emcfg)
        loopy (atom true) ]

    (log/info "Threaded one timer - interval = %s" intervalMillis)
    (.setv co :loopy loopy)
    (async!
      #(while @loopy
         (loopableWakeup co intervalMillis))
      (getCldr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup :czc.skaro.io/ThreadedTimer

  [co & args]

  (tryc (loopableOneLoop co) )
  (safeWait (first args) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStart :czc.skaro.io/ThreadedTimer

  [^Muble co & args]

  (log/info "ioStart: ThreadedTimer: %s" (.id ^Identifiable co))
  (let [{:keys [intervalMillis delayMillis delayWhen]}
        (.getv co :emcfg)
        loopy (atom true)
        func #(loopableSchedule co) ]
    (.setv co :loopy loopy)
    (if (or (number? delayMillis)
            (instance? Date delayWhen))
      (configTimer (Timer.) [delayWhen delayMillis] func)
      (func))
    (ioStarted co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStop :czc.skaro.io/ThreadedTimer

  [^Muble co & args]

  (log/info "ioStop ThreadedTimer: %s" (.id ^Identifiable co))
  (let [loopy (.getv co :loopy) ]
    (reset! loopy false)
    (ioStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


