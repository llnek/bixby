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

  czlabclj.xlib.util.scheduler

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core :only [NextInt juid MakeMMap]]
        [czlabclj.xlib.util.str :only [Format hgl?]])

  (:import  [com.zotohlab.frwk.util RunnableWithId Schedulable TCore]
            [com.zotohlab.frwk.core Activable Identifiable Named]
            [java.util.concurrent ConcurrentHashMap]
            [java.util Map Properties Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NulScheduler "Make a Mock Scheduler."

  ^Schedulable
  []

  (with-meta
    (reify

      Schedulable

      (dequeue [_ w] )

      (run [this w]
        (when-let [^Runnable r w]
          ;;(.preRun this r)
          ;;(log/debug "mock scheduler: nothing to schedule - just run it.")
          (.run r)))

      (postpone [this w delayMillis]
        (when (> delayMillis 0)
          (Thread/sleep delayMillis))
        (.run this w))

      (hold [this w] (.hold this 0 w))

      (hold [_ _ w]
        (throw (Exception. "Not Implemented.")))

      (wakeup [this w]
        (.wakeAndRun this 0 w))

      (wakeAndRun [this _ w] (.run this w))

      (reschedule [this w] (.run this w))

      (dispose [_] )

      Activable

      (activate [_ options] )
      (deactivate [_] ))

    { :typeid (keyword "czc.frwk.util/NulScheduler") }

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xrefPID

  [runable]

  (if (instance? Identifiable runable)
    (.id ^Identifiable runable)
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preRun  ""

  [^Map hQ ^Map rQ w]

  (when-let [pid (xrefPID w) ]
    (.remove hQ pid)
    (.put rQ pid w)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addTimer ""
  [^Timer timer ^TimerTask task ^long delay]
  (.schedule timer task delay))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkSCD "Make a Scheduler."

  ^Schedulable
  [^String named]

  (let [jid (if-not (hgl? named)
              (Format "xlib#core-%03d" (NextInt))
              (str named "#core"))
        holdQ (ConcurrentHashMap.)
        runQ (ConcurrentHashMap.)
        timer (atom nil)
        impl (MakeMMap) ]
    (reset! timer (Timer. jid true))
    (with-meta
      (reify

        Schedulable

        ;; called by a *running* task to remove itself from the running queue
        (dequeue [_ w]
          (when-let [pid (xrefPID w) ]
              (.remove runQ pid)))

        (run [this w]
          (when-let [^Runnable r w]
            (preRun holdQ runQ r)
            (.schedule ^TCore (.getf impl :core) r)) )

        (postpone [me w delayMillis]
          (cond
            (< delayMillis 0) (.hold me w)
            (= delayMillis 0) (.run me w)
            :else
            (do
              (addTimer @timer
                (proxy [TimerTask] []
                  (run [] (.wakeup me w))) delayMillis)) ))

        (hold [this w]
          (.hold this (xrefPID w) w))

        (hold [_ pid w]
          (when-not (nil? pid)
            (.remove runQ pid)
            (.put holdQ pid w) ))

        (wakeup [this w]
          (.wakeAndRun this (xrefPID w) w))

        (wakeAndRun [this pid w]
          (when-not (nil? pid)
            (.remove holdQ pid)
            (.put runQ pid w)
            (.run this w) ))

        (reschedule [this w]
          (when-not (nil? w)
            (.run this w)))

        (dispose [_]
          (let [^TCore c (.getf impl :core) ]
            (.cancel ^Timer @timer)
            (.clear holdQ)
            (.clear runQ)
            (when-not (nil? c) (.dispose c))))

        Activable

        (activate [_ options]
          (let [^long t (or (:threads options) 4)
                b (not (false? (:trace options)))
                c (TCore. jid t b) ]
            (doto impl
              (.setf! :core c))
            (.start c)))

        (deactivate [_]
          (.cancel ^Timer @timer)
          (.clear holdQ)
          (.clear runQ)
          (.stop ^TCore (.getf impl :core))))

      { :typeid (keyword "czc.frwk.util/Scheduler") }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeScheduler "Make a Scheduler."

  (^Schedulable [] (MakeScheduler ""))
  (^Schedulable [^String named] (mkSCD named)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private scheduler-eof nil)


