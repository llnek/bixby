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
    [czlab.xlib.core :refer [inst? trap! prtStk test-cond muble<>]]
    [czlab.xlib.resources :refer [getResource rstr rstr*]]
    [czlab.xlib.str :refer [str<>]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.files :refer [dirRead?]])

  (:use [czlab.skaro.etc.cmd2]
        [czlab.xlib.consts]
        [czlab.skaro.etc.cmd1])

  (:import
    [czlab.skaro.etc CmdHelpError]
    [java.io File]
    [czlab.xlib I18N]
    [java.util ResourceBundle List Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCmdInfo

  ""
  [rcb]

  (partition 2
    (rstr* rcb
           ["usage.cmdline"] []
           ["usage.new"] ["usage.new.desc"]
           ["usage.podify"] ["usage.podify.desc"]
           ["usage.ide"] [ "usage.ide.desc"]
           ["usage.build"] [ "usage.build.desc"]
           ["usage.test"] [ "usage.test.desc"]

           ["usage.debug"] ["usage.debug.desc"]
           ["usage.start"] ["usage.start.desc"]

           ["usage.gen.keypair"] [ "usage.gen.keypair.desc"]
           ["usage.gen.key"] [ "usage.gen.key.desc"]
           ["usage.gen.pwd"] [ "usage.gen.pwd.desc"]
           ["usage.gen.csr"] [ "usage.gen.csr.desc"]
           ["usage.gen.guid"] [ "usage.gen.guid.desc"]
           ["usage.encrypt"] [ "usage.encrypt.desc"]
           ["usage.decrypt"] [ "usage.decrypt.desc"]
           ["usage.hash"] [ "usage.hash.desc"]
           ["usage.testjce"] ["usage.testjce.desc"]

           ["usage.demo"] [ "usage.demo.desc"]
           ["usage.version"] [ "usage.version.desc"]
           ["usage.help"] [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- drawHelp

  ""
  [fmt arr]

  (doseq [a arr]
    (print (apply format fmt a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn usage

  ""
  []

  (let [strs (getCmdInfo (I18N/base))
        b (drop-last (drop 1 strs))
        h (take 1 strs)
        e (take-last 1 strs)]
    (println (str<> \= 78))
    (drawHelp " %-35s %s\n" h)
    (println " -----------------")
    (drawHelp " %-35s %s\n" b)
    (println "")
    (drawHelp " %-35s %s\n" e)
    (println (str<> \= 78))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- execArgs

  ""
  [args]

  (let [cmd (first args)
        args (vec (drop 1 args))]
    (case cmd
      :new (onCreate args)
      :ide (onIDE args)
      :make (onBuild args)
      :podify (onPodify args)
      :test (onTest args)
      :debug (onDebug args)
      :start (onStart args)
      :demos (onDemos args)
      :generate (onGenerate args)
      :encrypt (onEncrypt args)
      :decrypt (onDecrypt args)
      :hash (onHash args)
      :testjce (onTestJCE args)
      :version (onVersion args)
      (onHelp args))
    args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseArgs "Do nothing right now" [args] args)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cmdStart

  "Make sure cmdline args are ok"
  [args]

  (when (< (count args) 1) (trap! CmdHelpError ))
  args)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootAndRun

  ""
  [^File home ^ResourceBundle rcb & args]

  (binding [*skaro-home* home
            *skaro-rb* rcb]
    (try
      (-> args
          (comp execArgs
                parseArgs
                cmdStart))
      (catch Throwable e
        (if (inst? CmdHelpError e)
          (usage)
          (prtStk e))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


