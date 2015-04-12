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
        [czlabclj.xlib.util.wfs]
        [czlabclj.tardis.etc.cmd1])

  (:import  [com.zotohlab.skaro.etc CmdHelpError]
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
(defn- getCmdInfo ""

  [rcb]

  [[(RStr rcb "usage.cmdline") ""]
   [(RStr rcb "usage.new") (RStr rcb "usage.new.desc")]
   [(RStr rcb "usage.podify")  (RStr rcb "usage.podify.desc")]

   [(RStr rcb "usage.ide") (RStr rcb "usage.ide.desc")]
   [(RStr rcb "usage.build") (RStr rcb "usage.build.desc")]
   [(RStr rcb "usage.test") (RStr rcb "usage.test.desc")]

   [(RStr rcb "usage.debug") (RStr rcb "usage.debug.desc")]
   [(RStr rcb "usage.start") (RStr rcb "usage.start.desc")]

   [(RStr rcb "usage.gen.keypair") (RStr rcb "usage.gen.keypair.desc")]
   [(RStr rcb "usage.gen.key") (RStr rcb "usage.gen.key.desc")]
   [(RStr rcb "usage.gen.pwd") (RStr rcb "usage.gen.pwd.desc")]
   [(RStr rcb "usage.gen.csr") (RStr rcb "usage.gen.csr.desc")]
   [(RStr rcb "usage.gen.guid") (RStr rcb "usage.gen.guid.desc")]
   [(RStr rcb "usage.encrypt") (RStr rcb "usage.encrypt.desc")]
   [(RStr rcb "usage.decrypt") (RStr rcb "usage.decrypt.desc")]
   [(RStr rcb "usage.hash") (RStr rcb "usage.hash.desc")]
   [(RStr rcb "usage.testjce") (RStr rcb "usage.testjce.desc")]

   [(RStr rcb "usage.demo") (RStr rcb "usage.demo.desc")]
   [(RStr rcb "usage.version") (RStr rcb "usage.version.desc")]
   [(RStr rcb "usage.help") ""]])

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

  (let [strs (getCmdInfo (I18N/getBase))
        h (take 1 strs)
        e (take-last 1 strs)
        b (drop-last (drop 1 strs))]
    (println (MakeString \= 78))
    (drawHelp "> %-35s %s\n" h)
    (println "> -----------------")
    (drawHelp "> %-35s %s\n" b)
    (println ">")
    (drawHelp "> %-35s %s\n" e)
    (println (MakeString \= 78))
  ))

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

  (SimPTask (fn [_])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cmdStart ""

  ^Activity
  []

  (SimPTask
    (fn [^Job j]
      (try
        (let [args (.getLastResult j)]
          (when (< (count args) 1) (throw (CmdHelpError. ""))))
        (catch CmdHelpError e# (Usage))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype CmdDelegate [] PDelegate

  (onStop [_ p] (-> (.core p) (.dispose)))
  (onError [_ err cur] (Nihil.))
  (startWith [_ p]
    (require 'czlabclj.tardis.etc.core)
    (-> (cmdStart)
        (.chain (parseArgs))
        (.chain (execArgs)))))

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

