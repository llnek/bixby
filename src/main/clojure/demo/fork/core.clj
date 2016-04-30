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


(ns ^:no-doc
    ^{:author "kenl"}

  demo.fork.core


  (:require
    [czlab.xlib.util.core :refer [try!]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [hgl?]]
    [czlab.xlib.util.wfs :refer [SimPTask]])

  (:import
    [com.zotohlab.skaro.core Container]
    [com.zotohlab.wflow Job
    Activity WorkFlow FlowDot PTask Split]
    [java.lang StringBuilder]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fib ""

  [n]

  (if (< n 3)
    1
    (+ (fib (- n 2))
       (fib (- n 1)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;   parent(s1) --> split&nowait
;;                  |-------------> child(s1)----> split&wait --> grand-child
;;                  |                              |                    |
;;                  |                              |<-------------------+
;;                  |                              |---> child(s2) -------> end
;;                  |
;;                  |-------> parent(s2)----> end

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Activity
  a1
  (SimPTask
    (fn [_]
       (println "I am the *Parent*")
       (println "I am programmed to fork off a parallel child process, "
                "and continue my business."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Activity
  a2
  (Split/fork
    (SimPTask
      (fn [^Job j]
        (println "*Child*: will create my own child (blocking)")
        (.setv j :rhs 60)
        (.setv j :lhs 5)
        (-> (Split/applyAnd
              (SimPTask
                (fn [^Job j]
                  (println "*Child*: the result for (5 * 60) according to "
                           "my own child is = "
                           (.getv j :result))
                  (println "*Child*: done."))))
            (.include
              (SimPTask
                (fn [^Job j2]
                  (println "*Child->child*: taking some time to do "
                           "this task... ( ~ 6secs)")
                  (dotimes [n 7]
                    (Thread/sleep 1000)
                    (print "."))
                  (println "")
                  (println "*Child->child*: returning result back to *Child*.")

                  (.setv j2 :result (* (.getv j2 :rhs)
                                       (.getv j2 :lhs)))
                  (println "*Child->child*: done.")
                  nil))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Activity
  a3
  (SimPTask
    (fn [_]
      (let [b (StringBuilder. "*Parent*: ")]
        (println "*Parent*: after fork, continue to calculate fib(6)...")
        (dotimes [n 7]
          (when (> n 0)
            (.append b (str (fib n) " "))))
        (println (.toString b) "\n" "*Parent*: done.")
        nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Demo

  "split but no wait, parent continues"

  ^WorkFlow
  []

  (reify WorkFlow
    (startWith [_]
      (-> (.chain a1 a2)
          (.chain a3)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

