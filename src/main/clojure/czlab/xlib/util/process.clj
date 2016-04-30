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


(ns ^{:doc "OS Process related utilities"
      :author "kenl" }

  czlab.xlib.util.process

  (:require
    [czlab.xlib.util.core :refer [try! tryc]]
    [czlab.xlib.util.meta :refer [GetCldr]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [hgl?]])

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
    (with-local-vars
      [daemon false cl nil]
      (when
        (instance? ClassLoader arg)
        (var-set cl arg))
      (when (map? arg)
        (var-set cl (:classLoader arg))
        (when
          (true? (:daemon arg))
          (var-set daemon true)))
      (when (some? @cl)
        (.setContextClassLoader t ^ClassLoader @cl))
      (.setDaemon t (true? @daemon))
      (when start (.start t))
      (log/debug "threadFunc: thread#%s%s%s"
                 (.getName t)
                 ", daemon = " (.isDaemon t)))
    t))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncBlockExec

  "Run this function synchronously"

  [^Object lock func & args]

  (CU/syncExec
    lock
    (reify CallableWithArgs
      (run [_ p1 more]
        (apply func p1 more)))
    (first args)
    (drop 1 args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Coroutine

  "Run this function asynchronously"

  [func & [args]]

  {:pre [(fn? func)]}

  (ThreadFunc func true args))

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

  (let [ss (-> (ManagementFactory/getRuntimeMXBean)
               (.getName)
               (str)
               (.split "@")) ]
    (if (empty ss)
      ""
      (first ss))))

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
                 (long delayMillis))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

