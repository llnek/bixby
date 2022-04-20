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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns czlab.bixby.demo.fork.core

  (:require [czlab.flux.core :as w]
            [czlab.basal.core :as c]
            [czlab.bixby.core :as b]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- fib
  [n] (if (< n 3) 1 (+ (fib (- n 2)) (fib (- n 1)))))

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
(c/def- a1
  #(c/do->nil
     (let [job %2]
       (println "I am the *Parent*")
       (println "I am programmed to fork off a parallel child process, "
                "and continue my business."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- a2
  (w/group<>
    #(c/do->nil
       (let [job %2]
         (c/prn!! "*Child*: will create my own child (blocking)")
         (c/setv job :rhs 60)
         (c/setv job :lhs 5)))
    (w/split-join<> [:type :and]
      #(c/do->nil
         (let [job %2]
           (c/prn!! "*Child->child*: taking some time to do " "this task... ( ~ 6secs)")
           (dotimes [n 7]
             (Thread/sleep 1000) (c/prn! "."))
           (c/prn!! "")
           (c/prn!! "*Child->child*: returning result back to *Child*.")
           (c/setv job :result (* (c/getv job :rhs)
                                  (c/getv job :lhs)))
           (c/prn!! "*Child->child*: done."))))
    #(c/do->nil
       (let [job %2]
         (c/prn!! "*Child*: the result for (5 * 60) according to %s%s"
                  "my own child is = "
                  (c/getv job :result))
         (c/prn!! "*Child*: done.")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- a3
  #(c/do->nil
     (let [_ %2
           b (c/sbf<> "*Parent*: ")]
       (c/prn!! "*Parent*: after fork, continue to calculate fib(6)...")
       (dotimes [n 7]
         (when (> n 0)
           (c/sbf+ b (str (fib n) " "))))
       (c/prn!! (str b) "\n" "*Parent*: done."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn demo

  "Split but no wait, parent continues."
  [evt]

  (let [p (c/parent evt)
        s (c/parent p)
        c (b/scheduler s)]
    (w/exec (w/workflow*
              (w/group<> a1 (w/split<> a2) a3)) (w/job<> c nil evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


