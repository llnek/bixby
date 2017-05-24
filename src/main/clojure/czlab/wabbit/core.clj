;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.core

  (:gen-class)

  (:require [czlab.basal.resources :as r :refer [loadResource getResource]]
            [czlab.basal.io :as i :refer [closeQ readAsStr writeFile]]
            [czlab.wabbit.exec :as e :refer [execvisor<>]]
            [czlab.basal.scheduler :as u :refer [scheduler<>]]
            [czlab.basal.meta :as m :refer [setCldr getCldr]]
            [czlab.basal.format :as f :refer [readEdn]]
            [czlab.basal.process :as p]
            [io.aviso.ansi :as ansi]
            [czlab.basal.log :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.wabbit.base :as b]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s])

  (:import [czlab.jasal
            Startable
            CU
            I18N
            Initable
            Disposable]
           [clojure.lang Atom APersistentMap]
           [czlab.basal Cljrt]
           [java.io File]
           [java.util ResourceBundle Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private stopcli (volatile! false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI "" [exec gist]

  (let [{:keys [pidFile
                killSvr]} @gist]
    (when-not @stopcli
      (vreset! stopcli true)
      (print "\n\n")
      (log/info "unhooking remote shutdown...")
      (if (fn? killSvr) (killSvr))
      (log/info "remote hook closed - ok")
      (log/info "about to stop wabbit...")
      (log/info "wabbit is shutting down...")
      (.stop ^Startable exec)
      (.dispose ^Disposable exec)
      (shutdown-agents)
      (log/info "wabbit stopped"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookToKill "" [cb cfg]
  (with-open [rts (Cljrt/newrt)]
    (let [v "czlab.wabbit.plugs.http/Discarder!"]
      (.callEx rts
               v
               (c/vargs* Object cb cfg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableKillHook "" [exec gist]
  (let [[h p] (-> "wabbit.kill.port" c/sysProp str (.split ":"))
        m {:host "localhost" :port 4444 :threads 1}
        m (if (s/hgl? h) (assoc m :host h) m)
        m (if (s/hgl? p) (assoc m :port (c/convLong p 4444)) m)]
    (log/info "enabling remote shutdown hook: %s" m)
    (swap! gist
           assoc
           :killSvr
           (hookToKill #(stopCLI exec gist) m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defn- primodial "" [gist]
  (log/info "\n%s\n%s\n%s"
            (s/str<> 78 \=)
            "inside primodial()" (s/str<> 78 \=))
  ;;(log/info "exec = czlab.wabbit.exec.Execvisor")
  (c/do-with [e (e/execvisor<>)]
    (swap! gist
           assoc
           :stop! #(stopCLI e gist))
    (.init ^Initable e @gist)
    (log/info "\n%s\n%s\n%s"
              (s/str<> 78 \*)
              "starting wabbit..." (s/str<> 78 \*))
    (.start ^Startable e)
    (log/info "wabbit started")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaConfig ""

  ([cwd confObj] (startViaConfig cwd confObj false))
  ([cwd confObj join?]
   (let
     [{:keys [locale info]}
      confObj
      cn (s/stror (:country locale) "US")
      ln (s/stror (:lang locale) "en")
      verStr (some-> b/c-verprops
                     r/loadResource
                     (.getString "version"))
      fp (io/file cwd "wabbit.pid")
      loc (Locale. ln cn)
      rc (r/getResource b/c-rcb loc)
      ctx (atom
            {:encoding (s/stror (:encoding info) "utf-8")
             :wabbit {:version (str verStr) }
             :homeDir (io/file cwd)
             :pidFile fp
             :locale loc
             :conf confObj})
      cz (m/getCldr)]
     (log/info "wabbit.user.dir = %s" (c/fpath cwd))
     (log/info "wabbit.version = %s" verStr)
     (c/doto->> rc
                (c/test-some "base resource" ) I18N/setBase)
     (log/info "wabbit's i18n#base loaded")
     (let [exec (primodial ctx)]
       (doto fp (i/writeFile (p/processPid)) .deleteOnExit)
       (log/info "wrote wabbit.pid - ok")
       (enableKillHook exec ctx)
       (p/exitHook #(stopCLI exec ctx))
       (log/info "added shutdown hook")
       (log/info "app-loader: %s" (type cz))
       (log/info "sys-loader: %s"
                 (type (.getParent cz)))
       (log/debug "%s" @ctx)
       (log/info "wabbit is now running...")
       (when join?
         (while (not @stopcli) (c/pause 3000))
         (log/info "vm shut down")
         (log/info "(bye)")
         (shutdown-agents))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCons "" [cwd]
  (println (ansi/bold-yellow (b/bannerText)))
  (b/precondFile (io/file cwd b/cfg-pod-cf))
  (startViaConfig cwd
                  (b/slurpXXXConf cwd b/cfg-pod-cf true) true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "" [& args]
  (let [p2 (second args)
        p1 (first args)]
    (c/do-with [dir
                (if (or (= "--home" p1)(= "-home" p1))
                  (io/file p2)
                  (io/file (c/getCwd)))]
               (c/sysProp! "wabbit.user.dir" (c/fpath dir))
               (startViaCons dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


