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

  cmzlabclj.tardis.etc.core

  (:gen-class)

  (:require [clojure.tools.logging :as log :only [warn error info debug]]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.i18n.resources :only [GetResource GetString]]
        [cmzlabclj.nucleus.util.core :only [test-cond]]
        [cmzlabclj.nucleus.util.str :only [MakeString]]
        [cmzlabclj.nucleus.util.files :only [DirRead?]]
        [cmzlabclj.tardis.etc.cmdline :only [GetCommands EvalCommand]])

  (:import  [com.zotohlab.gallifrey.etc CmdHelpError]
            [java.util List Locale]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private CMDLINE-INFO [
  ["new [mvc|jetty|basic] <app-name> "  "e.g. new mvc foo"]
  ["podify <app-name>"  "e.g. package app as a pod file"]

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
(defn- usage ""

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
;; arg(0) is skaro-home
;;println("#### apprunner loader = " + getClass().getClassLoader().getClass().getName())
;;println("#### sys loader = " + ClassLoader.getSystemClassLoader().getClass().getName())
;;mkCZldrs(home)
(defn- parseArgs "Returns false or a valid function to execute."

  [rcb & args]

  (let [h (File. ^String (first args)) ]
    (test-cond (GetString rcb "skaro.home.none" [h]) (DirRead? h))
    (if (not (contains? (GetCommands) (keyword (nth args 1))))
      false
      #(apply EvalCommand h rcb (drop 1 args)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "Main Entry"

  [& args]

  ;;(debug "Skaro: Main Entry")
  ;; for security, don't just eval stuff
  ;;(alter-var-root #'*read-eval* (constantly false))
  (let [rcb (GetResource "cmzlabclj/tardis/etc/Resources"
                         (Locale/getDefault)) ]
    (try
      (when (< (count args) 2) (throw (CmdHelpError. "")))
      (if-let [rc (apply parseArgs rcb args) ]
        (rc)
        (throw (CmdHelpError. "")))
      (catch CmdHelpError e# (usage)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

