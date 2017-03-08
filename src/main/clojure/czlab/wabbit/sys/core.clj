;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.sys.core

  (:gen-class)

  (:require [czlab.basal.resources :refer [loadResource getResource]]
            [czlab.basal.io :refer [closeQ readAsStr writeFile]]
            [czlab.wabbit.sys.exec :refer [execvisor<>]]
            [czlab.basal.scheduler :refer [scheduler<>]]
            [czlab.basal.meta :refer [setCldr getCldr]]
            [czlab.basal.format :refer [readEdn]]
            [czlab.basal.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.base.core]
        [czlab.basal.process]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [czlab.jasal Startable CU Muble I18N]
           [clojure.lang Atom APersistentMap]
           [czlab.wabbit.sys Execvisor]
           [czlab.wabbit.base Cljshim]
           [java.io File]
           [java.util ResourceBundle Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private stopcli (volatile! false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI "" [gist]

  (let [{:keys [pidFile
                exec
                killSvr]} @gist]
    (when-not @stopcli
      (vreset! stopcli true)
      (print "\n\n")
      (log/info "unhooking remote shutdown...")
      (if (fn? killSvr) (killSvr))
      (log/info "remote hook closed - ok")
      (log/info "about to stop wabbit...")
      (log/info "wabbit is shutting down...")
      (when-some [e (cast? Execvisor exec)]
        (.stop e)
        (.dispose e))
      (shutdown-agents)
      (log/info "wabbit stopped"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookToKill "" [gist cb cfg]
  (let-try [rt (Cljshim/newrt (getCldr))]
    (. rt callEx
       (str "czlab.wabbit.plugs."
            "io.http/Discarder!")
       (vargs* Object cb cfg)) (finally (.close rt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableKillHook "" [gist]
  (let [[h p] (-> "wabbit.kill.port" sysProp str (.split ":"))
        m {:host "localhost" :port 4444 :threads 2}
        m (if (hgl? h) (assoc m :host h) m)
        m (if (hgl? p) (assoc m :port (convLong p 4444)) m)]
    (log/info "enabling remote shutdown hook: %s" m)
    (swap! gist
           assoc
           :killSvr
           (hookToKill gist #(stopCLI gist) m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defn- primodial "" [gist]
  (log/info "\n%s\n%s\n%s"
            (str<> 78 \=)
            "inside primodial()" (str<> 78 \=))
  (log/info "exec = czlab.wabbit.sys.Execvisor")
  (let [e (execvisor<>)]
    (swap! gist
           assoc
           :exec e
           :stop! #(stopCLI gist))
    (.init e @gist)
    (log/info "\n%s\n%s\n%s"
              (str<> 78 \*)
              "starting wabbit..." (str<> 78 \*))
    (.start e {})
    (log/info "wabbit started")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCons "" [cwd]
  (let
    [_ (precondFile (io/file cwd cfg-pod-cf))
     {:keys [locale info] :as conf}
     (slurpXXXConf cwd cfg-pod-cf true)
     cn (stror (:country locale) "US")
     ln (stror (:lang locale) "en")
     verStr (some-> c-verprops
                    loadResource (.getString "version"))
     fp (io/file cwd "wabbit.pid")
     loc (Locale. ln cn)
     rc (getResource c-rcb loc)
     ctx (atom
           {:encoding (stror (:encoding info) "utf-8")
            :wabbit {:version (str verStr) }
            :homeDir (io/file cwd)
            :pidFile fp
            :conf conf
            :locale loc})
     cz (getCldr)]
    (log/info "wabbit.user.dir = %s" (fpath cwd))
    (log/info "wabbit.version = %s" verStr)
    (doto->> rc
             (test-some "base resource" )
             I18N/setBase )
    (log/info "wabbit's i18n#base loaded")
    (primodial ctx)
    (doto fp (writeFile (processPid)) .deleteOnExit)
    (log/info "wrote wabbit.pid - ok")
    (enableKillHook ctx)
    (exitHook #(stopCLI ctx))
    (log/info "added shutdown hook")
    (log/info "app-loader: %s" (type cz))
    (log/info "sys-loader: %s"
              (type (.getParent cz)))
    (log/debug "%s" @ctx)
    (log/info "wabbit is now running...")
    (while (not @stopcli) (pause 3000))
    (log/info "vm shut down")
    (log/info "(bye)")
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "" [& args]
  (let
    [cwd (getCwd)
     dir (io/file cwd)]
    (sysProp! "wabbit.user.dir" (fpath dir))
    (startViaCons dir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


