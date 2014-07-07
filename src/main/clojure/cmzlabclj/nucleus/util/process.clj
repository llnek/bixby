;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc "OS Process related utilities."
       :author "kenl" }

  cmzlabclj.nucleus.util.process

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabclj.nucleus.util.meta :only [GetCldr] ])
  (:use [cmzlabclj.nucleus.util.core :only [Try!] ])
  (:use [cmzlabclj.nucleus.util.str :only [nsb] ])

  (:import (java.lang.management ManagementFactory))
  (:import (com.zotohlab.frwk.util CoreUtils))
  (:import (java.lang Thread Runnable)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AsyncExec "Run the code (runnable) in a separate daemon thread."

  ([^Runnable runable] (AsyncExec runable (GetCldr)))

  ([^Runnable runable ^ClassLoader cl]
   (if (nil? runable)
       nil
       (doto (Thread. runable)
         (.setContextClassLoader cl)
         (.setDaemon true)
         (.start))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Coroutine "Run this function asynchronously."

  ([func] (Coroutine func nil))

  ([func cl]
   (let [ r (reify Runnable
              (run [_] (when (fn? func) (func)))) ]
      (AsyncExec r cl))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeWait "Block current thread for some millisecs."

  [millisecs]

  (Try!  (when (> millisecs 0) (Thread/sleep millisecs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProcessPid "Get the current process pid."

  ^String
  []

  (let [ ss (.split (nsb (.getName (ManagementFactory/getRuntimeMXBean))) "@") ]
    (if (or (nil? ss) (empty ss)) "" (first ss))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private process-eof nil)

