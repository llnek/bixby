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
      :author "Kenneth Leung" }

  czlab.skaro.sys.climain

  (:require
    [czlab.xlib.str :refer [str<> stror lcase hgl? strim]]
    [czlab.netty.discarder :refer [discardHTTPD<>]]
    [czlab.xlib.files :refer [readFile writeFile]]
    [czlab.xlib.scheduler :refer [scheduler<>]]
    [czlab.xlib.resources :refer [getResource]]
    [czlab.xlib.meta :refer [setCldr getCldr]]
    [czlab.skaro.sys.exec :refer [execvisor<>]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.io :refer [closeQ]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.core
     :refer [test-nonil
             sysProp
             getCwd
             inst?
             fpath
             convLong]]
    [czlab.netty.core :refer [startServer stopServer]])

  (:use [czlab.skaro.sys.core]
        [czlab.xlib.process]
        [czlab.xlib.consts]
        [czlab.skaro.sys.dfts])

  (:import
    [io.netty.bootstrap ServerBootstrap]
    [czlab.skaro.server CljAppLoader]
    [clojure.lang
     Atom
     APersistentMap]
    [io.netty.channel
     Channel
     ChannelFuture
     ChannelFutureListener]
    [czlab.skaro.server
     Execvisor
     Component
     ConfigError]
    [czlab.xlib
     Identifiable
     Versioned
     Disposable
     Activable
     Hierarchial
     Startable
     CU
     Muble
     I18N
     Schedulable]
    [java.io File]
    [czlab.skaro.etc CmdHelpError]
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

  "Stop all apps and processors"
  ^Atom
  [^Atom gist]

  (let [{:keys [pidFile
                execv
                kill9]}
        @gist]
    (when-not @STOPCLI
      (reset! STOPCLI true)
      (print "\n\n")
      (log/info "closing the remote shutdown hook")
      (stopServer (:bootstrap kill9)
                  (:channel kill9))
      (log/info "remote shutdown hook closed - ok")
      (log/info "containers are shutting down...")
      (log/info "about to stop skaro...")
      (if (some? execv)
        (.stop ^Startable execv))
      (shutdown-agents)
      (log/info "skaro stopped")
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
  (let [bs (discardHTTPD<> #(stopCLI gist))
        ch (->> {:port (-> (sysProp "skaro.kill.port")
                           (convLong  4444))}
                (startServer bs))]
    (swap! gist assoc :kill9  {:bootstrap bs
                               :channel ch})
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
            "czlab.skaro.rt.Execvisor")
  (let [execv (execvisor<>)]
    (swap! gist
           assoc
           :execv execv
           :stop! #(stopCLI gist))
    (comp->initialize execv gist)
    (log/info "%s\n%s\n%s"
              (str<> 24 \*)
              "about to start skaro..."
              (str<> 24 \*))
    (.start execv)
    (log/info "skaro started!")
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCLI

  ""
  [home cwd]

  (let
    [env (->> (io/file cwd CFG_ENV_CF)
              (readEdn ))
     cn (get-in env [:locale :country])
     ln (get-in env [:locale :lang])
     ver (sysProp "skaro.version")
     fp (io/file cwd "skaro.pid")
     ln (stror ln "en")
     cn (stror cn "US")
     loc (Locale. ln cn)
     rc (getResource C_RCB loc)
     ctx (->> {:basedir (io/file home)
               :appDir (io/file cwd)
               :pidFile fp
               :locale loc}
              (merge env)
              (cliGist ))
     cz (getCldr)]
    (log/info "skaro.home    = %s" (fpath home))
    (log/info "skaro.version = %s" ver)
    (log/info "skaro folder - ok")
    (test-nonil "base resouces" rc)
    (I18N/setBase rc)
    (log/info "resource bundle found and loaded")
    (primodial ctx)
    (writeFile fp (processPid))
    (.deleteOnExit fp)
    (log/info "wrote skaro.pid - ok")
    (enableRemoteShutdown ctx)
    (exitHook #(stopCLI ctx))
    (log/info "added shutdown hook")
    (log/info "class-loader: using %s" (type cz))
    (log/info "sys-loader: %s"
              (type (.getParent cz)))
    (log/info "%s" @ctx)
    (log/info "container(s) are now running...")
    (while (not @STOPCLI)
      (safeWait 5000))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCLI

  ""
  [^File home ^File cwd]

  (if false
    (->> (CljAppLoader/newInstance home cwd)
         (.setContextClassLoader (Thread/currentThread))))
  (runCLI home cwd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


