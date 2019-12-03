;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.demo.fork.core

  (:require [czlab.wabbit.xpis :as xp]
            [czlab.flux.core :as w]
            [czlab.basal.core :as c]
            [czlab.basal.log :as l]
            [czlab.basal.xpis :as po])

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
  #(c/do#nil
     (let [job %2]
       (println "I am the *Parent*")
       (println "I am programmed to fork off a parallel child process, "
                "and continue my business."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- a2
  (w/group<>
    #(c/do#nil
       (let [job %2]
         (c/prn!! "*Child*: will create my own child (blocking)")
         (po/setv job :rhs 60)
         (po/setv job :lhs 5)))
    (w/split-join<> [:type :and]
      #(c/do#nil
         (let [job %2]
           (c/prn!! "*Child->child*: taking some time to do " "this task... ( ~ 6secs)")
           (dotimes [n 7]
             (Thread/sleep 1000) (c/prn! "."))
           (c/prn!! "")
           (c/prn!! "*Child->child*: returning result back to *Child*.")
           (po/setv job :result (* (po/getv job :rhs)
                                   (po/getv job :lhs)))
           (c/prn!! "*Child->child*: done."))))
    #(c/do#nil
       (let [job %2]
         (c/prn!! "*Child*: the result for (5 * 60) according to %s%s"
                  "my own child is = "
                  (po/getv job :result))
         (c/prn!! "*Child*: done.")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- a3
  #(c/do#nil
     (let [_ %2
           b (c/sbf<> "*Parent*: ")]
       (c/prn!! "*Parent*: after fork, continue to calculate fib(6)...")
       (dotimes [n 7]
         (when (> n 0)
           (c/sbf+ b (str (fib n) " "))))
       (c/prn!! (str b) "\n" "*Parent*: done."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn demo<>

  "Split but no wait, parent continues."
  [evt]

  (let [p (xp/get-pluglet evt)
        s (po/parent p)
        c (xp/get-scheduler s)]
    (w/exec (w/workflow*
              (w/group<> a1 (w/split<> a2) a3)) (w/job<> c nil evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


