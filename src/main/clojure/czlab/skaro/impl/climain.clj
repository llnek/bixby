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
      :author "kenl" }

  czlab.skaro.impl.climain

  (:require
    [czlab.netty.discarder :refer [discardHTTPD]]
    [czlab.xlib.str :refer [lcase hgl? strim]]
    [czlab.xlib.io :refer [closeQ]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.process
     :refer [processPid safeWait threadFunc]]
    [czlab.xlib.resources :refer [getResource]]
    [czlab.xlib.meta :refer [setCldr getCldr]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.files
     :refer [readOneFile writeOneFile]]
    [czlab.xlib.scheduler :refer [nulScheduler*]]
    [czlab.xlib.core
     :refer [test-nonil test-cond convLong sysVar
             fpath printMutableObj mubleObj!]]
    [czlab.skaro.impl.exec :refer [execvisor]]
    [czlab.netty.io :refer [stopServer]])

  (:use [czlab.skaro.core.consts]
        [czlab.xlib.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.core.wfs]
        [czlab.skaro.impl.dfts])

  (:import
    [czlab.skaro.server CLJShim Context ConfigError]
    [io.netty.channel Channel ChannelFuture
     ChannelFutureListener]
    [czlab.skaro.loaders AppClassLoader
     RootClassLoader
     ExecClassLoader]
    [czlab.skaro.runtime ExecvisorAPI]
    [czlab.xlib Versioned
     Identifiable
     Disposable Activable
     Hierarchial Startable]
    [czlab.xlib Schedulable Muble I18N]
    [czlab.wflow Job
     WorkFlow
     FlowDot
     Activity Nihil]
    [io.netty.bootstrap ServerBootstrap]
    [com.google.gson JsonObject]
    [czlab.wflow.server ServerLike
     ServiceHandler
     Component]
    [czlab.skaro.etc CmdHelpError]
    [java.util ResourceBundle Locale]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private STOPCLI (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizContext

  "The context object has a set of properties, such as home dir, which
   is shared with other key components"

  ^Muble
  [baseDir]

  (let [etc (io/file baseDir DN_CFG)
        home (.getParentFile etc)]
    (map precondDir [(io/file home DN_CONF)
                     (io/file home DN_DIST)
                     (io/file home DN_LIB)
                     (io/file home DN_BIN) home etc])
    (doto (makeContext)
      (.setv K_BASEDIR home)
      (.setv K_CFGDIR etc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI

  "Stop all apps and processors"

  [^Muble ctx]

  (let [pid (.getv ctx K_PIDFILE)
        kp (.getv ctx K_KILLPORT)
        execv (.getv ctx K_EXECV) ]

    (when-not @STOPCLI
      (reset! STOPCLI true)
      (print "\n\n")
      (log/info "closing the remote shutdown")
      (stopServer (:bootstrap kp)
                  (:channel kp))
      (log/info "remote shutdown closed. ok")
      ;;(when-not (nil? pid) (io/delete-file pid true))
      (log/info "containers are shutting down...")
      (log/info "about to stop skaro...")
      (when (some? execv) (.stop ^Startable execv))
      (log/info "skaro stopped")
      (log/info "vm shut down")
      (log/info "\"goodbye\""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown

  "Listen on a port for remote kill command"

  [^Muble ctx]

  (log/info "enabling remote shutdown")
  (->> (-> (sysVar "skaro.kill.port")
           (convLong  4444)
           (discardHTTPD #(stopCLI ctx)))
       (.setv ctx K_KILLPORT )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cserver

  ^ServerLike
  [^File home]

  (let [ctxt (atom (mubleObj!))
        impl (mubleObj!)
        cpu (nulScheduler*) ]
    (-> ^Activable
        cpu
        (.activate {:threads 1}))
    (reify

      ServiceHandler

      (handle [this arg options]
        (let [w (toWorkFlow arg)
              j (newJob this)]
          (doseq [[k v] options]
            (.setv j k v))
          (.setv j :wflow w)
          (->> (nihilDot j)
               (.reify (.startWith w))
               (.run cpu ))))

      (handleError [_ e] )

      Disposable

      (dispose [_] (.dispose cpu))

      ServerLike

      (core [_] cpu)

      Context

      (setx [_ x] (reset! ctxt x))
      (getx [_] @ctxt)

      Muble

      (setv [_ a v] (.setv impl a v) )
      (unsetv [_ a] (.unsetv impl a) )
      (getv [_ a] (.getv impl a) )
      (seq [_] )
      (clear [_] (.clear impl))
      (toEDN [_ ] (.toEDN impl))

      Hierarchial

      (parent [_] nil)

      Versioned

      (version [_]
        (str (sysVar "skaro.version")))

      Identifiable

      (id [_] K_CLISH)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  pauseCLI
  (simPTask "PauseCLI"
    (fn [^Job j]
      (let [ctx (.getLastResult j)
            s (.container j)]
        (log/debug "#### sys loader = %s"
                   (-> (ClassLoader/getSystemClassLoader)
                       (.getClass)
                       (.getName)))
        (printMutableObj ctx)
        (log/info "container(s) are now running...")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  hookShutdown
  (simPTask "HookShutDown"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            cli (.getv ctx K_CLISH)]
        (->> (ThreadFunc #(stopCLI ctx) false)
             (.addShutdownHook (Runtime/getRuntime)))
        (enableRemoteShutdown ctx)
        (log/info "added shutdown hook")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  writePID
  (simPTask "WritePID"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            home (.getv ctx K_BASEDIR)
            fp (io/file home "skaro.pid")]
        (writeOneFile fp (processPid))
        (.setv ctx K_PIDFILE fp)
        (.deleteOnExit fp)
        (log/info "wrote skaro.pid - ok")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defonce ^:private ^Activity
  primodial
  (simPTask "Primodial"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            cl (.getv ctx K_EXEC_CZLR)
            cli (.getv ctx K_CLISH)
            wc (.getv ctx K_PROPS)
            cz (get-in wc [K_COMPS K_EXECV]) ]
        (test-cond "conf file:execvisor" (= cz "czlab.skaro.impl.Execvisor"))
        (log/info "inside primodial() ----------------------------->")
        (log/info "execvisor = %s" cz)
        (let [execv (execvisor cli)]
          (.setv ctx K_EXECV execv)
          (synthesizeComponent execv {:ctx ctx})
          (log/info "execvisor created and synthesized - ok")
          (log/info "*********************************************************")
          (log/info "about to start skaro...")
          (log/info "*********************************************************")
          (-> ^Startable execv (.start ))
          (log/info "skaro started!"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;look for and load the resource bundle
(defonce ^:private ^Activity
  loadRes
  (simPTask "LoadResource"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            rc (-> "czlab.skaro.etc/Resources"
                   (getResource (.getv ctx K_LOCALE)))]
        (test-nonil "etc/resouces" rc)
        (.setv ctx K_RCBUNDLE rc)
        (I18N/setBase rc)
        (log/info "resource bundle found and loaded")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;parse skaro.conf
(defonce ^:private ^Activity
  loadConf
  (simPTask "LoadConf"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            home (.getv ctx K_BASEDIR)
            cf (io/file home
                        DN_CONF (name K_PROPS))]
        (log/info "about to parse config file %s" cf)
        (let [w (readEdn cf)
              lg (lcase (or (get-in w [K_LOCALE K_LANG]) "en"))
              cn (lcase (get-in w [K_LOCALE K_COUNTRY]))
              loc (if (empty? cn)
                    (Locale. lg)
                    (Locale. lg cn))]
          (log/info "using locale: %s" loc)
          (doto ctx
            (.setv K_LOCALE loc)
            (.setv K_PROPS w)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;prepare class loaders.  The root class loader loads all the core libs,
;;the exec class loader inherits from the root and is the class loader
;;which runs skaro
(defonce ^:private ^Activity
  setupLoaders
  (simPTask "SetupLoaders"
    (fn [^Job j]
      (let [^Muble x (.getLastResult j)
            cz (getCldr)
            p (.getParent cz)
            pp (.getParent p)]
        (test-cond "bad classloaders" (and (instance? RootClassLoader pp)
                                           (instance? ExecClassLoader p)))
        (.setv x K_ROOT_CZLR pp)
        (.setv x K_EXEC_CZLR p)
        (log/info "classloaders configured: using %s" (type cz))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  rtStart
  (simPTask "RtStart"
    (fn [^Job j]
      (let [home (.getv j :home)
            c (.container j)
            x (inizContext home)]
        (log/info "skaro.home %s" (FPath home))
        (log/info "skaro.version= %s" (.version ^Versioned c))
        (.setv x K_CLISH c)
        (-> ^Context c (.setx x))
        (log/info "home directory looks ok")
        (.setLastResult j x)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn startViaCLI ""

  [home]

  (let [a (-> rtStart
              (.chain setupLoaders)
              (.chain loadConf)
              (.chain loadRes)
              (.chain primodial)
              (.chain writePID)
              (.chain hookShutdown)
              (.chain pauseCLI))]
    (-> ^ServiceHandler
        (cserver home)
        (.handle a {:home home}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


