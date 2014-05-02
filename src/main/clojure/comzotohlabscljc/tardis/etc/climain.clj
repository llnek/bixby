;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.etc.climain

  (:require [clojure.tools.logging :as log :only (info warn error debug)])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.process :only [ProcessPid SafeWait] ])
  (:use [comzotohlabscljc.i18n.resources :only [GetResource] ])
  (:use [comzotohlabscljc.util.meta :only [SetCldr GetCldr] ])
  (:use [comzotohlabscljc.util.core
         :only [test-nonil test-cond ConvLong Try! PrintMutableObj MakeMMap] ])
  (:use [comzotohlabscljc.util.str :only [hgl? nsb strim] ])
  (:use [comzotohlabscljc.util.ini :only [ParseInifile] ])
  (:use [comzotohlabscljc.netty.discarder :only [MakeDiscardHTTPD] ])

  (:use [comzotohlabscljc.tardis.impl.exec :only [MakeExecvisor] ])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.impl.defaults])

  (:import (com.zotohlabs.gallifrey.loaders AppClassLoader
                                            RootClassLoader ExecClassLoader))
  (:import (com.zotohlabs.frwk.core Versioned Identifiable Hierarchial Startable ))
  (:import (io.netty.channel Channel ChannelFuture ChannelFutureListener))
  (:import (com.zotohlabs.gallifrey.core ConfigError))
  (:import (com.zotohlabs.frwk.server Component ComponentRegistry))
  (:import (com.zotohlabs.gallifrey.etc CmdHelpError))
  (:import (java.util Locale))
  (:import (java.io File))
  (:import (org.apache.commons.io FileUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def CLI-TRIGGER (promise))
(def STOPCLI (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizContext ""

  ^comzotohlabscljc.util.core.MubleAPI
  [^File baseDir]

  (let [ cfg (File. baseDir ^String DN_CFG)
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

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ root (.getf ctx K_ROOT_CZLR)
         cl (ExecClassLoader. root) ]
    (SetCldr cl)
    (.setf! ctx K_EXEC_CZLR cl)
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setupClassLoaderAsRoot ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ root (RootClassLoader. (GetCldr)) ]
    (.setf! ctx K_ROOT_CZLR root)
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInizLoaders ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ cz (GetCldr) ]
    (cond
      (instance? RootClassLoader cz)
      (do
        (.setf! ctx K_ROOT_CZLR cz)
        (setupClassLoader ctx))

      (instance? ExecClassLoader cz)
      (do
        (.setf! ctx K_ROOT_CZLR (.getParent cz))
        (.setf! ctx K_EXEC_CZLR cz))

      :else
      (setupClassLoader (setupClassLoaderAsRoot ctx)))

    (log/info "classloaders configured.  using ExecClassLoader.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- loadConf ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ ^File home (.getf ctx K_BASEDIR)
         cf (File. home  (str DN_CONF "/" (name K_PROPS) ))
        ^comzotohlabscljc.util.ini.IWin32Conf
         w (ParseInifile cf)
         cn (cstr/lower-case (.optString w K_LOCALE K_COUNTRY ""))
         lg (cstr/lower-case (.optString w K_LOCALE K_LANG "en"))
         loc (if (hgl? cn) (Locale. lg cn) (Locale. lg)) ]
    (log/info (str "using locale: " loc))
    (doto ctx
          (.setf! K_PROPS w)
          (.setf! K_LOCALE loc))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setupResources ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ rc (GetResource "comzotohlabscljc/tardis/etc/Resources" (.getf ctx K_LOCALE)) ]
    (test-nonil "etc/resouces" rc)
    (.setf! ctx K_RCBUNDLE rc)
    (log/info "resource bundle found and loaded.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pre-parse ""

  [^comzotohlabscljc.tardis.core.sys.Element cli args]

  (let [ bh (File. ^String (first args))
         ctx (inizContext bh) ]
    (log/info "inside pre-parse()")
    ;;(precondDir (File. bh ^String DN_BLOCKS))
    (PrecondDir (File. bh ^String DN_BOXX))
    (PrecondDir (File. bh ^String DN_CFG))
    (.setf! ctx K_CLISH cli)
    (.setCtx! cli ctx)
    (log/info "home directory looks ok.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- start-exec ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (do
    (log/info "about to start Skaro...")
    (let [ ^Startable exec (.getf ctx K_EXECV) ]
      (.start exec))
    (log/info "Skaro started.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- primodial ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ ^comzotohlabscljc.util.ini.IWin32Conf
         wc (.getf ctx K_PROPS)
         cl (.getf ctx K_EXEC_CZLR)
         cli (.getf ctx K_CLISH)
         cz (.optString wc K_COMPS K_EXECV "") ]
    (test-cond "conf file:exec-visor"
                  (= cz "comzotohlabscljc.tardis.impl.Execvisor"))
    (log/info "inside primodial()")
    (let [ ^comzotohlabscljc.util.core.MubleAPI
           execv (MakeExecvisor cli) ]
      (.setf! ctx K_EXECV execv)
      (SynthesizeComponent execv { :ctx ctx } )
      (log/info "Execvisor created and synthesized - OK.")
      ctx
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stop-cli ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ ^File pid (.getf ctx K_PIDFILE)
         execv (.getf ctx K_EXECV) ]
    (when-not @STOPCLI
      (do
        (reset! STOPCLI true)
        (when-not (nil? pid) (FileUtils/deleteQuietly pid))
        (log/info "about to stop Skaro...")
        (log/info "applications are shutting down...")
        (when-not (nil? execv)
          (.stop ^Startable execv))
        (log/info "Skaro stopped.")
        (log/info "Tardis says \"Goodbye\".")
        (deliver CLI-TRIGGER 911)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- enableRemoteShutdown ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ port (ConvLong (System/getProperty "skaro.kill.port") 4444) ]
    (log/info "Enabling remote shutdown...")
    ;;TODO - how to clean this up
    (MakeDiscardHTTPD "127.0.0.1" port (fn [] (stop-cli ctx)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookShutdown ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ cli (.getf ctx K_CLISH) ]
    (.addShutdownHook (Runtime/getRuntime)
          (Thread. (reify Runnable
                      (run [_] (Try! (stop-cli ctx))))))
    (enableRemoteShutdown ctx)
    (log/info "added shutdown hook.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writePID ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (let [ fp (File. ^File (.getf ctx K_BASEDIR) "skaro.pid") ]
    (FileUtils/writeStringToFile fp (ProcessPid) "utf-8")
    (.setf! ctx K_PIDFILE fp)
    (log/info "wrote skaro.pid - OK.")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pause-cli ""

  [^comzotohlabscljc.util.core.MubleAPI ctx]

  (PrintMutableObj ctx)
  (log/info "applications are now running...")
  (log/info "system thread paused on promise - awaits delivery.")
  (deref CLI-TRIGGER) ;; pause here
  (log/info "promise delivered!")
  (SafeWait 5000) ;; give some time for stuff to wind-down.
  (System/exit 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-climain ""

  [ & args ]

  (let [ impl (MakeMMap) ]
    (reify

      Element

      (setCtx! [_ x] (.setf! impl :ctx x))
      (getCtx [_] (.getf impl :ctx))
      (setAttr! [_ a v] (.setf! impl a v) )
      (clrAttr! [_ a] (.clrf! impl a) )
      (getAttr [_ a] (.getf impl a) )

      Hierarchial
      (parent [_] nil)

      Versioned
      (version [_] "1.0")

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

      (stop [this]
        (let [ ^comzotohlabscljc.util.core.MubleAPI
               ctx (getCtx this) ]
          (stop-cli ctx))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StartMain ""

  [ & args ]

  (when (< (count args) 1) (throw (CmdHelpError. "Skaro Home not defined.")))
  (log/info "set skaro-home= " (first args))
  (.start ^Startable (apply make-climain args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private climain-eof nil)

