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

  cmzlabclj.tardis.impl.exec

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabclj.tardis.core.constants])
  (:use [cmzlabclj.tardis.core.sys])
  (:use [cmzlabclj.tardis.impl.defaults])
  (:use [cmzlabclj.nucleus.jmx.core])
  (:use [cmzlabclj.tardis.impl.sys
         :only [MakeKernel MakePodMeta MakeDeployer] ])
  (:use [ cmzlabclj.nucleus.util.core
         :only [LoadJavaProps test-nestr NiceFPath TryC
                ConvLong MakeMMap juid test-nonil] ])
  (:use [ cmzlabclj.nucleus.util.str :only [nsb strim hgl?] ])
  (:use [ cmzlabclj.nucleus.util.ini :only [ParseInifile] ])
  (:import (org.apache.commons.io.filefilter DirectoryFileFilter))
  (:import (org.apache.commons.io FilenameUtils FileUtils))
  (:import (java.io File FileFilter))
  (:import (java.net URL))
  (:import (java.util Date))
  (:import (com.zotohlab.frwk.io IOUtils))
  (:import (com.zotohlab.frwk.core Startable Versioned Hierarchial Identifiable))
  (:import (com.zotohlab.frwk.server Component ComponentRegistry)))

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
;;
(defn- chkManifest

  [^cmzlabclj.tardis.core.sys.Element execv
   app
   ^File des
   mf]

  (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx execv)
         ^ComponentRegistry root (.getf ctx K_COMPS)
         ^ComponentRegistry apps (.lookup root K_APPS)
         ps (LoadJavaProps mf)
         vid (.getProperty ps "Implementation-Vendor-Id", "???")
         ver (.getProperty ps "Implementation-Version" "")
         cz (.getProperty ps "Main-Class" "") ]

    (test-nestr "POD-MainClass" cz)
    (test-nestr "POD-Version" ver)

    (log/info "checking manifest for app: " app ", version: " ver ", main-class: " cz)

    ;;ps.gets("Manifest-Version")
    ;;.gets("Implementation-Title")
    ;;.gets("Implementation-Vendor-URL")
    ;;.gets("Implementation-Vendor")

    ;; synthesize the block meta component and register it as a application.
    (let [ ^cmzlabclj.tardis.core.sys.Element
           m (-> (MakePodMeta app ver nil cz vid (-> des (.toURI) (.toURL)))
                 (SynthesizeComponent { :ctx ctx })) ]
      (.setf! ^cmzlabclj.nucleus.util.core.MubleAPI (.getCtx m) K_EXECV execv)
      (.reg apps m)
      m)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Make sure the POD file is kosher.
;;
(defn- inspect-pod  ""

  [execv ^File des]

  (let [ app (FilenameUtils/getBaseName (NiceFPath des))
         mf (File. des ^String MN_FILE) ]
    (log/info "About to inspect app: " app)
    (log/info "app-dir: " des)
    (TryC
        (PrecondDir (File. des ^String POD_INF))
        (PrecondDir (File. des ^String POD_CLASSES))
        (PrecondDir (File. des ^String POD_LIB))
        (PrecondDir (File. des ^String META_INF))
        (PrecondFile (File. des ^String CFG_APP_CF))
        (PrecondFile (File. des ^String CFG_ENV_CF))
        (PrecondDir (File. des ^String DN_CONF))
        (PrecondFile mf)
        (chkManifest execv app des mf) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Check all pods in the /apps directory to ensure they are kosher.
;;
(defn- inspect-pods ""

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx co)
         ^FileFilter ff DirectoryFileFilter/DIRECTORY
         ^File pd (.getf ctx K_PLAYDIR) ]
    (doseq [ f (seq (.listFiles pd ff)) ]
      (inspect-pod co f))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic JMX support.
;;
(defn- start-jmx ""

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  (log/info "JMX config " cfg)
  (TryC
    (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx co)
           port (ConvLong (nsb (get cfg "port")) 7777)
           host (nsb (get cfg "host"))
           ^cmzlabclj.nucleus.jmx.core.JMXServer
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

  [^cmzlabclj.tardis.core.sys.Element co]

  (TryC
    (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx co)
           ^Startable jmx (.getf ctx K_JMXSVR) ]
      (when-not (nil? jmx) (.stop jmx))
      (.setf! ctx K_JMXSVR nil)))
  (log/info "JMX connection terminated."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeExecvisor ""

  [parObj]

  (log/info "creating execvisor, parent = " parObj)
  (let [ impl (MakeMMap) ]
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toJson [_ ] (.toJson impl))

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

        Startable
        (start [this]
          (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx this)
                 ^ComponentRegistry root (.getf ctx K_COMPS)
                 ^Startable k (.lookup root K_KERNEL) ]
            (inspect-pods this)
            (.start k)))
        (stop [this]
          (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx this)
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

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [ ^cmzlabclj.nucleus.util.core.MubleAPI ctx (.getCtx co)
         ^cmzlabclj.nucleus.util.ini.IWin32Conf
         cf (.getf ctx K_PROPS)
         comps (.getSection cf K_COMPS)
         regs (.getSection cf K_REGS)
         jmx  (.getSection cf K_JMXMGM) ]

    (log/info "initializing component: Execvisor: " co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (let [ ^File home (.homeDir ^cmzlabclj.tardis.impl.exec.ExecvisorAPI co)
           bks (doto (File. home (str DN_CFG "/" DN_BLOCKS)) (.mkdir))
           apps (doto (File. home ^String DN_BOXX) (.mkdir))
           tmp (doto (File. home ^String DN_TMP) (.mkdir))
           pods (File. home ^String DN_PODS)
           db (File. home ^String DN_DBS)
           log (doto (File. home ^String DN_LOGS) (.mkdir)) ]
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
    (start-jmx co jmx)
    (let [ ^ComponentRegistry root (MakeComponentRegistry :SystemRegistry K_COMPS "1.0" co)
           bks (MakeComponentRegistry :BlocksRegistry K_BLOCKS "1.0" nil)
           apps (MakeComponentRegistry :AppsRegistry K_APPS "1.0" nil)
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
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-blockmeta ""

  ;; url points to block-meta file
  [^URL url]

  (let [ impl (MakeMMap) ]
    (.setf! impl :id (keyword (juid)))
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toJson [_ ] (.toJson impl))

        Component
        (id [_] (.getf impl :id))
        (version [_] "1.0")

        Hierarchial
        (parent [_] nil)

        BlockMeta

        (enabled? [_] (true? (.getf impl :active)))
        (metaUrl [_] url) )

      { :typeid (keyword "czc.tardis.impl/BlockMeta") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Description of a Block.
;;
(defmethod CompInitialize :czc.tardis.impl/BlockMeta

  [^cmzlabclj.tardis.impl.defaults.BlockMeta block]

  (let [ ^URL url (.metaUrl block)
         ^cmzlabclj.nucleus.util.ini.IWin32Conf
         cfg (ParseInifile url)
         inf (.getSection cfg "info") ]
    (test-nonil "Invalid block-meta file, no info section." inf)
    (log/info "initializing BlockMeta: " url)
    (let [ cz (strim (.optString cfg "info" "block-type" ""))
           ^cmzlabclj.tardis.core.sys.Element co block  ]
      (when (hgl? cz)
            (.setAttr! co :id (keyword cz))
            (.setAttr! co :active true) )
      (.setAttr! co :version (strim (.optString cfg "info" "version" "")))
      (.setAttr! co :name (strim (.optString cfg "info" "name" "")))
      co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters.  each block has a meta data file describing
;; its functions and features.
;; This registry loads these meta files and adds them to the registry.
;;
(defmethod CompInitialize :czc.tardis.impl/BlocksRegistry

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [ ^cmzlabclj.nucleus.util.core.MubleAPI
         ctx (.getCtx co)
         bDir (.getf ctx K_BKSDIR)
         fs (IOUtils/listFiles ^File bDir "meta" false) ]
    (doseq [ ^File f (seq fs) ]
      (let [ ^cmzlabclj.tardis.core.sys.Element
             b (-> (make-blockmeta (-> f (.toURI)(.toURL)))
                   (SynthesizeComponent {}) ) ]
        (.reg ^ComponentRegistry co b)
        (log/info "added one block: " (.id ^Identifiable b)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private exec-eof nil)

