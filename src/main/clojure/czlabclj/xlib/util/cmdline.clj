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

  czlabclj.xlib.util.cmdline

  (:require
    [czlabclj.xlib.util.core :refer [IntoMap IsWindows?] ]
    [czlabclj.xlib.util.str :refer [strim nsb Has?] ])

  (:require
    [czlabclj.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:import
    [java.io BufferedOutputStream
     InputStreamReader OutputStreamWriter]
    [java.io Reader Writer]
    [java.util Map HashMap]
    [org.apache.commons.lang3 StringUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;(defrecord CmdSeqQ [qid qline choices dft must onok] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeCmdSeqQ

  "Make a command line question"

  [^String questionId
   ^String questionLine
   ^String choices
   ^String defaultValue
   mandatory
   fnOK ]

  {:choices choices
   :qline questionLine
   :qid questionId
   :dft defaultValue
   :must mandatory
   :onok fnOK } )

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
      (strim buf))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- popQQ ""

  [^Writer cout
   ^Reader cin
   cmdQ
   ^java.util.Map props]

  (let [chs (strim (:choices cmdQ))
        dft (strim (:dft cmdQ))
        must (:must cmdQ)
        onResp (:onok cmdQ)
        q (strim (:qline cmdQ))]
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
    ;; point to next question, blank ends it
    (let [rc (readData cout cin) ]
      (if (nil? rc)
        (do (.write cout "\n") nil)
        (onResp (if (cs/blank? rc) dft rc) props)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- popQ ""

  [cout cin cmdQ props]

  (if (some? cmdQ)
    (popQQ cout cin cmdQ props)
    ""
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cycleQ ""

  ;; map
  [cout cin cmdQNs start props]

  (loop [rc (popQ cout cin (cmdQNs start) props) ]
    (cond
      (nil? rc) {}
      (cs/blank? rc) (IntoMap props)
      :else (recur (popQ cout cin (cmdQNs rc) props)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CLIConverse

  "Prompt a sequence of questions via console"

  ;; map
  [cmdQs question1]

  (let [cout (OutputStreamWriter. (BufferedOutputStream. (System/out)))
        kp (if (IsWindows?) "<Ctrl-C>" "<Ctrl-D>")
        cin (InputStreamReader. (System/in)) ]
    (.write cout (str ">>> Press " kp "<Enter> to cancel...\n"))
    (cycleQ cout cin cmdQs question1 (HashMap.))
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
;;EOF

