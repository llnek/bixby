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

(ns ^{:doc "OS Process related utilities"
      :author "kenl" }

  czlabclj.xlib.util.process

  (:require
    [czlabclj.xlib.util.core :refer [try! tryc]]
    [czlabclj.xlib.util.meta :refer [GetCldr]]
    [czlabclj.xlib.util.str :refer [nsb hgl?]])

  (:require
    [czlabclj.xlib.util.logging :as log])

  (:import
    [java.lang.management ManagementFactory]
    [java.util.concurrent Callable]
    [java.util TimerTask Timer]
    [com.zotohlab.frwk.util CU]
    [com.zotohlab.frwk.core CallableWithArgs]
    [java.lang Thread Runnable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncBlockExec

  "Run this function synchronously"

  [^Object lock func & args]

  (CU/syncExec
    lock
    (reify CallableWithArgs
      (run [_ a1 pms]
        (apply func a1 pms)))
    (first args)
    (drop 1 args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- asyncExecThread

  "Execute this runnable in a separate thread"

  [^Runnable r options]

  (when (some? r)
    (let [c (or (:classLoader options)
                (GetCldr))
          d (true? (:daemon options))
          n (:name options)
          t (Thread. r) ]
      (.setContextClassLoader t ^ClassLoader c)
      (.setDaemon t d)
      (when (hgl? n)
        (.setName t (str "(" n ") " (.getName t))))
      (log/debug "asyncExecThread: start thread#%s%s%s"
                 (.getName t)
                 ", daemon = " d)
      (.start t))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AsyncExec

  "Run the code (runnable) in a separate thread"

  ([^Runnable runable]
   (AsyncExec runable (GetCldr)))

  ([^Runnable runable arg]
   (asyncExecThread
     runable
     (if (instance? ClassLoader arg)
       {:classLoader arg}
       (if (map? arg) arg {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Coroutine

  "Run this function asynchronously"

  [func & [options]]

  {:pre [(fn? func)]}

  (-> (reify Runnable
       (run [_]
         (tryc (func))))
      (AsyncExec options)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThreadFunc

  "Run this function in a separate thread"

  ^Thread
  [func start & [arg]]

  {:pre [(fn? func)]}

  (let
    [t (-> (reify
             Runnable
             (run [_] (func)))
           (Thread. ))]
    (with-local-vars [daemon false cl nil]
      (when (instance? ClassLoader arg)
        (var-set cl arg))
      (when (map? arg)
        (var-set cl (:classLoader arg))
        (when (true? (:daemon arg))
          (var-set daemon true)))
      (when (some? @cl)
        (.setContextClassLoader t ^ClassLoader @cl))
      (.setDaemon t (true? @daemon))
      (when start (.start t))
      (log/debug "threadFunc: thread#%s%s%s"
                 (.getName t)
                 ", daemon = " (.isDaemon t)))
    t
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeWait

  "Block current thread for some millisecs"

  [millisecs]

  (try! (when (> millisecs 0)
          (Thread/sleep millisecs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProcessPid

  "Get the current process pid"

  ^String
  []

  (let [ss (-> (nsb (.getName (ManagementFactory/getRuntimeMXBean)))
               (.split "@")) ]
    (if (or (nil? ss) (empty ss))
      ""
      (first ss))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DelayExec

  "Run this function after some delay"

  [func delayMillis]

  {:pre [(fn? func)]}

  (-> (Timer. true)
      (.schedule (proxy [TimerTask][]
                   (run []
                     (func)))
                 (long delayMillis))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

