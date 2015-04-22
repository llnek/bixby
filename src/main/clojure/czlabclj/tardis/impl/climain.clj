;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.impl.climain

  (:require [clojure.tools.logging :as log :only (info warn error debug)]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.netty.discarder :only [MakeDiscardHTTPD]]
        [czlabclj.xlib.util.str :only [lcase hgl? nsb strim]]
        [czlabclj.xlib.util.ini :only [ParseInifile]]
        [czlabclj.xlib.util.process
         :only
         [ProcessPid SafeWait ThreadFunc]]
        [czlabclj.xlib.i18n.resources :only [GetResource]]
        [czlabclj.xlib.util.meta :only [SetCldr GetCldr]]
        [czlabclj.xlib.util.format :only [ReadEdn]]
        [czlabclj.xlib.util.files
         :only
         [ReadOneFile WriteOneFile]]
        [czlabclj.xlib.util.scheduler :only [MakeScheduler]]
        [czlabclj.xlib.util.core
         :only
         [ternary test-nonil test-cond ConvLong
          Try! PrintMutableObj MakeMMap]]
        [czlabclj.tardis.impl.exec :only [MakeExecvisor]]
        [czlabclj.tardis.core.consts]
        [czlabclj.tardis.core.sys]
        [czlabclj.xlib.util.wfs]
        [czlabclj.tardis.impl.dfts]
        [czlabclj.xlib.netty.io :only [StopServer]])

  (:import  [io.netty.channel Channel ChannelFuture ChannelFutureListener]
            [com.zotohlab.skaro.loaders AppClassLoader
             RootClassLoader ExecClassLoader]
            [com.zotohlab.frwk.core Versioned Identifiable
             Hierarchial Startable]
            [com.zotohlab.frwk.util IWin32Conf]
            [com.zotohlab.frwk.i18n I18N]
            [com.zotohlab.wflow Job Pipeline
             Activity Nihil PDelegate]
            [com.zotohlab.skaro.core ConfigError]
            [com.zotohlab.skaro.etc CliMain]
            [io.netty.bootstrap ServerBootstrap]
            [com.google.gson JsonObject]
            [com.zotohlab.frwk.server ServerLike
             Component ComponentRegistry]
            [com.zotohlab.skaro.etc CmdHelpError]
            [java.util ResourceBundle Locale]
            [java.io File]
            [org.apache.commons.io FileUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(def CLI-TRIGGER (promise))
(def ^:private STOPCLI (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizContext

  "the context object has a set of properties, such as basic dir, which
  is shared with other key components."

  ^czlabclj.xlib.util.core.MubleAPI
  [^File baseDir]

  (let [cfg (File. baseDir DN_CFG)
        home (.getParentFile cfg) ]
    (PrecondDir home)
    (PrecondDir cfg)
    (doto (MakeContext)
      (.setf! K_BASEDIR home)
      (.setf! K_CFGDIR cfg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI "Stop all apps and processors."

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [^File pid (.getf ctx K_PIDFILE)
        kp (.getf ctx K_KILLPORT)
        execv (.getf ctx K_EXECV) ]

    (when-not @STOPCLI
      (reset! STOPCLI true)
      (println)
      (log/info "Shutting down the http discarder...")
      (StopServer ^ServerBootstrap (:bootstrap kp)
                  ^Channel (:channel kp))
      (log/info "Http discarder closed. OK")
      (when-not (nil? pid) (FileUtils/deleteQuietly pid))
      (log/info "Containers are shutting down...")
      (log/info "About to stop Skaro...")
      (when-not (nil? execv)
        (.stop ^Startable execv))
      (log/info "Skaro stopped.")
      (log/info "VM shut down.")
      (log/info "\"Goodbye\"."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown "Listen on a port for remote kill command"

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (log/info "Enabling remote shutdown")
  (let [port (ConvLong (System/getProperty "skaro.kill.port") 4444)
        rc (MakeDiscardHTTPD "127.0.0.1"
                             port {}
                             #(stopCLI ctx)) ]
    (.setf! ctx K_KILLPORT rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cserver

  ^ServerLike
  [^File home]

  (let [^czlabclj.xlib.util.scheduler.SchedulerAPI
        cpu (MakeScheduler nil)
        impl (MakeMMap)]
    (.activate cpu { :threads 1 })
    (reify

      ServerLike

      (hasService [_ s] )
      (getService [_ s] )
      (core [_] cpu)

      Element

      (setCtx! [_ x] (.setf! impl :ctx x))
      (getCtx [_] (.getf impl :ctx))
      (setAttr! [_ a v] (.setf! impl a v) )
      (clrAttr! [_ a] (.clrf! impl a) )
      (getAttr [_ a] (.getf impl a) )
      (toEDN [_ ] (.toEDN impl))

      Hierarchial
      (parent [_] nil)

      Versioned
      (version [_]
        (let [f (File. home "VERSION")
              v (ReadOneFile f)]
          (nsb v)))

      Identifiable
      (id [_] K_CLISH))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pauseCLI ""

  []

  (SimPTask "PauseCLI"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.MubleAPI
            x (.getLastResult j)
            s (.container j)]
        (log/debug "#### sys loader = "
                   (-> (ClassLoader/getSystemClassLoader)
                       (.getClass)
                       (.getName)))
        (PrintMutableObj x)
        (log/info "Container(s) are now running...")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookShutdown ""

  []

  (SimPTask "HookShutDown"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.MubleAPI
            x (.getLastResult j)
            cli (.getf x K_CLISH)]
        (.addShutdownHook (Runtime/getRuntime)
                          (ThreadFunc #(stopCLI x) false))
        (enableRemoteShutdown x)
        (log/info "Added shutdown hook.")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writePID ""

  []

  (SimPTask "WritePID"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.MubleAPI
            x (.getLastResult j)
            ^File home (.getf x K_BASEDIR)
            fp (File. home "skaro.pid")]
        (WriteOneFile fp (ProcessPid))
        (.setf! x K_PIDFILE fp)
        (log/info "Wrote skaro.pid - OK.")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create and synthesize Execvisor.
(defn- primodial ""

  []

  (SimPTask "Primodial"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.MubleAPI
            x (.getLastResult j)
            cl (.getf x K_EXEC_CZLR)
            cli (.getf x K_CLISH)
            wc (.getf x K_PROPS)
            cz (ternary (K_EXECV (K_COMPS wc)) "")]
        ;;(test-cond "conf file:exec-visor" (= cz "czlabclj.tardis.impl.Execvisor"))
        (log/info "Inside primodial() ---------------------------------------------->")
        (log/info "Execvisor = " cz)
        (let [^czlabclj.xlib.util.core.MubleAPI
              execv (MakeExecvisor cli)]
          (.setf! x K_EXECV execv)
          (SynthesizeComponent execv { :ctx x })
          (log/info "Execvisor created and synthesized - OK.")
          (log/info "*********************************************************")
          (log/info "*")
          (log/info "About to start Skaro...")
          (log/info "*")
          (log/info "*********************************************************")
          (.start ^Startable execv)
          (log/info "Skaro started."))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- loadRes "Look for and load the resource bundle."

  []

  (SimPTask "LoadResource"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.MubleAPI
            x (.getLastResult j)
            rc (GetResource "czlabclj/tardis/etc/Resources"
                            (.getf x K_LOCALE))]
        (test-nonil "etc/resouces" rc)
        (.setf! x K_RCBUNDLE rc)
        (I18N/setBase rc)
        (log/info "Resource bundle found and loaded.")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse skaro.conf
(defn- loadConf ""

  []

  (SimPTask "LoadConf"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.MubleAPI
            x (.getLastResult j)
            ^File home (.getf x K_BASEDIR)
            cf (File. home (str DN_CONF
                                 "/" (name K_PROPS)))]
        (log/info "About to parse config file " cf)
        (let [w (ReadEdn cf)
              cn (lcase (ternary (K_COUNTRY (K_LOCALE w)) ""))
              lg (lcase (ternary (K_LANG (K_LOCALE w)) "en"))
              loc (if (hgl? cn)
                    (Locale. lg cn)
                    (Locale. lg))]
          (log/info (str "Using locale: " loc))
          (doto x
            (.setf! K_LOCALE loc)
            (.setf! K_PROPS w)
          ))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setupLoaders

  "Prepare class loaders.  The root class loader loads all the core libs.
  The exec class loader inherits from the root and is the class loader
  that runs skaro."

  []

  (letfn
    [(f1 [^czlabclj.xlib.util.core.MubleAPI x]
       (let [cl (ExecClassLoader. ^ClassLoader
                                  (.getf x K_ROOT_CZLR))]
         (SetCldr cl)
         (.setf! x K_EXEC_CZLR cl)))
     (f0 [^czlabclj.xlib.util.core.MubleAPI x
          ^ClassLoader cur]
       (.setf! x K_ROOT_CZLR (RootClassLoader. cur))) ]
    (SimPTask "SetupLoaders"
      (fn [^Job j]
        (let [^czlabclj.xlib.util.core.MubleAPI
              x (.getLastResult j)
              cz (GetCldr)
              p (.getParent cz)]
          (condp instance? cz
            RootClassLoader
            (do
              (.setf! x K_ROOT_CZLR cz)
              (f1 x))

            ExecClassLoader
            (do
              (.setf! x K_ROOT_CZLR p)
              (.setf! x K_EXEC_CZLR cz))

            (do
              (f0 x cz)
              (f1 x)))
          (log/info "Classloaders configured. using " (type cz))
        )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- rtStart ""

  ^Activity
  []

  (SimPTask "RtStart"
    (fn [^Job j]
      (let [^czlabclj.tardis.core.sys.Element
            c (.container j)
            ^File
            home (.getv j :home)
            x (inizContext home)]
        (log/info "SKARO.Version= " (.version ^Versioned c))
        ;;(precondDir (File. home ^String DN_BLOCKS))
        (PrecondDir (File. home DN_BOXX))
        (PrecondDir (File. home DN_CFG))
        ;; a bit of circular referencing here.  the climain object refers to context
        ;; and the context refers back to the climain object.
        (.setf! x K_CLISH c)
        (.setCtx! c x)
        (log/info "Home directory looks ok.")
        (.setLastResult j x)
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype RtDelegate [] PDelegate
  (onStop [_ p] (-> (.core p) (.dispose)))
  (onError [_ err cur] (Nihil.))
  (startWith [_ p]
    (-> (rtStart)
        (.chain (setupLoaders))
        (.chain (loadConf))
        (.chain (loadRes))
        (.chain (primodial))
        (.chain (writePID))
        (.chain (hookShutdown))
        (.chain (pauseCLI)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype StartMainViaCLI []
    CliMain
    (run [_ args]
      (require 'czlabclj.tardis.impl.climain)
      (let [home (first args)
            svr (cserver home)
            job (PseudoJob svr)
            p (Pipeline. "RtDelegate" "czlabclj.tardis.impl.climain.RtDelegate" job)]
        (.setv job :home home)
        (.start p))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private climain-eof nil)

