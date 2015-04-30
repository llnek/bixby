;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.io.loops

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core
         :only
         [NextLong spos? MubleAPI TryC]]
        [czlabclj.xlib.util.process :only [Coroutine SafeWait]]
        [czlabclj.xlib.util.dates :only [ParseDate]]
        [czlabclj.xlib.util.meta :only [GetCldr]]
        [czlabclj.xlib.util.str :only [nsb hgl? strim]]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.io.core])

  (:import  [java.util Date Timer TimerTask]
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
                                  (TryC (apply func []))))
        [^Date dw ^long ds]
        delays]
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
        [^Date dw ^long ds]
        delays]
    (when (instance? Date dw)
      (.schedule tm tt dw) )
    (when (number? ds)
      (.schedule tm tt ds))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask ""

  [^czlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        intv (:intervalMillis cfg)
        t (.getAttr co :timer)
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

  [^czlabclj.tardis.core.sys.Element co cfg]

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

  [^czlabclj.tardis.core.sys.Element co]

  (.setAttr! co :timer (Timer. true))
  (LoopableSchedule co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- kill-timer ""

  [^czlabclj.tardis.core.sys.Element co]

  (let [^Timer t (.getAttr co :timer) ]
    (TryC
        (when-not (nil? t) (.cancel t)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/RepeatingTimer

  [co & args]

  (log/info "IOESReifyEvent: RepeatingTimer: " (.id ^Identifiable co))
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

      { :typeid :czc.tardis.io/TimerEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/RepeatingTimer

  [^czlabclj.tardis.core.sys.Element co cfg0]

  (log/info "CompConfigure: RepeatingTimer: " (.id ^Identifiable co))
  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        c2 (CfgLoopable co cfg)]
    (.setAttr! co :emcfg c2)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/RepeatingTimer

  [co]

  (log/info "IOESStart: RepeatingTimer: " (.id ^Identifiable co))
  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/RepeatingTimer

  [co]

  (log/info "IOESStop RepeatingTimer: " (.id ^Identifiable co))
  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/RepeatingTimer

  [^czlabclj.tardis.io.core.EmitAPI co & args]

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
(defmethod IOESReifyEvent :czc.tardis.io/OnceTimer

  [co & args]

  (log/info "IOESReifyEvent: OnceTimer: " (.id ^Identifiable co))
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

      { :typeid :czc.tardis.io/TimerEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/OnceTimer

  [^czlabclj.tardis.core.sys.Element co cfg0]

  (log/info "CompConfigure: OnceTimer: " (.id ^Identifiable co))
  ;; get rid of interval millis field, if any
  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        c2 (CfgLoopable co cfg) ]
    (.setAttr! co :emcfg (dissoc c2 :intervalMillis))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/OnceTimer

  [co]

  (log/info "IOESStart OnceTimer: " (.id ^Identifiable co))
  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/OnceTimer

  [co]

  (log/info "IOESStop OnceTimer: " (.id ^Identifiable co))
  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/OnceTimer

  [^czlabclj.tardis.io.core.EmitAPI co & args]

  (.dispatch co (IOESReifyEvent co) {} )
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :czc.tardis.io/ThreadedTimer

  [^czlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        intv (:intervalMillis cfg)
        loopy (atom true)
        cl (GetCldr) ]
    (log/info "Threaded one timer - interval = " intv)
    (.setAttr! co :loopy loopy)
    (Coroutine #(while @loopy (LoopableWakeup co intv)) cl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/ThreadedTimer

  [co & args]

  (TryC (LoopableOneLoop co) )
  (SafeWait (first args) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/ThreadedTimer

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "IOESStart: ThreadedTimer: " (.id ^Identifiable co))
  (let [cfg (.getAttr co :emcfg)
        intv (:intervalMillis cfg)
        ds (:delayMillis cfg)
        dw (:delayWhen cfg)
        loopy (atom true)
        cl (GetCldr)
        func #(LoopableSchedule co) ]
    (.setAttr! co :loopy loopy)
    (if (or (number? ds)
            (instance? Date dw))
      (configTimer (Timer.) [dw ds] func)
      (apply func []))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/ThreadedTimer

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "IOESStop ThreadedTimer: " (.id ^Identifiable co))
  (let [loopy (.getAttr co :loopy) ]
    (reset! loopy false)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private loops-eof nil)

