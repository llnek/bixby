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

(ns ^{  :doc "Functions to enable console questions."
        :author "kenl" }

  comzotohlabscljc.util.cmdline

  (:require [clojure.tools.logging :as log :only [info warn error debug]])
  (:require [ clojure.string :as cstr ])
  (:use [ comzotohlabscljc.util.core :only [IntoMap IsWindows?] ])
  (:use [ comzotohlabscljc.util.str :only [strim nsb Has?] ])
  (:import (java.io BufferedOutputStream InputStreamReader
                    OutputStreamWriter))
  (:import (java.io Reader Writer))
  (:import (java.util Map HashMap))
  (:import (org.apache.commons.lang3 StringUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;(defrecord CmdSeqQ [qid qline choices dft must onok] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeCmdSeqQ "Make a command line question."

  [ ^String questionId
    ^String questionLine
    ^String choices
    ^String defaultValue
    mandatory
    fnOK ]

  {
    :choices choices
    :qline questionLine
    :qid questionId
    :dft defaultValue
    :must mandatory
    :onok fnOK } )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readData "Read user input."

  ^String
  [^Writer cout ^Reader cin]

  ;; windows has '\r\n' linux has '\n'

  (let [ buf (StringBuilder.)
         ms (loop [ c (.read cin) ]
              (let [ m (cond
                         (or (= c -1)(= c 4)) #{ :quit :break }
                         (= c (int \newline)) #{ :break }
                         (or (= c (int \return))
                             (= c (int \backspace)) (= c 27)) #{}
                         :else (do (.append buf (char c)) #{})) ]
                (if (contains? m :break)
                  m
                  (recur (.read cin))))) ]
    (if (contains? ms :quit) nil (strim (.toString buf)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- popQQ ""

  [^Writer cout ^Reader cin cmdQ ^java.util.Map props]

  (let [ chs (nsb (:choices cmdQ))
         dft (nsb (:dft cmdQ))
         must (:must cmdQ)
         onResp (:onok cmdQ)
         q (:qline cmdQ) ]
    (.write cout (str q (if must "*" "" ) " ? "))
    ;; choices ?
    (when-not (cstr/blank? chs)
      (if (Has? chs \n)
        (.write cout (str
              (if (.startsWith chs "\n") "[" "[\n")  chs
              (if (.endsWith chs "\n") "]" "\n]" ) ))
        (.write cout (str "[" chs "]"))))
    ;; defaults ?
    (when-not (cstr/blank? dft)
      (.write cout (str "(" dft ")")) )
    (doto cout (.write " ")(.flush))
    ;; get the input from user
    ;; point to next question, blank ends it
    (let [ rc (readData cout cin) ]
      (if (nil? rc)
        (do (.write cout "\n") nil )
        (do (onResp (if (cstr/blank? rc) dft rc) props))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- popQ ""

  [^Writer cout ^Reader cin cmdQ ^java.util.Map props]

  (if (nil? cmdQ)
    ""
    (popQQ cout cin cmdQ props)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cycleQ ""

  ;; map
  [^Writer cout ^Reader cin cmdQNs ^String start ^java.util.Map props]

  (do
    (loop [ rc (popQ cout cin (get cmdQNs start) props) ]
      (cond
        (nil? rc) {}
        (cstr/blank? rc) (IntoMap props)
        :else (recur (popQ cout cin (get cmdQNs rc) props))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CLIConverse "Prompt a sequence of questions via console."

  ;; map
  [cmdQs question1]

  (let [ cout (OutputStreamWriter. (BufferedOutputStream. (System/out)))
         kp (if (IsWindows?) "<Ctrl-C>" "<Ctrl-D>")
         cin (InputStreamReader. (System/in)) ]
    (.write cout (str ">>> Press " kp "<Enter> to cancel...\n"))
    (cycleQ cout cin cmdQs ^String question1 (HashMap.))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(comment
(def q1 (MakeCmdSeqQ "q1" "hello ken" "q|b|c" "c" true
           (fn [a ps]
             (do (.put ps "a1" a) "q2")) ) )
(def q2 (MakeCmdSeqQ "q2" "hello paul" "" "" false
           (fn [a ps]
             (do (.put ps "a2" a) "q3"))) )
(def q3 (MakeCmdSeqQ "q3" "hello joe" "z" "" false
           (fn [a ps]
             (do (.put ps "a3" a) "" ))) )
(def QM { "q1" q1 "q2" q2 "q3" q3 })
)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cmdline-eof nil)

