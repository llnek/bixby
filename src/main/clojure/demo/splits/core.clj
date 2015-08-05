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

(ns ^{:doc ""
      :author "kenl"}

  demo.splits.core

  (:use [czlab.xlib.util.wfs])

  (:mport
    [com.zotohlab.wflow WorkFlow Activity Split PTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;
;;
;;  parent(s1) --> split&nowait
;;                 |-------------> child(s1)----> split&wait --> grand-child
;;                 |                              |                    |
;;                 |                              |<-------------------+
;;                 |                              |---> child(s2) -------> end
;;                 |
;;                 |-------> parent(s2)----> end
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fib ""

  [n]

  (if (< n 3) 1 (+ (fib (- n 2)) (fib (- n 1)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  a0
  (SimPTask
    #(do
       (println "I am the *Parent*")
       (println "I am programmed to fork off a parallel child process, "
                "and continue my business"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  a1
  (Split/fork
    (SimPTask
      (fn [^Job j]
        (println "*child*: will create my own child (blocking)")
        (.setv j :rhs 60)
        (.setv j :lhs 5)
        (let [s1 (Split/applyAnd
                   (SimPTask
                     (fn [^Job jj]
                       (println "*child*: the result for (5 * 60) "
                                "according to my own child is = "
                                (.getv jj :result))
                       (println "*child*: done")))) ]
          ;; split & wait
          (-> s1
              (.include
                (SimPTask
                  (fn [^Job jx]
                    (println "*child->child*: taking some time to "
                             "do this task... ( ~ 6secs)")
                    (doseq [x (range 6)]
                      (try
                        (Thread/sleep 1000)
                        (catch InterruptedException e#
                          (.printStackTrace e#)))
                      (print "..."))
                      (println "")
                      (println "*child->child*: returning result back to *Child*")
                      (.setv jx :result  (* (.getv jx :rhs) (.getv jx :lhs)))
                      (println "*child->child*: done"))))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  a2
  (SimPTask
    (fn [^Job j]
      (println "*parent*: after fork, continue to calculate fib(6)...")
      (let [b (StringBuilder. "*parent*: ") ]
        (doseq [i (range 6)]
          (.append b (str (fib i) " ")))
        (println (str b  "\n" "*parent*: done"))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] WorkFlow

  (startWith [_]
    (-> a0
        (.chain a1)
        (.chain a2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

