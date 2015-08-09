;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.io.loops

  (:require
    [czlab.xlib.util.process :refer [Coroutine SafeWait]]
    [czlab.xlib.util.core :refer [NextLong spos? tryc]]
    [czlab.xlib.util.dates :refer [ParseDate]]
    [czlab.xlib.util.meta :refer [GetCldr]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [hgl? strim]])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.core])

  (:import
    [java.util Date Timer TimerTask]
    [com.zotohlab.skaro.io TimerEvent]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.frwk.core Identifiable Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti LoopableWakeup "" (fn [a & args] (:typeid (meta a)) ))
(defmulti LoopableSchedule "" (fn [a] (:typeid (meta a)) ))
(defmulti LoopableOneLoop "" (fn [a] (:typeid (meta a)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-repeat-timer ""

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
        func #(LoopableWakeup co) ]
    (if (number? intervalMillis)
      (config-repeat-timer t
                           [delayWhen delayMillis] intervalMillis func)
      (configTimer t [delayWhen delayMillis] func))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CfgLoopable ""

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
  (LoopableSchedule co))

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
(defmethod IOESReifyEvent :czc.skaro.io/RepeatingTimer

  [co & args]

  (log/info "IOESReifyEvent: RepeatingTimer: %s" (.id ^Identifiable co))
  (let [eeid (NextLong) ]
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
(defmethod CompConfigure :czc.skaro.io/RepeatingTimer

  [^Muble co cfg0]

  (log/info "compConfigure: RepeatingTimer: %s" (.id ^Identifiable co))
  (->> (merge (.getv co :dftOptions) cfg0)
       (CfgLoopable co )
       (.setv co :emcfg )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/RepeatingTimer

  [co]

  (log/info "IOESStart: RepeatingTimer: %s" (.id ^Identifiable co))
  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/RepeatingTimer

  [co]

  (log/info "IOESStop RepeatingTimer: %s" (.id ^Identifiable co))
  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.skaro.io/RepeatingTimer

  [^czlab.skaro.io.core.EmitAPI co & args]

  (.dispatch co (IOESReifyEvent co) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :default

  [co]

  (configTimerTask co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/OnceTimer

  [co & args]

  (log/info "IOESReifyEvent: OnceTimer: %s" (.id ^Identifiable co))
  (let [eeid (NextLong) ]
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
(defmethod CompConfigure :czc.skaro.io/OnceTimer

  [^Muble co cfg0]

  (log/info "compConfigure: OnceTimer: %s" (.id ^Identifiable co))
  ;; get rid of interval millis field, if any
  (let [cfg (merge (.getv co :dftOptions) cfg0)
        c2 (CfgLoopable co cfg) ]
    (.setv co :emcfg (dissoc c2 :intervalMillis))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/OnceTimer

  [co]

  (log/info "IOESStart OnceTimer: %s" (.id ^Identifiable co))
  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/OnceTimer

  [co]

  (log/info "IOESStop OnceTimer: %s" (.id ^Identifiable co))
  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.skaro.io/OnceTimer

  [^czlab.skaro.io.core.EmitAPI co & args]

  (.dispatch co (IOESReifyEvent co) {} )
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :czc.skaro.io/ThreadedTimer

  [^Muble co]

  (let [{:keys [intervalMillis]}
        (.getv co :emcfg)
        loopy (atom true) ]

    (log/info "Threaded one timer - interval = %s" intervalMillis)
    (.setv co :loopy loopy)
    (Coroutine
      #(while @loopy
         (LoopableWakeup co intervalMillis))
      (GetCldr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.skaro.io/ThreadedTimer

  [co & args]

  (tryc (LoopableOneLoop co) )
  (SafeWait (first args) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/ThreadedTimer

  [^Muble co]

  (log/info "IOESStart: ThreadedTimer: %s" (.id ^Identifiable co))
  (let [{:keys [intervalMillis delayMillis delayWhen]}
        (.getv co :emcfg)
        loopy (atom true)
        func #(LoopableSchedule co) ]
    (.setv co :loopy loopy)
    (if (or (number? delayMillis)
            (instance? Date delayWhen))
      (configTimer (Timer.) [delayWhen delayMillis] func)
      (func))
    (IOESStarted co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/ThreadedTimer

  [^Muble co]

  (log/info "IOESStop ThreadedTimer: %s" (.id ^Identifiable co))
  (let [loopy (.getv co :loopy) ]
    (reset! loopy false)
    (IOESStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

