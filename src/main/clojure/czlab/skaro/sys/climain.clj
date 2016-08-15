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
    [czlab.xlib.str :refer [str<> lcase hgl? strim]]
    [czlab.netty.discarder :refer [discardHTTPD<>]]
    [czlab.xlib.files :refer [readFile writeFile]]
    [czlab.xlib.scheduler :refer [scheduler<>]]
    [czlab.xlib.resources :refer [getResource]]
    [czlab.xlib.meta :refer [setCldr getCldr]]
    [czlab.skaro.impl.exec :refer [execvisor<>]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.io :refer [closeQ]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.process
     :refer [processPid
             exitHook
             jvmInfo
             safeWait
             thread<>]]
    [czlab.xlib.core
     :refer [test-nonil
             test-cond
             convLong
             sysProp
             inst?
             fpath
             muble<>
             printMutableObj]]
    [czlab.netty.io :refer [stopServer]])

  (:use [czlab.skaro.sys.core]
        [czlab.xlib.consts]
        [czlab.skaro.sys.dfts])

  (:import
    [io.netty.bootstrap ServerBootstrap]
    [czlab.skaro.runtime Execvisor]
    [clojure.lang
     Atom
     APersistentMap]
    [io.netty.channel
     Channel
     ChannelFuture
     ChannelFutureListener]
    [czlab.skaro.server
     Component
     Cljshim
     Context
     ConfigError]
    [czlab.skaro.loaders
     AppClassLoader
     ExecClassLoader]
    [czlab.xlib
     Identifiable
     Versioned
     Disposable
     Activable
     Hierarchial
     Startable
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
  [baseDir]

  (let [etc (io/file baseDir DN_ETC)
        home (.getParentFile etc)]
    (precondDir (io/file home DN_CONF)
                (io/file home DN_DIST)
                (io/file home DN_LIB)
                (io/file home DN_BIN) home etc)
    (atom {:basedir home})))

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
      (when (some? execv) (.stop ^Startable execv))
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
  (->> (-> (sysProp "skaro.kill.port")
           (convLong  4444)
           (discardHTTPD<> #(stopCLI gist)))
       (swap! gist assoc :kill9 ))
  gist)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pauseCLI

  ""
  [^Atom gist]

  (log/debug "#### sys loader = %s"
             (-> (ClassLoader/getSystemClassLoader)
                 (.getClass)
                 (.getName)))
  (println @gist)
  (log/info "container(s) are now running...")
  (CU/block))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookShutdown

  ""
  ^Atom
  [^Atom gist]

  (let [gist (enableRemoteShutdown gist)]
    (exitHook #(stopCLI gist))
    (log/info "added shutdown hook")
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writePID

  ""
  ^Atom
  [^Atom gist]

  (let [fp (-> (:basedir @gist)
               (io/file "skaro.pid"))]
    (writeFile fp (processPid))
    (.deleteOnExit fp)
    (log/info "wrote skaro.pid - ok")
    (swap! gist assoc :pidFile fp))
  gist)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defn- primodial

  ""
  ^Atom
  [^Atom gist]

  (let
    [cli {:stop #(stopCLI gist)}
     cl (:exeLoader gist)
     wc (:skaroConf gist)
     {:keys [exeLoader
             skaroConf]}
     @gist
     cz (get-in skaroConf [:components
                           :execvisor])]
    (test-cond "conf file:execvisor"
               (= cz "czlab.skaro.rt.Execvisor"))
    (log/info "inside primodial() ----------------------------->")
    (log/info "execvisor = %s" cz)
    (let [execv (execvisor<> gist)]
      (swap! gist assoc :execv execv)
      (comp->initialize execv @gist)
      (log/info "%s\n%s\n%s"
                (str<> 72 \*)
                "about to start skaro..."
                (str<> 72 \*))
      (.start ^Startable execv)
      (log/info "skaro started!"))
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;look for and load the resource bundle
(defn- loadRes

  ""
  ^Atom
  [^Atom gist]

  (let [rc (-> "czlab.skaro.etc/Resources"
               (getResource (:locale @gist)))]
    (test-nonil "etc/resouces" rc)
    (swap! gist assoc :skaroRes rc)
    (I18N/setBase rc)
    (log/info "resource bundle found and loaded")
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;parse skaro.conf
(defn- loadConf

  ""
  ^Atom
  [^Atom gist]

  (let [cf (-> (:basedir @gist)
               (io/file DN_CONF SKARO_CF))]
    (log/info "about to parse config file %s" cf)
    (let [w (readEdn cf)
          lg (lcase (or (get-in w [:locale :lang]) "en"))
          cn (lcase (get-in w [:locale :country]))
          loc (if (empty? cn)
                (Locale. lg)
                (Locale. lg cn))]
      (log/info "using locale: %s" loc)
      (swap! gist
             merge
             {:skaroConf w
              :locale loc})
      gist)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;prepare class loaders.  The root class loader loads all the core libs,
;;the exec class loader inherits from the root and is the class loader
;;which runs skaro
(defn- setupLoaders

  ""
  ^Atom
  [^Atom gist]

  (let [cz (getCldr)
        p (.getParent cz)]
    (test-cond "bad classloaders"
               (inst? ExecClassLoader cz))
    (log/info "class-loader: using %s" (type cz))
    (log/info "parent: %s" (type p))
    gist))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- rtStart

  ""
  ^Atom
  [home]

  (let [ctx (cliGist home)]
    (log/info "skaro.home   = %s" (fpath home))
    (log/info "skaro.version= %s" (sysProp "skaro.version"))
    (log/info "skaro folder - ok")
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCLI

  ""
  [home]

  (-> (rtStart home)
      (setupLoaders)
      (loadConf)
      (loadRes)
      (primodial)
      (writePID)
      (hookShutdown)
      (pauseCLI)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


