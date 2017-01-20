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

  (:require [czlab.xlib.resources :refer [loadResource getResource]]
            [czlab.xlib.io :refer [closeQ readAsStr writeFile]]
            [czlab.wabbit.io.http :refer [cfgShutdownServer]]
            [czlab.wabbit.sys.exec :refer [execvisor<>]]
            [czlab.xlib.scheduler :refer [scheduler<>]]
            [czlab.xlib.meta :refer [setCldr getCldr]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.process]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.consts])

  (:import [czlab.xlib Startable CU Muble I18N]
           [czlab.wabbit.server CljPodLoader]
           [clojure.lang Atom APersistentMap]
           [czlab.wabbit.server Execvisor]
           [czlab.wabbit.base ConfigError]
           [java.io File]
           [java.util ResourceBundle Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private stopcli (volatile! false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cliGist
  ""
  ^Atom
  [{:keys [podDir] :as gist}]
  (precondFile (io/file podDir cfg-pod-cf))
  (atom gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI
  "Stop all apps and services"
  ^Atom
  [^Atom gist]
  (let [{:keys [pidFile
                execv
                killSvr]} @gist]
    (when-not @stopcli
      (vreset! stopcli true)
      (print "\n\n")
      (log/info "closing the remote shutdown hook")
      (if (fn? killSvr) (killSvr))
      (log/info "remote shutdown hook closed - ok")
      (log/info "container is shutting down...")
      (log/info "about to stop wabbit...")
      (if (some? execv)
        (.stop ^Startable execv))
      (shutdown-agents)
      (log/info "wabbit stopped"))
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown
  "Listen on a port for remote kill command"
  ^Atom
  [^Atom gist]
  (let [[h p] (-> (str (sysProp "wabbit.kill.port"))
                  (.split ":"))
        m {}
        m (if (hgl? h) (assoc m :host h) m)
        m (if (hgl? p) (assoc m :port (convLong p 4444)))]
    (log/info "enabling remote shutdown hook: %s" m)
    (swap! gist
           assoc
           :killSvr
           (cfgShutdownServer gist
                              #(stopCLI gist) m))
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defn- primodial
  ""
  ^Atom
  [^Atom gist]
  (log/info "\n%s\n%s\n%s"
            (str<> 78 \=)
            "inside primodial()" (str<> 78 \=))
  (log/info "execvisor = %s"
            "czlab.wabbit.server.Execvisor")
  (let [execv (execvisor<>)]
    (swap! gist
           assoc
           :execv execv
           :stop! #(stopCLI gist))
    (.init execv gist)
    (log/info "\n%s\n%s\n%s"
              (str<> 78 \*)
              "about to start wabbit..."
              (str<> 78 \*))
    (.start execv nil)
    (log/info "wabbit started!")
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCons
  ""
  [cwd]
  (let
    [{:keys [locale info] :as env}
     (slurpXXXConf cwd cfg-pod-cf true)
     cn (stror (:country locale) "US")
     ln (stror (:lang locale) "en")
     verStr (str (some-> (loadResource c-verprops)
                         (.getString "version")))
     fp (io/file cwd "wabbit.pid")
     loc (Locale. ln cn)
     rc (getResource c-rcb loc)
     ctx (->> {:encoding (stror (:encoding info) "utf-8")
               :podDir (io/file cwd)
               :version verStr
               :pidFile fp
               :env env
               :locale loc}
              (cliGist ))
     cz (getCldr)]
    (log/info "wabbit.proc.dir = %s" (fpath cwd))
    (log/info "wabbit.version = %s" verStr)
    (doto->> rc
             (test-some "base resource" )
             (I18N/setBase ))
    (log/info "wabbit's i18n#base found and loaded")
    (primodial ctx)
    (doto fp
      (writeFile (processPid))
      (.deleteOnExit ))
    (log/info "wrote wabbit.pid - ok")
    (enableRemoteShutdown ctx)
    (exitHook #(stopCLI ctx))
    (log/info "added shutdown hook")
    (log/info "app-loader: %s" (type cz))
    (log/info "sys-loader: %s"
              (type (.getParent cz)))
    (log/debug "%s" @ctx)
    (log/info "container is now running...")
    (while (not @stopcli) (pause 3000))
    (log/info "vm shut down")
    (log/info "(bye)")
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


