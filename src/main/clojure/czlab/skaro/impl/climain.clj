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

  czlab.skaro.impl.climain

  (:require
    [czlab.xlib.netty.discarder :refer [MakeDiscardHTTPD]]
    [czlab.xlib.util.str :refer [lcase hgl? strim]]
    [czlab.xlib.util.io :refer [CloseQ]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.util.process
    :refer [ProcessPid SafeWait ThreadFunc]]
    [czlab.xlib.i18n.resources :refer [GetResource]]
    [czlab.xlib.util.meta :refer [SetCldr GetCldr]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.xlib.util.files
    :refer [ReadOneFile WriteOneFile]]
    [czlab.xlib.util.scheduler :refer [NulScheduler]]
    [czlab.xlib.util.core
    :refer [test-nonil test-cond ConvLong SysVar
    FPath PrintMutableObj MakeMMap]]
    [czlab.skaro.impl.exec :refer [MakeExecvisor]]
    [czlab.xlib.netty.io :refer [StopServer]])

  (:use [czlab.skaro.core.consts]
        [czlab.xlib.util.consts]
        [czlab.skaro.core.sys]
        [czlab.xlib.util.wfs]
        [czlab.skaro.impl.dfts])

  (:import
    [com.zotohlab.skaro.core Muble Context ConfigError]
    [io.netty.channel Channel ChannelFuture
    ChannelFutureListener]
    [com.zotohlab.skaro.loaders AppClassLoader
    RootClassLoader ExecClassLoader]
    [com.zotohlab.skaro.runtime ExecvisorAPI]
    [com.zotohlab.frwk.core
    Versioned Identifiable
    Disposable Activable
    Hierarchial Startable]
    [com.zotohlab.frwk.util Schedulable]
    [com.zotohlab.frwk.i18n I18N]
    [com.zotohlab.wflow Job WorkFlow
    FlowDot
    Activity Nihil]
    [com.zotohlab.skaro.etc CliMain]
    [io.netty.bootstrap ServerBootstrap]
    [com.google.gson JsonObject]
    [com.zotohlab.frwk.server ServerLike
    ServiceHandler
    Component ComponentRegistry]
    [com.zotohlab.skaro.etc CmdHelpError]
    [java.util ResourceBundle Locale]
    [java.io File]
    [org.apache.commons.io FileUtils]))

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
    (map PrecondDir [(io/file home DN_CONF)
                     (io/file home DN_DIST)
                     (io/file home DN_LIB)
                     (io/file home DN_BIN) home etc])
    (doto (MakeContext)
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
      (log/info "closing the http discarder...")
      (StopServer (:bootstrap kp)
                  (:channel kp))
      (log/info "http discarder closed. ok")
      ;;(when-not (nil? pid) (io/delete-file pid true))
      (log/info "containers are shutting down...")
      (log/info "about to stop skaro...")
      (when (some? execv)
        (.stop ^Startable execv))
      (log/info "skaro stopped")
      (log/info "vm shut down")
      (log/info "\"goodbye\""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown

  "Listen on a port for remote kill command"

  [^Muble ctx]

  (log/info "enabling remote shutdown")
  (->> (-> (SysVar "skaro.kill.port")
           (ConvLong  4444)
           (MakeDiscardHTTPD #(stopCLI ctx)))
       (.setv ctx K_KILLPORT )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cserver

  ^ServerLike
  [^File home]

  (let [ctxt (atom (MakeMMap))
        impl (MakeMMap)
        cpu (NulScheduler) ]
    (-> ^Activable
        cpu
        (.activate {:threads 1}))
    (reify

      ServiceHandler

      (handle [this arg options]
        (let [w (ToWorkFlow arg)
              j (NewJob this)]
          (doseq [[k v] options]
            (.setv j k v))
          (.setv j :wflow w)
          (->> (NihilDot j)
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
        (str (SysVar "skaro.version")))

      Identifiable

      (id [_] K_CLISH)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  pauseCLI
  (SimPTask "PauseCLI"
    (fn [^Job j]
      (let [ctx (.getLastResult j)
            s (.container j)]
        (log/debug "#### sys loader = %s"
                   (-> (ClassLoader/getSystemClassLoader)
                       (.getClass)
                       (.getName)))
        (PrintMutableObj ctx)
        (log/info "container(s) are now running...")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce ^:private ^Activity
  hookShutdown
  (SimPTask "HookShutDown"
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
  (SimPTask "WritePID"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            home (.getv ctx K_BASEDIR)
            fp (io/file home "skaro.pid")]
        (WriteOneFile fp (ProcessPid))
        (.setv ctx K_PIDFILE fp)
        (.deleteOnExit fp)
        (log/info "wrote skaro.pid - ok")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;create and synthesize Execvisor
(defonce ^:private ^Activity
  primodial
  (SimPTask "Primodial"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            cl (.getv ctx K_EXEC_CZLR)
            cli (.getv ctx K_CLISH)
            wc (.getv ctx K_PROPS)
            cz (get-in wc [K_COMPS K_EXECV]) ]
        (test-cond "conf file:execvisor" (= cz "czlab.skaro.impl.Execvisor"))
        (log/info "inside primodial() ----------------------------->")
        (log/info "execvisor = %s" cz)
        (let [^Startable execv (MakeExecvisor cli)]
          (.setv ctx K_EXECV execv)
          (SynthesizeComponent execv {:ctx ctx})
          (log/info "execvisor created and synthesized - ok")
          (log/info "*********************************************************")
          (log/info "about to start skaro...")
          (log/info "*********************************************************")
          (.start execv)
          (log/info "skaro started!"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;look for and load the resource bundle
(defonce ^:private ^Activity
  loadRes
  (SimPTask "LoadResource"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            rc (-> "czlab.skaro.etc/Resources"
                   (GetResource (.getv ctx K_LOCALE)))]
        (test-nonil "etc/resouces" rc)
        (.setv ctx K_RCBUNDLE rc)
        (I18N/setBase rc)
        (log/info "resource bundle found and loaded")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;parse skaro.conf
(defonce ^:private ^Activity
  loadConf
  (SimPTask "LoadConf"
    (fn [^Job j]
      (let [^Muble ctx (.getLastResult j)
            home (.getv ctx K_BASEDIR)
            cf (io/file home
                        DN_CONF (name K_PROPS))]
        (log/info "about to parse config file %s" cf)
        (let [w (ReadEdn cf)
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
  (SimPTask "SetupLoaders"
    (fn [^Job j]
      (let [^Muble x (.getLastResult j)
            cz (GetCldr)
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
  (SimPTask "RtStart"
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
(defn StartViaCLI ""

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

