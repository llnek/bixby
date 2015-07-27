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

  czlabclj.tardis.impl.climain

  (:require [czlabclj.xlib.netty.discarder :refer [MakeDiscardHTTPD]]
            [czlabclj.xlib.util.str :refer [lcase hgl? nsb strim]]
            [czlabclj.xlib.util.ini :refer [ParseInifile]]
            [czlabclj.xlib.util.io :refer [CloseQ]]
            [czlabclj.xlib.util.process
             :refer
             [ProcessPid
              SafeWait
              ThreadFunc]]
            [czlabclj.xlib.i18n.resources :refer [GetResource]]
            [czlabclj.xlib.util.meta :refer [SetCldr GetCldr]]
            [czlabclj.xlib.util.format :refer [ReadEdn]]
            [czlabclj.xlib.util.files
             :refer
             [ReadOneFile
              WriteOneFile]]
            [czlabclj.xlib.util.scheduler :refer [NulScheduler]]
            [czlabclj.xlib.util.core
             :refer
             [test-nonil
              test-cond
              ConvLong
              NiceFPath
              Try!
              PrintMutableObj
              MakeMMap]]
            [czlabclj.tardis.impl.exec :refer [MakeExecvisor]]
            [czlabclj.xlib.netty.io :refer [StopServer]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlabclj.tardis.core.consts]
        [czlabclj.xlib.util.consts]
        [czlabclj.tardis.core.sys]
        [czlabclj.xlib.util.wfs]
        [czlabclj.tardis.impl.dfts])

  (:import  [io.netty.channel Channel ChannelFuture
             ChannelFutureListener]
            [com.zotohlab.skaro.loaders AppClassLoader
             RootClassLoader ExecClassLoader]
            [com.zotohlab.frwk.core Versioned Identifiable
             Disposable Activable
             Hierarchial Startable]
            [com.zotohlab.frwk.util Schedulable]
            [com.zotohlab.frwk.i18n I18N]
            [com.zotohlab.wflow Job WorkFlow
             Activity Nihil]
            [com.zotohlab.skaro.core ConfigError]
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
(defn- inizContext "the context object has a set of properties, such as basic dir, which
                   is shared with other key components."

  ^czlabclj.xlib.util.core.Muble
  [^File baseDir]

  (let [etc (io/file baseDir DN_CFG)
        home (.getParentFile etc)]
    ;;(PrecondDir (io/file home DN_PATCH))
    (PrecondDir (io/file home DN_CONF))
    (PrecondDir (io/file home DN_DIST))
    (PrecondDir (io/file home DN_LIB))
    (PrecondDir (io/file home DN_BIN))
    (PrecondDir home)
    (PrecondDir etc)
    (doto (MakeContext)
      (.setf! K_BASEDIR home)
      (.setf! K_CFGDIR etc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopCLI "Stop all apps and processors."

  [^czlabclj.xlib.util.core.Muble ctx]

  (let [^File pid (.getf ctx K_PIDFILE)
        kp (.getf ctx K_KILLPORT)
        execv (.getf ctx K_EXECV) ]

    (when-not @STOPCLI
      (reset! STOPCLI true)
      (print "\n\n")
      (log/info "Closing the http discarder...")
      (StopServer (:bootstrap kp)
                  (:channel kp))
      (log/info "Http discarder closed. OK")
      ;;(when-not (nil? pid) (io/delete-file pid true))
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

  [^czlabclj.xlib.util.core.Muble ctx]

  (log/info "Enabling remote shutdown")
  (let [port (ConvLong (System/getProperty "skaro.kill.port") 4444)
        rc (MakeDiscardHTTPD "127.0.0.1"
                             port
                             #(stopCLI ctx) {}) ]
    (.setf! ctx K_KILLPORT rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cserver

  ^ServerLike
  [^File home]

  (let [cpu (NulScheduler)
        impl (MakeMMap)]
    (-> ^Activable
        cpu
        (.activate { :threads 1 }))
    (reify

      ServiceHandler

      (handle [this arg options]
        (let [options (or options {})
              w (ToWorkFlow arg)
              j (NewJob this w)]
          (doseq [[k v] options]
            (.setv j k v))
          (.run cpu (.reify (.startWith w)
                            (-> (Nihil/apply)
                                (.reify j))))))
      (handleError [_ e] )

      Disposable
      (dispose [_] (.dispose cpu))

      ServerLike

      (core [_] cpu)

      Elmt

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
        (nsb (System/getProperty "skaro.version")))

      Identifiable
      (id [_] K_CLISH))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pauseCLI ""

  ^Activity
  []

  (SimPTask "PauseCLI"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            ctx (.getLastResult j)
            s (.container j)]
        (log/debug "#### sys loader = "
                   (-> (ClassLoader/getSystemClassLoader)
                       (.getClass)
                       (.getName)))
        (PrintMutableObj ctx)
        (log/info "Container(s) are now running...")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hookShutdown ""

  ^Activity
  []

  (SimPTask "HookShutDown"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            ctx (.getLastResult j)
            cli (.getf ctx K_CLISH)]
        (.addShutdownHook (Runtime/getRuntime)
                          (ThreadFunc #(stopCLI ctx) false))
        (enableRemoteShutdown ctx)
        (log/info "Added shutdown hook.")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- writePID ""

  ^Activity
  []

  (SimPTask "WritePID"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            ctx (.getLastResult j)
            ^File home (.getf ctx K_BASEDIR)
            fp (io/file home "skaro.pid")]
        (WriteOneFile fp (ProcessPid))
        (.setf! ctx K_PIDFILE fp)
        (.deleteOnExit fp)
        (log/info "Wrote skaro.pid - OK.")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create and synthesize Execvisor.
(defn- primodial ""

  ^Activity
  []

  (SimPTask "Primodial"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            ctx (.getLastResult j)
            cl (.getf ctx K_EXEC_CZLR)
            cli (.getf ctx K_CLISH)
            wc (.getf ctx K_PROPS)
            cz (or (K_EXECV (K_COMPS wc)) "")]
        ;;(test-cond "conf file:exec-visor" (= cz "czlabclj.tardis.impl.Execvisor"))
        (log/info "Inside primodial() ---------------------------------------------->")
        (log/info "Execvisor = " cz)
        (let [^czlabclj.xlib.util.core.Muble
              execv (MakeExecvisor cli)]
          (.setf! ctx K_EXECV execv)
          (SynthesizeComponent execv {:ctx ctx})
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

  ^Activity
  []

  (SimPTask "LoadResource"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            ctx (.getLastResult j)
            rc (GetResource "czlabclj/tardis/etc/Resources"
                            (.getf ctx K_LOCALE))]
        (test-nonil "etc/resouces" rc)
        (.setf! ctx K_RCBUNDLE rc)
        (I18N/setBase rc)
        (log/info "Resource bundle found and loaded.")
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse skaro.conf
(defn- loadConf ""

  ^Activity
  []

  (SimPTask "LoadConf"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            ctx (.getLastResult j)
            ^File home (.getf ctx K_BASEDIR)
            cf (io/file home DN_CONF (name K_PROPS))]
        (log/info "About to parse config file " cf)
        (let [w (ReadEdn cf)
              cn (lcase (or (K_COUNTRY (K_LOCALE w)) ""))
              lg (lcase (or (K_LANG (K_LOCALE w)) "en"))
              loc (if (hgl? cn)
                    (Locale. lg cn)
                    (Locale. lg))]
          (log/info (str "Using locale: " loc))
          (doto ctx
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

  ^Activity
  []

  (SimPTask "SetupLoaders"
    (fn [^Job j]
      (let [^czlabclj.xlib.util.core.Muble
            x (.getLastResult j)
            cz (GetCldr)
            p (.getParent cz)
            pp (.getParent p)]
        (test-cond "bad classloaders" (and (instance? RootClassLoader pp)
                                           (instance? ExecClassLoader p)))
        (.setf! x K_ROOT_CZLR (.getParent p))
        (.setf! x K_EXEC_CZLR p)
        (log/info "Classloaders configured. using " (type cz))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- rtStart ""

  ^Activity
  []

  (SimPTask "RtStart"
    (fn [^Job j]
      (let [^czlabclj.tardis.core.sys.Elmt
            c (.container j)
            ^File
            home (.getv j :home)
            x (inizContext home)]
        (log/info "skaro.home " (NiceFPath home))
        (log/info "skaro.version= " (.version ^Versioned c))
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
(defn StartViaCLI ""

  [home]

  (let [cs (cserver home)
        a (-> (rtStart)
              (.chain (setupLoaders))
              (.chain (loadConf))
              (.chain (loadRes))
              (.chain (primodial))
              (.chain (writePID))
              (.chain (hookShutdown))
              (.chain (pauseCLI)))]
    (-> ^ServiceHandler
        cs
        (.handle a {:home home}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

