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

(ns ^{:doc "A Scheduler with pooled threads"
      :author "kenl" }

  czlab.xlib.util.scheduler

  (:require
    [czlab.xlib.util.core
    :refer [trap! ex* NextInt juid MubleObj]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [ToKW hgl?]])

  (:import
    [com.zotohlab.frwk.util RunnableWithId Schedulable TCore]
    [com.zotohlab.frwk.core Activable Identifiable Named]
    [java.util.concurrent ConcurrentHashMap]
    [java.util Map Properties Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NulScheduler*

  "Make a Single threaded Scheduler"

  ^Schedulable
  []

  (with-meta
    (reify

      Schedulable

      (dequeue [_ w] )

      (run [this w]
        (when-some [^Runnable r w]
          (.run r)))

      (postpone [this w delayMillis]
        (when (> delayMillis 0)
          (Thread/sleep delayMillis))
        (.run this w))

      (hold [this w] (.hold this 0 w))

      (hold [_ _ w]
        (trap! Exception "Not Implemented."))

      (wakeup [this w]
        (.wakeAndRun this 0 w))

      (wakeAndRun [this _ w] (.run this w))

      (reschedule [this w] (.run this w))

      (dispose [_] )

      Activable

      (activate [_ options] )
      (deactivate [_] ))

    { :typeid (ToKW "czc.frwk.util" "NulScheduler") } ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xrefPID

  "id of this runnable or nil"

  [runable]

  (when
    (instance? Identifiable runable)
    (.id ^Identifiable runable)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preRun

  "Stuff to do before running the task"

  [^Map hQ ^Map rQ w]

  (when-some [pid (xrefPID w) ]
    (.remove hQ pid)
    (.put rQ pid w)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addTimer

  "Schedule a timer task"

  [^Timer timer ^TimerTask task
   ^long delay]

  (.schedule timer task delay))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkSCD

  "Make a Scheduler"

  ^Schedulable
  [^String named]

  (let [jid (if-not (hgl? named)
              (format "xlib#core-%03d" (NextInt))
              (str named "#core"))
        holdQ (ConcurrentHashMap.)
        runQ (ConcurrentHashMap.)
        timer (atom nil)
        cpu (atom nil)
        impl (MubleObj) ]
    (reset! timer (Timer. jid true))
    (with-meta
      (reify

        Schedulable

        ;; called by a *running* task to remove itself from the running queue
        (dequeue [_ w]
          (when-some [pid (xrefPID w)]
            (.remove runQ pid)))

        (run [this w]
          (when-some [^Runnable r w]
            (preRun holdQ runQ r)
            (-> ^TCore
                @cpu
                (.schedule r))))

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
          (when (some? pid)
            (.remove runQ pid)
            (.put holdQ pid w) ))

        (wakeup [this w]
          (.wakeAndRun this (xrefPID w) w))

        (wakeAndRun [this pid w]
          (when (some? pid)
            (.remove holdQ pid)
            (.put runQ pid w)
            (.run this w) ))

        (reschedule [this w]
          (when (some? w)
            (.run this w)))

        (dispose [_]
          (let [^TCore c @cpu]
            (.cancel ^Timer @timer)
            (.clear holdQ)
            (.clear runQ)
            (when (some? c) (.dispose c))))

        Activable

        (activate [_ options]
          (let [^long t (or (:threads options) 4)
                b (not (false? (:trace options)))
                c (TCore. jid t b) ]
            (reset! cpu c)
            (.start c)))

        (deactivate [_]
          (.cancel ^Timer @timer)
          (.clear holdQ)
          (.clear runQ)
          (.stop ^TCore @cpu)))

      { :typeid (ToKW "czc.frwk.util" "Scheduler") } )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Scheduler*

  "Make a Scheduler"

  (^Schedulable [] (Scheduler* ""))
  (^Schedulable [^String named] (mkSCD named)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

