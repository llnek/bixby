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


(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.etc.core

  (:require
    [czlab.xlib.core :refer [trap! prtStk test-cond mubleObj!]]
    [czlab.xlib.resources :refer [getResource rstr rstr*]]
    [czlab.xlib.str :refer [makeString]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.scheduler :refer [mkNulScheduler]]
    [czlab.xlib.files :refer [dirRead?]])

  (:use [czlab.skaro.core.wfs]
        [czlab.xlib.consts]
        [czlab.skaro.etc.cmd2]
        [czlab.skaro.etc.cmd1])

  (:import
    [czlab.wflow.server ServiceHandler ServerLike]
    [czlab.skaro.etc CmdHelpError]
    [java.io File]
    [czlab.wflow.dsl Activity
     WorkFlowEx
     Nihil
     Job
     Switch]
    [czlab.xlib I18N]
    [java.util ResourceBundle List Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCmdInfo ""

  [rcb]

  (partition 2
    (rstr* rcb
           ["usage.cmdline"] []
           ["usage.new"] ["usage.new.desc"]
           ["usage.podify"] ["usage.podify.desc"]
           ["usage.ide"] [ "usage.ide.desc"]
           [ "usage.build"] [ "usage.build.desc"]
           [ "usage.test"] [ "usage.test.desc"]

           [ "usage.debug"] ["usage.debug.desc"]
           [ "usage.start"] [ "usage.start.desc"]

           [ "usage.gen.keypair"] [ "usage.gen.keypair.desc"]
           [ "usage.gen.key"] [ "usage.gen.key.desc"]
           [ "usage.gen.pwd"] [ "usage.gen.pwd.desc"]
           [ "usage.gen.csr"] [ "usage.gen.csr.desc"]
           [ "usage.gen.guid"] [ "usage.gen.guid.desc"]
           [ "usage.encrypt"] [ "usage.encrypt.desc"]
           [ "usage.decrypt"] [ "usage.decrypt.desc"]
           [ "usage.hash"] [ "usage.hash.desc"]
           [ "usage.testjce"] ["usage.testjce.desc"]

           [ "usage.demo"] [ "usage.demo.desc"]
           [ "usage.version"] [ "usage.version.desc"]
           [ "usage.help"] [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- drawHelp ""

  [fmt arr]

  (doseq [a arr]
    (print (apply format fmt a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn usage ""

  []

  (let [strs (getCmdInfo (I18N/getBase))
        b (drop-last (drop 1 strs))
        h (take 1 strs)
        e (take-last 1 strs)]
    (println (makeString \= 78))
    (drawHelp " %-35s %s\n" h)
    (println " -----------------")
    (drawHelp " %-35s %s\n" b)
    (println "")
    (drawHelp " %-35s %s\n" e)
    (println (makeString \= 78))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- execArgs ""

  ^Activity
  []

  (doto (Switch/apply (defChoiceExpr
                        #(keyword (first (.getLastResult ^Job %)))))
    (.withChoice :new (simPTask #(onCreate %)))
    (.withChoice :ide (simPTask #(onIDE %)))
    (.withChoice :make (simPTask #(onBuild %)))
    (.withChoice :podify (simPTask #(onPodify %)))
    (.withChoice :test (simPTask #(onTest %)))
    (.withChoice :debug (simPTask #(onDebug %)))
    (.withChoice :start (simPTask #(onStart %)))
    (.withChoice :demos (simPTask #(onDemos %)))
    (.withChoice :generate (simPTask #(onGenerate %)))
    (.withChoice :encrypt (simPTask #(onEncrypt %)))
    (.withChoice :decrypt (simPTask #(onDecrypt %)))
    (.withChoice :hash (simPTask #(onHash %)))
    (.withChoice :testjce (simPTask #(onTestJCE %)))
    (.withChoice :version (simPTask #(onVersion %)))
    (.withChoice :help (simPTask #(onHelp %)))
    (.withDft (simPTask #(onHelp %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseArgs

  "Do nothing right now"

  ^Activity
  []

  (simPTask (fn [_])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cmdStart

  "Make sure cmdline args are ok"

  ^Activity
  []

  (simPTask
    (fn [^Job j]
      (let [args (.getLastResult j)]
        (when (< (count args) 1) (trap! CmdHelpError ))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootAndRun ""

  [^File home ^ResourceBundle rcb & args]

  (let [wf (reify WorkFlowEx
             (startWith [_]
               (-> (cmdStart)
                   (.chain (parseArgs))
                   (.chain (execArgs))))
             (onError [_ e]
               (if
                 (instance? CmdHelpError e)
                 (usage)
                 (prtStk e))
               (Nihil/apply))) ]
    (setGlobals! :homeDir home)
    (setGlobals! :rcb rcb)
    (-> ^ServiceHandler
        (flowServer (mkNulScheduler) {})
        (.handle wf {:home home
                     :rcb rcb
                     JS_LAST args}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


