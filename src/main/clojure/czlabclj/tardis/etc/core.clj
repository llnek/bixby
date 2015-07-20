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
      :author "kenl" }

  czlabclj.tardis.etc.core

  (:require [czlabclj.xlib.i18n.resources :refer [GetResource RStr RStr*]]
            [czlabclj.xlib.util.core :refer [test-cond MakeMMap]]
            [czlabclj.xlib.util.str :refer [MakeString]]
            [czlabclj.xlib.util.scheduler :refer [NulScheduler]]
            [czlabclj.xlib.util.files :refer [DirRead?]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.consts]
        [czlabclj.xlib.util.wfs]
        [czlabclj.tardis.etc.cmd2]
        [czlabclj.tardis.etc.cmd1])

  (:import  [com.zotohlab.frwk.server ServiceHandler ServerLike]
            [com.zotohlab.skaro.etc CmdHelpError]
            [com.zotohlab.wflow Activity
             WorkFlowEx Nihil
             Job Switch]
            [com.zotohlab.frwk.i18n I18N]
            [java.util ResourceBundle List Locale]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCmdInfo ""

  [rcb]

  (partition 2
    (RStr* rcb
           [ "usage.cmdline"] []
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
    (print (apply format fmt a))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Usage ""

  []

  (let [strs (getCmdInfo (I18N/getBase))
        b (drop-last (drop 1 strs))
        h (take 1 strs)
        e (take-last 1 strs)]
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
                        #(keyword (first (.getLastResult ^Job %)))))
    (.withChoice :new (SimPTask #(OnCreate %)))
    (.withChoice :ide (SimPTask #(OnIDE %)))
    (.withChoice :make (SimPTask #(OnBuild %)))
    (.withChoice :podify (SimPTask #(OnPodify %)))
    (.withChoice :test (SimPTask #(OnTest %)))
    (.withChoice :debug (SimPTask #(OnDebug %)))
    (.withChoice :start (SimPTask #(OnStart %)))
    (.withChoice :demos (SimPTask #(OnDemos %)))
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
(defn- parseArgs "Do nothing right now."

  ^Activity
  []

  (SimPTask (fn [_])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cmdStart "Make sure cmdline args are ok."

  ^Activity
  []

  (SimPTask
    (fn [^Job j]
      (let [args (.getLastResult j)]
        (when (< (count args) 1) (throw (CmdHelpError. "")))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootAndRun ""

  [^File home ^ResourceBundle rcb & args]

  (let [wf (reify WorkFlowEx
             (startWith [_]
               (-> (cmdStart)
                   (.chain (parseArgs))
                   (.chain (execArgs))))
             (onError [_ e] (Usage)))]
    (reset! SKARO-HOME-DIR home)
    (reset! SKARO-RSBUNDLE rcb)
    (-> ^ServiceHandler
        (FlowServer (NulScheduler) {})
        (.handle wf {:home home
                     :rcb rcb
                     JS_LAST args}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

