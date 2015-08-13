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

(ns ^{:doc "Functions to enable console questions"
      :author "kenl" }

  czlab.xlib.util.cmdline

  (:require
    [czlab.xlib.util.core :refer [IsWindows?] ]
    [czlab.xlib.util.str :refer [strim Has?] ]
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:import
    [java.io BufferedOutputStream
    InputStreamReader OutputStreamWriter]
    [java.io Reader Writer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readData

  "Read user input"

  ^String
  [^Writer cout ^Reader cin]

  ;; windows has '\r\n' linux has '\n'

  (let [buf (StringBuilder.)
        ms (loop [c (.read cin)]
             (let [m (cond
                       (or (= c -1)(= c 4)) #{ :quit :break }
                       (= c (int \newline)) #{ :break }
                       (or (= c (int \return))
                           (= c (int \backspace)) (= c 27))
                       #{}
                       :else
                       (do
                         (.append buf (char c))
                         #{}))]
               (if (contains? m :break)
                 m
                 (recur (.read cin)))))]
    (if (contains? ms :quit)
      nil
      (strim buf))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onAnswer ""

  [^Writer cout
   cmdQ
   props
   answer]

  (let [dft (strim (:default cmdQ))
        must (:must cmdQ)
        nxt (:next cmdQ)
        res (:result cmdQ)]
    (if
      (nil? answer)
      (do
        (.write cout "\n")
        nil)
      ;;else
      (let [rc (if (empty? answer)
                 dft
                 answer)]
        (cond
          ;;if required to answer, repeat the question
          (and (empty? rc)
               must)
          (:id cmdQ)

          (keyword? res)
          (do
            (swap! props assoc res rc)
            nxt)

          (fn? res)
          (let [[n p] (res rc @props)]
            (reset! props p)
            n)

          :else :end)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- popQQ ""

  [^Writer cout
   ^Reader cin
   cmdQ
   props]

  (let [chs (strim (:choices cmdQ))
        dft (strim (:default cmdQ))
        q (strim (:question cmdQ))
        must (:must cmdQ)]
    (.write cout (str q (if must "*" "" ) " ? "))
    ;; choices ?
    (when-not (empty? chs)
      (if (Has? chs \n)
        (.write cout (str (if (.startsWith chs "\n")
                            "[" "[\n")
                          chs
                          (if (.endsWith chs "\n")
                            "]" "\n]" ) ))
        (.write cout (str "[" chs "]"))))
    ;; defaults ?
    (when-not (empty? dft)
      (.write cout (str "(" dft ")")) )
    (doto cout (.write " ")(.flush))
    ;; get the input from user
    ;; return the next question, :end ends it
    (->> (readData cout cin)
         (onAnswer cout cmdQ props))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- popQ ""

  [cout cin cmdQ props]

  (if (some? cmdQ)
    (popQQ cout cin cmdQ props)
    :end))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cycleQ ""

  [cout cin cmdQNs start props]

  (loop [rc (popQ cout
                  cin
                  (cmdQNs start) props) ]
    (cond
      (= :end rc) @props
      (nil? rc) {}
      :else (recur (popQ cout
                         cin
                         (cmdQNs rc) props)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CLIConverse

  "Prompt a sequence of questions via console"

  [cmdQs question1]

  {:pre [(map? cmdQs)]}

  (let [cout (->> (BufferedOutputStream. (System/out))
                  (OutputStreamWriter. ))
        kp (if (IsWindows?) "<Ctrl-C>" "<Ctrl-D>")
        cin (InputStreamReader. (System/in))
        func (partial cycleQ cout cin) ]
    (.write cout (str ">>> Press "
                      kp "<Enter> to cancel...\n"))
    (->
      (reduce
        (fn [memo k]
          (assoc memo k (assoc (get cmdQs k) :id k)))
        {}
        (keys cmdQs))
      (func question1 (atom {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(comment
(def QM
  {:q1 {:question "hello ken"
        :choices "q|b|c"
        :default "c"
        :required true
        :result :a1
        :next :q2}

   :q2 {:question "hello paul"
        :result :a2
        :next :q3}

   :q3 {:question "hello joe"
        :choices "2"
        :result (fn [answer result]
                  [:end (assoc result :zzz answer)])}})
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

