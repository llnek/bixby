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

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [NextInt juid MakeMMap]]
        [czlabclj.xlib.util.str :only [Format]])

  (:import  [com.zotohlab.frwk.util RunnableWithId Schedulable TCore]
            [com.zotohlab.frwk.core Identifiable Named]
            [java.util.concurrent ConcurrentHashMap]
            [java.util Map Properties Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


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
(defprotocol SchedulerAPI

  ""

  (preRun [_ w] )
  (activate [_ options] )
  (deactivate [_] )
  (addTimer [_ task dely] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkSCD "Make a Scheduler."

  ;;^czlabclj.xlib.util.scheduler.SchedulerAPI
  ^Schedulable
  [parObj]

  (let [impl (MakeMMap) ]
    (with-meta
      (reify SchedulerAPI

        (activate [_ options]
          (let [^long t (or (:threads options) 4)
                b (or (:trace options) true)
                jid (if-not (instance? Named parObj)
                      (Format "skaro#core-%03d" (NextInt))
                      (str (.getName ^Named parObj) "#core"))
                c (TCore. jid t (true? b)) ]
            (doto impl
              (.setf! :holdQ (ConcurrentHashMap.))
              (.setf! :runQ (ConcurrentHashMap.))
              (.setf! :timer (Timer. jid true))
              (.setf! :core c))
            (.start c)))

        (preRun [_ w]
          (let [pid (xrefPID w) ]
            (when-not (nil? pid)
              (.remove ^Map (.getf impl :holdQ) pid)
              (.put ^Map (.getf impl :runQ) pid w))))

        (deactivate [_]
          (.cancel ^Timer (.getf impl :timer))
          (.clear ^Map (.getf impl :holdQ))
          (.clear ^Map (.getf impl :runQ))
          (.stop ^TCore (.getf impl :core)))

        (addTimer [_ task dely]
          (.schedule ^Timer (.getf impl :timer)
                     ^TimerTask task
                     ^long dely))

        Schedulable

        ;; called by a *running* task to remove itself from the running queue
        (dequeue [_ w]
          (let [pid (xrefPID w) ]
            (when-not (nil? pid)
              (.remove ^Map (.getf impl :runQ) pid))) )

        (run [this w]
          (let [^Runnable r w]
            (.preRun this r)
            (.schedule ^TCore (.getf impl :core) r)) )

        (postpone [me w delayMillis]
          (cond
            (< delayMillis 0) (.hold me w)
            (= delayMillis 0) (.run me w)
            :else
            (do
              (addTimer me
                (proxy [TimerTask] []
                  (run [] (.wakeup me w))) delayMillis)) ))

        (hold [this w]
          (.hold this (xrefPID w) w))

        (hold [_ pid w]
          (when-not (nil? pid)
            (.remove ^Map (.getf impl :runQ) pid)
            (.put ^Map (.getf impl :holdQ) pid w) ))

        (wakeup [this w]
          (.wakeAndRun this (xrefPID w) w))

        (wakeAndRun [this pid w]
          (when-not (nil? pid)
            (.remove ^Map (.getf impl :holdQ) pid)
            (.put ^Map (.getf impl :runQ) pid w)
            (.run this w) ))

        (reschedule [this w]
          (when-not (nil? w)
            (.run this w)))

        (dispose [_]
          (let [^TCore c (.getf impl :core) ]
            (when-not (nil? c) (.dispose c)))) )

      { :typeid (keyword "czc.frwk.util/Scheduler") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeScheduler "Make a Scheduler."

  (^Schedulable [] (MakeScheduler nil))
  (^Schedulable [parObj] (mkSCD parObj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private scheduler-eof nil)


