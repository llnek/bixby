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

  czlab.skaro.impl.climain

  (:require
    [czlab.xlib.str :refer [str<> lcase hgl? strim]]
    [czlab.netty.discarder :refer [discardHTTPD<>]]
    [czlab.xlib.files :refer [readFile writeFile]]
    [czlab.xlib.scheduler :refer [scheduler<>]]
    [czlab.xlib.resources :refer [getResource]]
    [czlab.xlib.meta :refer [setCldr getCldr]]
    [czlab.skaro.impl.exec :refer [execvisor]]
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

  (:use [czlab.skaro.core.consts]
        [czlab.xlib.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.dfts])

  (:import
    [io.netty.bootstrap ServerBootstrap]
    [czlab.skaro.runtime ExecvisorAPI]
    [io.netty.channel
     Channel
     ChannelFuture
     ChannelFutureListener]
    [czlab.skaro.server
     Component
     CLJShim
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
(defn- inizContext

  "Context has a set of props, such as home dir, which
   is shared with other key components"
  ^Muble
  [baseDir]

  (let [etc (io/file baseDir DN_CFG)
        home (.getParentFile etc)]
    (precondDir (io/file home DN_CONF)
                (io/file home DN_DIST)
                (io/file home DN_LIB)
                (io/file home DN_BIN) home etc)
    (doto (muble<>)
      (.setv :basedir home)
      (.setv :etc etc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI

  "Stop all apps and processors"
  ^Muble
  [^Muble ctx]

  (let [{:keys [pidFile
                execv
                discarder]}
        (.impl ctx)]
    (when-not @STOPCLI
      (reset! STOPCLI true)
      (print "\n\n")
      (log/info "closing the remote shutdown hook")
      (stopServer (:bootstrap discarder)
                  (:channel discarder))
      (log/info "remote shutdown hook closed - ok")
      (log/info "containers are shutting down...")
      (log/info "about to stop skaro...")
      (when (some? execv) (.stop ^Startable execv))
      (log/info "skaro stopped")
      (log/info "vm shut down")
      (log/info "(bye)"))
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown

  "Listen on a port for remote kill command"
  ^Muble
  [^Muble ctx]

  (log/info "enabling remote shutdown hook")
  (->> (-> (sysProp "skaro.kill.port")
           (convLong  4444)
           (discardHTTPD<> #(stopCLI ctx)))
       (.setv ctx :discarder ))
  ctx)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pauseCLI

  ""
  ^Muble
  [^Muble ctx]

  (log/debug "#### sys loader = %s"
             (-> (ClassLoader/getSystemClassLoader)
                 (.getClass)
                 (.getName)))
  (printMutableObj ctx)
  (log/info "container(s) are now running...")
  ctx)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookShutdown

  ""
  ^Muble
  [^Muble ctx]

  (enableRemoteShutdown ctx)
  (exitHook #(stopCLI ctx))
  (log/info "added shutdown hook")
  ctx)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writePID

  ""
  ^Muble
  [^Muble ctx]

  (let [fp (-> (.getv ctx :basedir)
               (io/file "skaro.pid"))]
    (writeFile fp (processPid))
    (.setv ctx :pidFile fp)
    (.deleteOnExit fp)
    (log/info "wrote skaro.pid - ok")
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defn- primodial

  ""
  ^Muble
  [^Muble ctx]

  (let [cli {:stop #(stopCLI ctx) }
        cl (.getv ctx :exeLoader)
        wc (.getv ctx :skaroConf)
        cz (get-in wc [:components :execvisor])]
    (test-cond "conf file:execvisor"
               (= cz "czlab.skaro.impl.Execvisor"))
    (log/info "inside primodial() ----------------------------->")
    (log/info "execvisor = %s" cz)
    (let [execv (execvisor cli)]
      (.setv ctx :execv execv)
      (comp->synthesize execv {:ctx ctx})
      (log/info "execvisor synthesized - ok")
      (log/info (str<> 72 \*))
      (log/info "about to start skaro...")
      (log/info (str<> 72 \*))
      (.start ^Startable execv)
      (log/info "skaro started!"))
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;look for and load the resource bundle
(defn- loadRes

  ""
  ^Muble
  [^Muble ctx]

  (let [rc (-> "czlab.skaro.etc/Resources"
               (getResource (.getv ctx :locale)))]
    (test-nonil "etc/resouces" rc)
    (.setv ctx :skaroRes rc)
    (I18N/setBase rc)
    (log/info "resource bundle found and loaded")
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;parse skaro.conf
(defn- loadConf

  ""
  ^Muble
  [^Muble ctx]

  (let [cf (-> (.getv ctx :basedir)
               (io/file DN_CONF SKARO_CF))]
    (log/info "about to parse config file %s" cf)
    (let [w (readEdn cf)
          lg (lcase (or (get-in w [:locale :lang]) "en"))
          cn (lcase (get-in w [:locale :country]))
          loc (if (empty? cn)
                (Locale. lg)
                (Locale. lg cn))]
      (log/info "using locale: %s" loc)
      (doto ctx
        (.setv :locale loc)
        (.setv :skaroConf w)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;prepare class loaders.  The root class loader loads all the core libs,
;;the exec class loader inherits from the root and is the class loader
;;which runs skaro
(defn- setupLoaders

  ""
  ^Muble
  [ctx]

  (let [cz (getCldr)
        p (.getParent cz)]
    (test-cond "bad classloaders"
               (inst? ExecClassLoader p))
    (log/info "classloaders configured: using %s" (type cz))
    (log/info "parent-loader = %s" (type p))
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- rtStart

  ""
  ^Muble
  [home]

  (let [ctx (inizContext home)]
    (log/info "skaro.home   = %s" (fpath home))
    (log/info "skaro.version= %s" (sysProp "skaro.version"))
    (log/info "home directory - ok")
    x))

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


