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
(defn ThreadedTimer
  ""
  [{:keys [info conf] :as spec}]
  (let
    [loopy (volatile! true)
     schedule
     (fn [co funcs arg]
       (async!
         #(while @loopy
            ((or (:wakeup funcs)
                 (constantly nil)) co arg)
            (safeWait (:intervalMillis arg)))
         {:cl (getCldr)}))
     start
     (fn [co funcs]
       (let
         [{:keys [intervalSecs
                  delaySecs delayWhen]}
          (.config ^IoService co)
          func #(schedule co
                          funcs
                          {:intervalMillis
                           (s2ms intervalSecs)})]
         (if (or (spos? delaySecs)
                 (inst? Date delayWhen))
           (configOnce (Timer.)
                       [delayWhen (s2ms delaySecs)] func)
           (func))))
     stop (fn [_] (vreset! loopy false))
     init (fn [_ cfg0] (merge conf cfg0))]
    (doto
      {:schedule schedule
       :init init
       :start start
       :stop })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configRepeat
  ""
  [^Timer timer delays ^long intv func]
  (log/info "Scheduling a *repeating* timer: %dms" intv)
  (let
    [tt (tmtask<> func)
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
  ""
  [^Timer timer delays func]
  (log/info "Scheduling a *one-shot* timer at %s" delays)
  (let
    [tt (tmtask<> func)
     [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule timer tt ^Date dw)
      :else
      (.schedule timer
                 tt
                 (long (if (> ds 0) ds 1000))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimer
  [^IoService co
   timer
   wakeup
   {:keys [intervalSecs
           delayWhen delaySecs] :as cfg} repeat?]
  (let
    [d [delayWhen (s2ms delaySecs)]
     func #(wakeup co nil)]
    (test-some "java-timer" timer)
    (if (and repeat?
             (spos? intervalSecs))
      (configRepeat timer
                    d
                    (s2ms intervalSecs) func)
      (configOnce timer d func))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ioevent<>
  ^TimerEvent
  [co repeat?]
  (let [eeid (str "event#"
                  (seqint2))]
    (reify
      TimerEvent
      (checkAuthenticity [_] false)
      (id [_] eeid)
      (source [_] co)
      (isRepeating [_] repeat?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xxxTimer<>
  ""
  [{:keys [conf] :as spec} repeat?]
  (let
    [timer (atom nil)
     wakeup
     (fn [^IoService co _]
       (.dispatch co (ioevent<> co repeat?))
       (if-not repeat? (stop co)))
     schedule
     (fn [co funcs _]
       (configTimer co
                    @timer
                    (:wakeup funcs) repeat?))
     start
     (fn [co funcs]
       (swap! timer (Timer. true))
       (schedule co funcs _))
     stop
     (fn [_]
       (try! (some-> ^Timer
                     @timer (.cancel )))
       (reset! timer nil))
     init
     (fn [_ cfg0] (merge conf cfg0))]
    (doto
      {:schedule schedule
       :wakeup wakeup
       :init init
       :start start
       :stop stop})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RepeatingTimer
  ""
  [spec]
  (xxxTimer<> spec true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnceTimer
  ""
  [spec]
  (xxxTimer<> spec false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


