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
    [czlab.xlib.util.core
    :refer [NextLong spos? Muble tryc]]
    [czlab.xlib.util.dates :refer [ParseDate]]
    [czlab.xlib.util.meta :refer [GetCldr]]
    [czlab.xlib.util.str :refer [hgl? strim]])

  (:require
    [czlab.xlib.util.logging :as log])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.core])

  (:import
    [java.util Date Timer TimerTask]
    [com.zotohlab.skaro.io TimerEvent]
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

  (let [tt (proxy [TimerTask][] (run []
                                  (tryc (apply func []))))
        [^Date dw ^long ds] delays]
    (when (instance? Date dw)
      (.schedule tm tt dw intv))
    (when (number? ds)
      (.schedule tm tt ds intv))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimer ""

  [^Timer tm delays func]

  (let [tt (proxy [TimerTask][] (run []
                                  (apply func [])))
        [^Date dw ^long ds] delays]
    (when (instance? Date dw)
      (.schedule tm tt dw) )
    (when (number? ds)
      (.schedule tm tt ds))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask ""

  [^czlab.xlib.util.core.Muble co]

  (let [cfg (.getf co :emcfg)
        intv (:intervalMillis cfg)
        t (.getf co :timer)
        ds (:delayMillis cfg)
        dw (:delayWhen cfg)
        func #(LoopableWakeup co) ]
    (if (number? intv)
      (config-repeat-timer t [dw ds] intv func)
      (configTimer t [dw ds] func))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CfgLoopable ""

  [^czlab.xlib.util.core.Muble co cfg]

  (let [intv (:intervalSecs cfg)
        ds (:delaySecs cfg)
        dw (:delayWhen cfg) ]
    (with-local-vars [cpy (transient cfg)]
      (if (instance? Date dw)
        (var-set cpy (assoc! @cpy :delayWhen dw))
        (var-set cpy (assoc! @cpy :delayMillis (* 1000
                                             (if (spos? ds) ds 3)))))
      (when (spos? intv)
        (var-set cpy (assoc! @cpy :intervalMillis (* 1000 intv))))
      (-> (persistent! @cpy)
          (dissoc :delaySecs)
          (dissoc :intervalSecs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- start-timer ""

  [^czlab.xlib.util.core.Muble co]

  (.setf! co :timer (Timer. true))
  (LoopableSchedule co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- kill-timer ""

  [^czlab.xlib.util.core.Muble co]

  (let [^Timer t (.getf co :timer) ]
    (tryc
        (when (some? t) (.cancel t)) )
  ))

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
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (isRepeating [_] true))

      { :typeid :czc.skaro.io/TimerEvent })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/RepeatingTimer

  [^czlab.xlib.util.core.Muble co cfg0]

  (log/info "compConfigure: RepeatingTimer: %s" (.id ^Identifiable co))
  (let [cfg (merge (.getf co :dftOptions) cfg0)
        c2 (CfgLoopable co cfg)]
    (.setf! co :emcfg c2)
  ))

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
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (isRepeating [_] false))

      { :typeid :czc.skaro.io/TimerEvent })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/OnceTimer

  [^czlab.xlib.util.core.Muble co cfg0]

  (log/info "compConfigure: OnceTimer: %s" (.id ^Identifiable co))
  ;; get rid of interval millis field, if any
  (let [cfg (merge (.getf co :dftOptions) cfg0)
        c2 (CfgLoopable co cfg) ]
    (.setf! co :emcfg (dissoc c2 :intervalMillis))
  ))

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

  [^czlab.xlib.util.core.Muble co]

  (let [cfg (.getf co :emcfg)
        intv (:intervalMillis cfg)
        loopy (atom true)
        cl (GetCldr) ]
    (log/info "Threaded one timer - interval = %s" intv)
    (.setf! co :loopy loopy)
    (Coroutine #(while @loopy (LoopableWakeup co intv)) cl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.skaro.io/ThreadedTimer

  [co & args]

  (tryc (LoopableOneLoop co) )
  (SafeWait (first args) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/ThreadedTimer

  [^czlab.xlib.util.core.Muble co]

  (log/info "IOESStart: ThreadedTimer: %s" (.id ^Identifiable co))
  (let [cfg (.getf co :emcfg)
        intv (:intervalMillis cfg)
        ds (:delayMillis cfg)
        dw (:delayWhen cfg)
        loopy (atom true)
        cl (GetCldr)
        func #(LoopableSchedule co) ]
    (.setf! co :loopy loopy)
    (if (or (number? ds)
            (instance? Date dw))
      (configTimer (Timer.) [dw ds] func)
      (apply func []))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/ThreadedTimer

  [^czlab.xlib.util.core.Muble co]

  (log/info "IOESStop ThreadedTimer: %s" (.id ^Identifiable co))
  (let [loopy (.getf co :loopy) ]
    (reset! loopy false)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

