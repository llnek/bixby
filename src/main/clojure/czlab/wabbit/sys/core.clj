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
      :author "Kenneth Leung"}

  czlab.wabbit.sys.core

  (:require [czlab.xlib.io :refer [closeQ readAsStr writeFile]]
            [czlab.wabbit.io.http :refer [cfgShutdownServer]]
            [czlab.wabbit.sys.exec :refer [execvisor<>]]
            [czlab.xlib.scheduler :refer [scheduler<>]]
            [czlab.xlib.resources :refer [getResource]]
            [czlab.xlib.meta :refer [setCldr getCldr]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.etc.core]
        [czlab.xlib.process]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.consts])

  (:import [czlab.wabbit.etc CmdError ConfigError]
           [czlab.wabbit.server CljPodLoader]
           [clojure.lang Atom APersistentMap]
           [czlab.wabbit.server Execvisor]
           [czlab.xlib
            Versioned
            Disposable
            Activable
            Hierarchial
            Startable
            CU
            Muble
            I18N
            Schedulable
            Identifiable]
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
  [gist]
  (let [home (:basedir gist)]
    (precondDir (io/file home dn-dist)
                (io/file home dn-lib)
                (io/file home dn-etc)
                (io/file home dn-bin))
    (atom gist)))

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
  (let [ss (-> (sysProp "wabbit.kill.port")
               str
               (.split ":"))
        m {:host (first ss)
           :port (convLong (last ss) 4444)}]
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
    (comp->init execv gist)
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
  [home cwd]
  (let
    [{:keys [locale info] :as env}
     (slurpXXXConf cwd cfg-pod-cf true)
     cn (stror (:country locale) "US")
     ln (stror (:lang locale) "en")
     ver (sysProp "wabbit.version")
     fp (io/file cwd "wabbit.pid")
     loc (Locale. ln cn)
     rc (getResource c-rcb loc)
     ctx (->> {:encoding (stror (:encoding info) "utf-8")
               :basedir (io/file home)
               :podDir (io/file cwd)
               :version ver
               :pidFile fp
               :env env
               :locale loc}
              (cliGist ))
     cz (getCldr)]
    (log/info "wabbit.home    = %s" (fpath home))
    (log/info "wabbit.version = %s" ver)
    (log/info "wabbit folder - ok")
    (doto->> rc
             (test-some "base resource" )
             (I18N/setBase ))
    (log/info "resource bundle found and loaded")
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
    (while (not @stopcli)
      (safeWait 3000))
    (log/info "vm shut down")
    (log/info "(bye)")
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


