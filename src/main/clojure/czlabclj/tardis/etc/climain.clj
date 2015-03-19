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

  czlabclj.tardis.etc.climain

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
        [czlabclj.xlib.util.files
         :only
         [ReadOneFile WriteOneFile ReadEdn]]
        [czlabclj.xlib.util.core
         :only
         [ternary test-nonil test-cond ConvLong
          Try! PrintMutableObj MakeMMap]]
        [czlabclj.tardis.impl.exec :only [MakeExecvisor]]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.impl.defaults]
        [czlabclj.xlib.netty.io :only [StopServer]])

  (:import  [io.netty.channel Channel ChannelFuture ChannelFutureListener]
            [com.zotohlab.gallifrey.loaders AppClassLoader
             RootClassLoader ExecClassLoader]
            [com.zotohlab.frwk.core Versioned Identifiable
             Hierarchial Startable]
            [com.zotohlab.frwk.util IWin32Conf]
            [com.zotohlab.gallifrey.core ConfigError]
            [io.netty.bootstrap ServerBootstrap]
            [com.google.gson JsonObject]
            [com.zotohlab.frwk.server Component ComponentRegistry]
            [com.zotohlab.gallifrey.etc CmdHelpError]
            [java.util ResourceBundle Locale]
            [java.io File]
            [org.apache.commons.io FileUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def CLI-TRIGGER (promise))
(def STOPCLI (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizContext

  "the context object has a set of properties, such as basic dir, which
  is shared with other key components."

  ^czlabclj.xlib.util.core.MubleAPI
  [^File baseDir]

  (let [cfg (File. baseDir ^String DN_CFG)
        home (.getParentFile cfg) ]
    (PrecondDir home)
    (PrecondDir cfg)
    (doto (MakeContext)
      (.setf! K_BASEDIR home)
      (.setf! K_CFGDIR cfg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setupClassLoader ""

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [^ClassLoader root (.getf ctx K_ROOT_CZLR)
        cl (ExecClassLoader. root) ]
    (SetCldr cl)
    (.setf! ctx K_EXEC_CZLR cl)
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setupClassLoaderAsRoot ""

  [^czlabclj.xlib.util.core.MubleAPI ctx
   ^ClassLoader cur]

  (doto ctx
    (.setf! K_ROOT_CZLR (RootClassLoader. cur))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInizLoaders

  "Prepare class loaders.  The root class loader loads all the core libs.
  The exec class loader inherits from the root and is the class loader
  that runs skaro."

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [cz (GetCldr) ]
    (condp instance? cz
      RootClassLoader
      (do
        (.setf! ctx K_ROOT_CZLR cz)
        (setupClassLoader ctx))

      ExecClassLoader
      (do
        (.setf! ctx K_ROOT_CZLR (.getParent cz))
        (.setf! ctx K_EXEC_CZLR cz))

      (setupClassLoader (setupClassLoaderAsRoot ctx cz)))

    (log/info "Classloaders configured. using " (type (GetCldr)))
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse skaro.conf
(defn- loadConf ""

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [^File home (.getf ctx K_BASEDIR)
        cf (File. home  (str DN_CONF
                             "/" (name K_PROPS) )) ]
    (log/info "About to parse config file " cf)
    (let [w (ReadEdn cf)
          cn (lcase (ternary (K_COUNTRY (K_LOCALE w)) ""))
          lg (lcase (ternary (K_LANG (K_LOCALE w)) "en"))
          loc (if (hgl? cn)
                (Locale. lg cn)
                (Locale. lg)) ]
      (log/info (str "Using locale: " loc))
      (doto ctx
        (.setf! K_LOCALE loc)
        (.setf! K_PROPS w)
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setupResources "Look for and load the resource bundle."

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [rc (GetResource "czlabclj/tardis/etc/Resources"
                        (.getf ctx K_LOCALE)) ]
    (test-nonil "etc/resouces" rc)
    (.setf! ctx K_RCBUNDLE rc)
    (log/info "Resource bundle found and loaded.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pre-parse "Make sure that the home directory looks ok."

  [^czlabclj.tardis.core.sys.Element cli args]

  (let [home (File. ^String (first args))
        ctx (inizContext home) ]
    (log/info "Inside pre-parse()")
    ;;(precondDir (File. home ^String DN_BLOCKS))
    (PrecondDir (File. home ^String DN_BOXX))
    (PrecondDir (File. home ^String DN_CFG))
    ;; a bit of circular referencing here.  the climain object refers to context
    ;; and the context refers back to the climain object.
    (.setf! ctx K_CLISH cli)
    (.setCtx! cli ctx)
    (log/info "Home directory looks ok.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- start-exec "Start the Execvisor!"

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (log/info "About to start Skaro...")
  (let [^Startable exec (.getf ctx K_EXECV) ]
    (.start exec))
  (log/info "Skaro started.")
  ctx)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create and synthesize Execvisor.
(defn- primodial ""

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [cl (.getf ctx K_EXEC_CZLR)
        cli (.getf ctx K_CLISH)
        wc (.getf ctx K_PROPS)
        cz (ternary (K_EXECV (K_COMPS wc)) "") ]
    ;;(test-cond "conf file:exec-visor" (= cz "czlabclj.tardis.impl.Execvisor"))
    (log/info "Inside primodial() ---------------------------------------------->")
    (log/info "Execvisor = " cz)
    (let [^czlabclj.xlib.util.core.MubleAPI
          execv (MakeExecvisor cli) ]
      (.setf! ctx K_EXECV execv)
      (SynthesizeComponent execv { :ctx ctx } )
      (log/info "Execvisor created and synthesized - OK.")
      ctx)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stop-cli "Stop all apps and processors."

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [^File pid (.getf ctx K_PIDFILE)
        kp (.getf ctx K_KILLPORT)
        execv (.getf ctx K_EXECV) ]

    (log/info "Shutting down the http discarder...")
    (StopServer ^ServerBootstrap (:bootstrap kp)
                ^Channel (:channel kp))
    (log/info "Http discarder closed. OK")

    (when-not @STOPCLI
      (reset! STOPCLI true)
      (when-not (nil? pid) (FileUtils/deleteQuietly pid))
      (log/info "Applications are shutting down...")
      (log/info "About to stop Skaro...")
      (when-not (nil? execv)
        (.stop ^Startable execv))
      (log/info "Execvisor stopped.")
      (log/info "Time to say \"Goodbye\".")
      (deliver CLI-TRIGGER 911))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown "Listen on a port for remote kill command"

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (log/info "Enabling remote shutdown...")
  (let [port (ConvLong (System/getProperty "skaro.kill.port") 4444)
        rc (MakeDiscardHTTPD "127.0.0.1"
                             port {}
                             #(stop-cli ctx)) ]
    (.setf! ctx K_KILLPORT rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookShutdown ""

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [cli (.getf ctx K_CLISH) ]
    (.addShutdownHook (Runtime/getRuntime)
                      (ThreadFunc #(stop-cli ctx) false))
    (enableRemoteShutdown ctx)
    (log/info "Added shutdown hook.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writePID ""

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (let [fp (File. ^File (.getf ctx K_BASEDIR) "skaro.pid") ]
    (WriteOneFile fp (ProcessPid))
    (.setf! ctx K_PIDFILE fp)
    (log/info "Wrote skaro.pid - OK.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pause-cli ""

  [^czlabclj.xlib.util.core.MubleAPI ctx]

  (PrintMutableObj ctx)
  (log/info "Applications are now running...")
  (log/info "System thread paused on promise - awaits delivery.")
  (deref CLI-TRIGGER) ;; pause here
  (log/info "Promise delivered! Done.")
  (SafeWait 5000) ;; give some time for stuff to wind-down.
  (System/exit 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-climain ""

  [& args]

  (let [home (first args)
        impl (MakeMMap) ]
    (reify

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
      (id [_] K_CLISH)

      Startable

      (start [this]
        (-> (pre-parse this args)
            (maybeInizLoaders)
            (loadConf)
            (setupResources )
            (primodial)
            (start-exec)
            (writePID)
            (hookShutdown)
            (pause-cli)) )

      (stop [this] (stop-cli (.getCtx this))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StartMain ""

  [& args]

  (if (< (count args) 1)
    (throw (CmdHelpError. "Skaro Home not defined."))
    (log/info "Skaro.home= " (first args)))

  (let [m (apply make-climain args)]
    (log/info "Skaro.ver= " (.version ^Versioned m))
    (.start ^Startable m)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private climain-eof nil)

