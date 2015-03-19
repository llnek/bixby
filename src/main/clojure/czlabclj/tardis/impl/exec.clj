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

  czlabclj.tardis.impl.exec

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.str :only [nsb strim hgl?]]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.impl.defaults]
        [czlabclj.xlib.jmx.core]
        [czlabclj.tardis.impl.sys
         :only
         [MakeKernel MakePodMeta MakeDeployer]]
        [czlabclj.xlib.util.core
         :only
         [LoadJavaProps test-nestr NiceFPath TryC
          ternary
          ConvLong MakeMMap juid test-nonil]]
        [czlabclj.xlib.util.files
         :only [ReadOneUrl ReadEdn]])

  (:import  [org.apache.commons.io.filefilter DirectoryFileFilter]
            [org.apache.commons.io FilenameUtils FileUtils]
            [java.io File FileFilter]
            [com.zotohlab.frwk.util IWin32Conf]
            [java.net URL]
            [java.util Date]
            [com.zotohlab.frwk.io IOUtils]
            [com.zotohlab.frwk.core Startable
             Versioned Hierarchial Identifiable]
            [com.zotohlab.frwk.server Component ComponentRegistry]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ExecvisorAPI

  ""

  (homeDir [_] )
  (confDir [_] )
  (podsDir [_] )
  (playDir [_] )
  (logDir [_] )
  (tmpDir [_] )
  (dbDir [_] )
  (blocksDir [_] )
  (getStartTime [_] )
  (kill9 [_] )
  (getUpTimeInMillis [_] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Check the app's manifest file.
(defn- chkManifest

  [^czlabclj.tardis.core.sys.Element execv
   app
   ^File des
   mf]

  (let [^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx execv)
        ^ComponentRegistry root (.getf ctx K_COMPS)
        ^ComponentRegistry apps (.lookup root K_APPS)
        ps (LoadJavaProps mf)
        vid (.getProperty ps "Implementation-Vendor-Id", "???")
        ver (.getProperty ps "Implementation-Version" "")
        cz (.getProperty ps "Main-Class" "") ]

    (test-nestr "POD-MainClass" cz)
    (test-nestr "POD-Version" ver)

    (log/info "Checking manifest for app: " app
              ", version: " ver ", main-class: " cz)

    ;;ps.gets("Manifest-Version")
    ;;.gets("Implementation-Title")
    ;;.gets("Implementation-Vendor-URL")
    ;;.gets("Implementation-Vendor")

    ;; synthesize the block meta component and register it as a application.
    (let [^czlabclj.tardis.core.sys.Element
          m (-> (MakePodMeta app ver nil
                             cz vid
                             (-> des (.toURI) (.toURL)))
                (SynthesizeComponent { :ctx ctx }))
          ^czlabclj.xlib.util.core.MubleAPI
          cx (.getCtx m) ]
      (.setf! cx K_EXECV execv)
      (.reg apps m)
      m)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Make sure the POD file is kosher.
;;
(defn- inspect-pod  ""

  [execv ^File des]

  (let [app (FilenameUtils/getBaseName (NiceFPath des))
        mf (File. des MN_FILE) ]
    (log/info "About to inspect app: " app)
    (log/info "app-dir: " des)
    (TryC
      (PrecondDir (File. des POD_INF))
      (PrecondDir (File. des POD_CLASSES))
      (PrecondDir (File. des POD_LIB))
      (PrecondDir (File. des META_INF))
      (PrecondFile (File. des CFG_APP_CF))
      (PrecondFile (File. des CFG_ENV_CF))
      (PrecondDir (File. des DN_CONF))
      (PrecondFile mf)
      (chkManifest execv app des mf) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Check all pods in the /apps directory to ensure they are kosher.
;;
(defn- inspect-pods ""

  [^czlabclj.tardis.core.sys.Element co]

  (let [^FileFilter ff DirectoryFileFilter/DIRECTORY
        ^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        ^File pd (.getf ctx K_PLAYDIR) ]
    (doseq [f (seq (.listFiles pd ff)) ]
      (inspect-pod co f))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic JMX support.
;;
(defn- start-jmx ""

  [^czlabclj.tardis.core.sys.Element co cfg]

  (log/info "JMX config " cfg)
  (TryC
    (let [^czlabclj.xlib.util.core.MubleAPI
          ctx (.getCtx co)
          port (ternary (:port cfg) 7777)
          host (nsb (:host cfg))
          ^czlabclj.xlib.jmx.core.JMXServer
          jmx (MakeJmxServer host) ]
      (.setRegistryPort jmx port)
      (.start ^Startable jmx)
      (.reg jmx co "com.zotohlab" "execvisor" ["root=skaro"])
      (.setf! ctx K_JMXSVR jmx)
      (log/info (str "JMXserver listening on: " host " "  port)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Kill the internal JMX server.
;;
(defn- stop-jmx ""

  [^czlabclj.tardis.core.sys.Element co]

  (TryC
    (let [^czlabclj.xlib.util.core.MubleAPI
          ctx (.getCtx co)
          ^Startable jmx (.getf ctx K_JMXSVR) ]
      (when-not (nil? jmx) (.stop jmx))
      (.setf! ctx K_JMXSVR nil)))
  (log/info "JMX connection terminated."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeExecvisor ""

  [parObj]

  (log/info "Creating execvisor, parent = " parObj)
  (let [impl (MakeMMap) ]
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toEDN [_ ] (.toEDN impl))

        Versioned
        (version [_] "1.0")

        Hierarchial
        (parent [_] parObj)

        Identifiable
        (id [_] K_EXECV )

        ExecvisorAPI

        (getUpTimeInMillis [_] (- (System/currentTimeMillis) START-TIME))
        (getStartTime [_] START-TIME)
        (homeDir [this] (MaybeDir (.getCtx this) K_BASEDIR))
        (confDir [this] (MaybeDir (.getCtx this) K_CFGDIR))
        (podsDir [this] (MaybeDir (.getCtx this) K_PODSDIR))
        (playDir [this] (MaybeDir (.getCtx this) K_PLAYDIR))
        (logDir [this] (MaybeDir (.getCtx this) K_LOGDIR))
        (tmpDir [this] (MaybeDir (.getCtx this) K_TMPDIR))
        (dbDir [this] (MaybeDir (.getCtx this) K_DBSDIR))
        (blocksDir [this] (MaybeDir (.getCtx this) K_BKSDIR))
        (kill9 [this] (.stop ^Startable parObj))

        ;;start the kernel
        Startable
        (start [this]
          (let [^czlabclj.xlib.util.core.MubleAPI
                ctx (.getCtx this)
                ^ComponentRegistry
                root (.getf ctx K_COMPS)
                ^Startable k (.lookup root K_KERNEL) ]
            (inspect-pods this)
            (.start k)))

        ;;stop the kernel
        (stop [this]
          (let [^czlabclj.xlib.util.core.MubleAPI
                ctx (.getCtx this)
                ^ComponentRegistry
                root (.getf ctx K_COMPS)
                ^Startable k (.lookup root K_KERNEL) ]
            (stop-jmx this)
            (.stop k)))  )

       { :typeid (keyword "czc.tardis.impl/Execvisor") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Execvisor is the master controller of everthing.
;;
(defmethod CompInitialize :czc.tardis.impl/Execvisor

  [^czlabclj.tardis.core.sys.Element co]

  (let [^czlabclj.tardis.impl.exec.ExecvisorAPI exec co
        ^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        cf (.getf ctx K_PROPS)
        comps (K_COMPS cf)
        regs (K_REGS cf)
        jmx  (K_JMXMGM cf) ]

    (log/info "Initializing component: Execvisor: " co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (let [^File home (.homeDir exec)
          bks (doto (File. home
                           (str DN_CFG "/" DN_BLOCKS))
                (.mkdir))
          apps (doto (File. home ^String DN_BOXX)
                 (.mkdir))
          tmp (doto (File. home ^String DN_TMP)
                (.mkdir))
          pods (File. home ^String DN_PODS)
          db (File. home ^String DN_DBS)
          log (doto (File. home ^String DN_LOGS)
                (.mkdir)) ]
      ;;(precondDir pods)
      (PrecondDir apps)
      (PrecondDir log)
      (PrecondDir tmp)
      ;;(precondDir db)
      (PrecondDir bks)

      (doto ctx
        (.setf! K_PODSDIR pods)
        (.setf! K_PLAYDIR apps)
        (.setf! K_LOGDIR log)
        (.setf! K_DBSDIR db)
        (.setf! K_TMPDIR tmp)
        (.setf! K_BKSDIR bks)))

    (let [^ComponentRegistry
          root (MakeRegistry :SystemRegistry K_COMPS "1.0" co)
          bks (MakeRegistry :BlocksRegistry K_BLOCKS "1.0" nil)
          apps (MakeRegistry :AppsRegistry K_APPS "1.0" nil)
          deployer (MakeDeployer)
          knl (MakeKernel)
          options { :ctx ctx } ]

      (.setf! ctx K_COMPS root)
      (.reg root deployer)
      (.reg root knl)
      (.reg root apps)
      (.reg root bks)

      (.setf! ctx K_EXECV co)

      (SynthesizeComponent root options)
      (SynthesizeComponent bks options)
      (SynthesizeComponent apps options)
      (SynthesizeComponent deployer options)
      (SynthesizeComponent knl options))

      (start-jmx co jmx)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-blockmeta ""

  ;; url points to block-meta file
  [^URL url]

  (let [impl (MakeMMap) ]
    ;;(.setf! impl :id (keyword (juid)))
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toEDN [_ ] (.toEDN impl))

        Component
        (id [_] (-> (.getf impl :metaInfo)
                    (:blockType)
                    (keyword)))
        (version [_] (-> (.getf impl :metaInfo)
                         (:version)))

        Hierarchial
        (parent [_] nil)

        BlockMeta

        (enabled? [_] (not (false? (-> (.getf impl :metaInfo)
                                       (:enabled)))))
        (getName [_] (-> (.getf impl :metaInfo)
                         (:name)))
        (metaUrl [_] url) )

      { :typeid (keyword "czc.tardis.impl/BlockMeta") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Description of a Block.
;;
(defmethod CompInitialize :czc.tardis.impl/BlockMeta

  [^czlabclj.tardis.impl.defaults.BlockMeta block]

  (let [^czlabclj.tardis.core.sys.Element co block
        url (.metaUrl block)
        cfg (ReadEdn url)
        info (:info cfg)
        conf (:conf cfg)]
    (test-nonil "Invalid block-meta file, no info section." info)
    (test-nonil "Invalid block-meta file, no conf section." conf)
    (log/info "Initializing BlockMeta: " url)
    (.setAttr! co :metaInfo info)
    (.setAttr! co :dftOptions conf)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters.  each block has a meta data file describing
;; its functions and features.
;; This registry loads these meta files and adds them to the registry.
;;
(defmethod CompInitialize :czc.tardis.impl/BlocksRegistry

  [^czlabclj.tardis.core.sys.Element co]

  (let [^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        bDir (.getf ctx K_BKSDIR)
        fs (IOUtils/listFiles ^File bDir "meta" false) ]
    (doseq [^File f (seq fs) ]
      (let [^czlabclj.tardis.core.sys.Element
            b (-> (make-blockmeta (-> f (.toURI)(.toURL)))
                  (SynthesizeComponent {}) ) ]
        (.reg ^ComponentRegistry co b)
        (log/info "Added one block: " (.id ^Identifiable b)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private exec-eof nil)

