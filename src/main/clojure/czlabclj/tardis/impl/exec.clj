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
        [czlabclj.xlib.util.mime :only [SetupCache]]
        [czlabclj.xlib.util.files :only [Unzip]]
        [czlabclj.xlib.util.process :only [SafeWait]]
        [czlabclj.tardis.core.consts]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.impl.dfts]
        [czlabclj.xlib.jmx.core]
        [czlabclj.tardis.impl.dfts :only [MakePodMeta]]
        [czlabclj.tardis.impl.ext]
        [czlabclj.xlib.util.core
         :only
         [LoadJavaProps test-nestr NiceFPath TryC
          notnil? NewRandom
          ConvLong MakeMMap juid test-nonil]]
        [czlabclj.xlib.util.format :only [ReadEdn]]
        [czlabclj.xlib.util.files
         :only [Mkdirs ReadOneUrl]])

  (:import  [org.apache.commons.io.filefilter DirectoryFileFilter]
            [org.apache.commons.io FilenameUtils FileUtils]
            [com.zotohlab.skaro.loaders AppClassLoader]
            [org.apache.commons.lang3 StringUtils]
            [java.io File FileFilter]
            [com.zotohlab.frwk.util IWin32Conf]
            [java.security SecureRandom]
            [java.util.zip ZipFile]
            [java.net URL]
            [java.util Date]
            [com.zotohlab.frwk.io IOUtils]
            [com.zotohlab.frwk.core Startable Disposable
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

  ^czlabclj.tardis.impl.dfts.PODMeta
  [^czlabclj.tardis.core.sys.Element execv
   app
   ^File des
   mf]

  (let [^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx execv)
        ^ComponentRegistry
        root (.getf ctx K_COMPS)
        ^ComponentRegistry
        apps (.lookup root K_APPS)
        ps (LoadJavaProps mf)
        vid (.getProperty ps "Implementation-Vendor-Id", "???")
        ver (.getProperty ps "Implementation-Version" "")
        cz (.getProperty ps "Main-Class" "") ]

    (test-nestr "POD-MainClass" cz)
    (test-nestr "POD-Version" ver)

    (log/info "Checking manifest for app: " app
              ", version: " ver
              ", main-class: " cz)

    ;;ps.gets("Manifest-Version")
    ;;.gets("Implementation-Title")
    ;;.gets("Implementation-Vendor-URL")
    ;;.gets("Implementation-Vendor")

    ;; synthesize the pod meta component and register it as a application.
    (let [^czlabclj.tardis.core.sys.Element
          m (-> (MakePodMeta app ver
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
;; Make sure the app setup is kosher.
;;
(defn- inspectApp  ""

  ^czlabclj.tardis.impl.dfts.PODMeta
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
(defn- inspectApps ""

  [^czlabclj.tardis.core.sys.Element co]

  (let [^FileFilter ff DirectoryFileFilter/DIRECTORY
        ^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        ^File pd (.getf ctx K_PLAYDIR) ]
    (doseq [f (seq (.listFiles pd ff)) ]
      (inspectApp co f))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic JMX support.
;;
(defn- startJmx ""

  [^czlabclj.tardis.core.sys.Element co cfg]

  (log/info "JMX config " cfg)
  (TryC
    (let [^czlabclj.xlib.util.core.MubleAPI
          ctx (.getCtx co)
          port (or (:port cfg) 7777)
          host (nsb (:host cfg))
          ^czlabclj.xlib.jmx.core.JMXServer
          jmx (MakeJmxServer host) ]
      (.setRegistryPort jmx port)
      (.start ^Startable jmx)
      (.reg jmx co "com.zotohlab" "execvisor" ["root=skaro"])
      (.setf! ctx K_JMXSVR jmx)
      (log/info "JMXserver listening on: " host " "  port))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Kill the internal JMX server.
;;
(defn- stopJmx ""

  [^czlabclj.tardis.core.sys.Element co]

  (TryC
    (let [^czlabclj.xlib.util.core.MubleAPI
          ctx (.getCtx co)
          ^Startable jmx (.getf ctx K_JMXSVR) ]
      (when-not (nil? jmx) (.stop jmx))
      (.setf! ctx K_JMXSVR nil)))
  (log/info "JMX connection terminated."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scan for pods and deploy them to the /apps directory.  The pod file's
;; contents are unzipped verbatim to the target subdirectory under /apps.
;;
(defn- deployOnePod  ""

  [^File src ^File apps]

  (let [^File app (FilenameUtils/getBaseName (NiceFPath src))
        des (File. apps ^String app)]
    (when-not (.exists des)
      (Unzip src des))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- undeployOnePod  ""

  [^czlabclj.tardis.core.sys.Element co
   ^String app]

  (let [^czlabclj.xlib.util.core.MubleAPI ctx (.getCtx co)
        dir (File. ^File (.getf ctx K_PLAYDIR) app)]
    (when (.exists dir)
      (FileUtils/deleteDirectory dir))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- deployPods ""

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "Preparing to deploy pods...")
  (let [^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        ^File py (.getf ctx K_PLAYDIR)
        ^File pd (.getf ctx K_PODSDIR)]
    (with-local-vars [sum 0]
      (when (.isDirectory pd)
        (log/info "Scanning for pods in: " pd)
        (doseq [^File f (seq (IOUtils/listFiles pd "pod" false)) ]
          (var-set sum (inc @sum))
          (deployOnePod f py)))
      (log/info "Total pods deployed: " @sum))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeStartPod

  [^czlabclj.tardis.core.sys.Element co
   cset
   ^czlabclj.tardis.impl.dfts.PODMeta pod]

  (TryC
    (let [cache (.getAttr co K_CONTAINERS)
          cid (.id ^Identifiable pod)
          app (.moniker pod)
          ctr (if (and (not (empty? cset))
                       (not (contains? cset app)))
                nil
                (MakeContainer pod))]
      (log/debug "Start pod? cid = " cid ", app = " app " !! cset = " cset)
      (if (notnil? ctr)
        (do
          (.setAttr! co K_CONTAINERS (assoc cache cid ctr))
          true)
        (do
          (log/info "Execvisor: pod " cid " disabled.")
          false) ) )
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startPods ""

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "Preparing to start pods...")
  (let [^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        ^ComponentRegistry
        root (.getf ctx K_COMPS)
        wc (.getf ctx K_PROPS)
        endorsed (-> (:endorsed (K_APPS wc))
                     (or "")
                     strim)
        ^czlabclj.tardis.core.sys.Registry
        apps (.lookup root K_APPS)
         ;; start all apps or only those endorsed.
        cs (if (or (= "*" endorsed)
                   (= "" endorsed))
             #{}
             (into #{} (seq (StringUtils/split endorsed ",;"))))]
    ;; need this to prevent deadlocks amongst pods
    ;; when there are dependencies
    ;; TODO: need to handle this better
    (with-local-vars [r nil]
      (doseq [[k v] (seq* apps)]
        (when @r
          (SafeWait (* 1000 (Math/max (int 1) (int r)))))
        (when (maybeStartPod co cs v)
          (var-set r (-> (NewRandom) (.nextInt 6))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods ""

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "Preparing to stop pods...")
  (let [cs (.getAttr co K_CONTAINERS) ]
    (doseq [[k v] (seq cs) ]
      (.stop ^Startable v))
    (doseq [[k v] (seq cs) ]
      (.dispose ^Disposable v))
    (.setAttr! co K_CONTAINERS {})
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeExecvisor ""

  [parObj]

  (log/info "Creating execvisor, parent = " parObj)
  (let [impl (MakeMMap) ]
    (.setf! impl K_CONTAINERS {})
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

        Startable
        (start [this]
          (let []
            (inspectApps this)
            (startPods this)))

        (stop [this]
          (let []
            (stopJmx this)
            (stopPods this)))  )

       { :typeid (keyword "czc.tardis.impl/Execvisor") }
  )))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/PODMeta

  [^czlabclj.tardis.core.sys.Element co]

  (let [^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        rcl (.getf ctx K_EXEC_CZLR)
        ^URL url (.srcUrl ^czlabclj.tardis.impl.dfts.PODMeta co)
        cl  (AppClassLoader. rcl) ]
    (.configure cl (NiceFPath (File. (.toURI  url))) )
    (.setf! ctx K_APP_CZLR cl)
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Execvisor is the master controller of everthing.
;;
(defmethod CompInitialize :czc.tardis.impl/Execvisor

  [^czlabclj.tardis.core.sys.Element co]

  (let [^czlabclj.tardis.impl.exec.ExecvisorAPI exec co
        ^czlabclj.xlib.util.core.MubleAPI
        ctx (.getCtx co)
        ^File base (.getf ctx K_BASEDIR)
        cf (.getf ctx K_PROPS)
        comps (K_COMPS cf)
        regs (K_REGS cf)
        jmx  (K_JMXMGM cf) ]

    (SetupCache (-> (File. base (str DN_CFG "/app/mime.properties"))
                    (.toURI)
                    (.toURL )))

    (log/info "Initializing component: Execvisor: " co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (let [^File home (.homeDir exec)
          bks (Mkdirs (File. home
                             (str DN_CFG "/" DN_BLOCKS)))
          apps (Mkdirs (File. home ^String DN_BOXX))
          tmp (Mkdirs (File. home ^String DN_TMP))
          pods (File. home DN_PODS)
          db (File. home DN_DBS)
          log (Mkdirs (File. home DN_LOGS))]
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
          ;;deployer (MakeDeployer)
          ;;knl (MakeKernel)
          options { :ctx ctx } ]

      (.setf! ctx K_COMPS root)
      (.setf! ctx K_EXECV co)

      ;;(.reg root deployer)
      ;;(.reg root knl)
      (.reg root apps)
      (.reg root bks)

      (SynthesizeComponent root options)
      (SynthesizeComponent bks options)
      (SynthesizeComponent apps options)

      (deployPods co))
      ;;(SynthesizeComponent deployer options)
      ;;(SynthesizeComponent knl options))

    (startJmx co jmx)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeBlockMeta ""

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

        EmitMeta

        (enabled? [_] (not (false? (-> (.getf impl :metaInfo)
                                       (:enabled)))))
        (getName [_] (-> (.getf impl :metaInfo)
                         (:name)))
        (metaUrl [_] url) )

      { :typeid (keyword "czc.tardis.impl/EmitMeta") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Description of a Block.
;;
(defmethod CompInitialize :czc.tardis.impl/EmitMeta

  [^czlabclj.tardis.impl.dfts.EmitMeta block]

  (let [^czlabclj.tardis.core.sys.Element co block
        url (.metaUrl block)
        cfg (ReadEdn url)
        info (:info cfg)
        conf (:conf cfg)]
    (test-nonil "Invalid block-meta file, no info section." info)
    (test-nonil "Invalid block-meta file, no conf section." conf)
    (log/info "Initializing EmitMeta: " url)
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
            b (-> (makeBlockMeta (-> f (.toURI)(.toURL)))
                  (SynthesizeComponent {}) ) ]
        (.reg ^ComponentRegistry co b)
        (log/info "Added one block: " (.id ^Identifiable b)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/SystemRegistry

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "CompInitialize: SystemRegistry: " (.id ^Identifiable co))
  co
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/AppsRegistry

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "CompInitialize: AppsRegistry: " (.id ^Identifiable co))
  co
)




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private exec-eof nil)

