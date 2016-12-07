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

  czlab.wabbit.sys.main

  (:require [czlab.xlib.io :refer [closeQ readAsStr writeFile]]
            [czlab.wabbit.io.http :refer [cfgShutdownServer]]
            [czlab.wabbit.sys.exec :refer [execvisor<>]]
            [czlab.xlib.scheduler :refer [scheduler<>]]
            [czlab.xlib.resources :refer [getResource]]
            [czlab.xlib.meta :refer [setCldr getCldr]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.sys.core]
        [czlab.xlib.process]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.consts])

  (:import [czlab.wabbit.server CljPodLoader]
           [clojure.lang Atom APersistentMap]
           [czlab.wabbit.server
            Execvisor
            Component
            ConfigError]
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
           [czlab.wabbit.etc CmdHelpError]
           [java.util ResourceBundle Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private STOPCLI (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cliGist
  ""
  ^Atom
  [gist]
  (let [home (:basedir gist)]
    (precondDir (io/file home DN_DIST)
                (io/file home DN_LIB)
                (io/file home DN_ETC)
                (io/file home DN_BIN))
    (atom gist)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI
  "Stop all apps and services"
  ^Atom
  [^Atom gist]
  (let [{:keys [killServer
                execv
                pidFile]}
        @gist]
    (when-not @STOPCLI
      (reset! STOPCLI true)
      (print "\n\n")
      (log/info "closing the remote shutdown hook")
      (if (fn? killServer) (killServer))
      (log/info "remote shutdown hook closed - ok")
      (log/info "containers are shutting down...")
      (log/info "about to stop wabbit...")
      (if (some? execv)
        (.stop ^Startable execv))
      (shutdown-agents)
      (log/info "wabbit stopped")
      (log/info "vm shut down")
      (log/info "(bye)"))
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown
  "Listen on a port for remote kill command"
  ^Atom
  [^Atom gist]
  (log/info "enabling remote shutdown hook")
  (->> (-> (sysProp "wabbit.kill.port")
           (convLong  4444))
       (cfgShutdownServer gist #(stopCLI gist)))
  gist)

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
    (.start execv)
    (log/info "wabbit started!")
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCLI
  ""
  [home cwd]
  (let
    [{:keys [locale jmx] :as env}
     (slurpXXXConf cwd CFG_POD_CF true)
     ver (sysProp "wabbit.version")
     fp (io/file cwd "wabbit.pid")
     cn (get locale :country)
     ln (get locale :lang)
     ln (stror ln "en")
     cn (stror cn "US")
     loc (Locale. ln cn)
     rc (getResource C_RCB loc)
     ctx (->> {:basedir (io/file home)
               :podDir (io/file cwd)
               :pidFile fp
               :jmx jmx
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
    (log/info "%s" @ctx)
    (log/info "container is now running...")
    (while (not @STOPCLI)
      (safeWait 5000))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


