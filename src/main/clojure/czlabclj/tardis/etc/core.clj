;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.etc.core

  (:require [clojure.tools.logging :as log :only [warn error info debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.i18n.resources :only [GetResource RStr]]
        [czlabclj.xlib.util.core :only [test-cond MakeMMap]]
        [czlabclj.xlib.util.str :only [MakeString]]
        [czlabclj.xlib.util.scheduler :only [MakeScheduler]]
        [czlabclj.xlib.util.files :only [DirRead?]]
        [czlabclj.tardis.core.wfs]
        [czlabclj.tardis.etc.cmd1])

  (:import  [com.zotohlab.gallifrey.etc CmdHelpError]
            [com.zotohlab.frwk.server ServerLike]
            [com.zotohlab.wflow Activity Pipeline
             Nihil
             Job PDelegate Switch]
            [com.zotohlab.frwk.i18n I18N]
            [java.util ResourceBundle List Locale]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private CMDLINE-INFO [
  ["new [mvc|jetty|basic] <app-name> "  "e.g. new mvc foo"]
  ["podify <app-name>"  "Package app as a pod file"]

  ["ide eclipse <app-name>" "Generate eclipse project files."]
  ["build <app-name> [target]" "Build app."]
  ["test <app-name>" "Run test cases."]

  ["debug" "Start & debug the application."]
  ["start [bg]" "Start the application."]

  ["generate keypair <length>" "Generate a pair of private/public keys."]
  ["generate serverkey" "Create self-signed server key (pkcs12)."]
  ["generate password" "Generate a random password."]
  ["generate csr" "Create a Certificate Signing Request."]
  ["generate guid" "Generate a RFC4122 compliant UUID."]
  ["encrypt <password> <clear-text>" "e.g. encrypt SomeSecretData"]
  ["decrypt <password> <cipher-text>" "e.g. decrypt Data"]
  ["hash <password>" "e.g. hash SomePassword"]
  ["testjce" "Check JCE  Policy Files."]

  ["demo samples" "Generate a set of samples."]
  ["version" "Show version info."]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- drawHelp ""

  [fmt arr]

  (doseq [[k v] (seq arr) ]
    (print (String/format ^String fmt
                          (into-array Object [k v]) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Usage ""

  []

  (println (MakeString \= 78))
  (println "> skaro <commands & options>")
  (println "> -----------------")
  (drawHelp "> %-35s %s\n" CMDLINE-INFO )
  (println ">")
  (println "> help - show standard commands")
  (println (MakeString \= 78)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- execArgs ""

  ^Activity
  []

  (doto (Switch/apply (DefChoiceExpr
                        (fn [^Job j]
                          (keyword (first (.getLastResult j))))))
    (.withChoice :new (SimPTask #(OnCreate %)))
    (.withChoice :ide (SimPTask #(OnIDE %)))
    (.withChoice :build (SimPTask #(OnBuild %)))
    (.withChoice :podify (SimPTask #(OnPodify %)))
    (.withChoice :test (SimPTask #(OnTest %)))
    (.withChoice :debug (SimPTask #(OnDebug %)))
    (.withChoice :start (SimPTask #(OnStart %)))
    (.withChoice :demo (SimPTask #(OnDemo %)))
    (.withChoice :generate (SimPTask #(OnGenerate %)))
    (.withChoice :encrypt (SimPTask #(OnEncrypt %)))
    (.withChoice :decrypt (SimPTask #(OnDecrypt %)))
    (.withChoice :hash (SimPTask #(OnHash %)))
    (.withChoice :testjce (SimPTask #(OnTestJCE %)))
    (.withChoice :version (SimPTask #(OnVersion %)))
    (.withChoice :help (SimPTask #(OnHelp %)))
    (.withDft (SimPTask #(OnHelp %)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; arg(0) is skaro-home
;;println("#### apprunner loader = " + getClass().getClassLoader().getClass().getName())
;;println("#### sys loader = " + ClassLoader.getSystemClassLoader().getClass().getName())
;;mkCZldrs(home)
(defn- parseArgs ""

  ^Activity
  []

  (DefPTask
    (fn [_ ^Job j _])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cmdStart ""

  ^Activity
  []

  (DefPTask
    (fn [_ ^Job j _]
      (try
        (let [args (.getLastResult j)]
          (when (< (count args) 1) (throw (CmdHelpError. ""))))
        (catch CmdHelpError e# (Usage))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype CmdDelegate [] PDelegate
  (onStop [_ p]
    ;;(log/debug "CmdDelegate onstop ------------ ending!!!!!")
    (-> (.core p) (.dispose)))
  (onError [_ err cur] (Nihil.))
  (startWith [_ p]
    (require 'czlabclj.tardis.etc.core)
    (-> (cmdStart)
        (.ensue (parseArgs))
        (.ensue (execArgs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootAndRun ""

  [^File home ^ResourceBundle rcb args]

  (let [cz "czlabclj.tardis.etc.core.CmdDelegate"
        ctr (PseudoServer)
        job (PseudoJob ctr)
        pipe (Pipeline. "Cmdline" cz job false)]
    (reset! SKARO-HOME-DIR home)
    (reset! SKARO-RSBUNDLE rcb)
    (.setLastResult job args)
    (.setv job :home home)
    (.setv job :rcb rcb)
    (.start pipe)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

