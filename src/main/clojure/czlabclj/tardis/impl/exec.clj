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

  czlabclj.tardis.impl.exec

  (:require [czlabclj.xlib.util.str :refer [nsb strim hgl? ToKW]]
            [czlabclj.xlib.util.mime :refer [SetupCache]]
            [czlabclj.xlib.util.files :refer [ListFiles Unzip]]
            [czlabclj.xlib.util.process :refer [SafeWait]]
            [czlabclj.tardis.impl.dfts :refer [MakePodMeta]]
            [czlabclj.xlib.util.core
             :refer
             [LoadJavaProps
              test-nestr
              NiceFPath
              TryC
              notnil?
              NewRandom
              GetCwd
              ConvLong
              MakeMMap
              juid
              test-nonil]]
            [czlabclj.xlib.util.format :refer [ReadEdn]]
            [czlabclj.xlib.util.files
             :refer [Mkdirs ReadOneUrl]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlabclj.tardis.core.consts]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.impl.dfts]
        [czlabclj.xlib.jmx.core]
        [czlabclj.tardis.impl.ext])

  (:import  [org.apache.commons.io.filefilter DirectoryFileFilter]
            [org.apache.commons.io FilenameUtils FileUtils]
            [com.zotohlab.skaro.loaders AppClassLoader]
            [org.apache.commons.lang3 StringUtils]
            [java.io File FileFilter]
            [java.security SecureRandom]
            [java.util.zip ZipFile]
            [java.net URL]
            [java.util Date]
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
(defprotocol ExecVisor

  "ExecVisor API."

  (homeDir [_] )
  (confDir [_] )
  (blocksDir [_] )
  (getStartTime [_] )
  (kill9 [_] )
  (getUpTimeInMillis [_] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Check the app's manifest file.
(defn- chkManifest

  ^czlabclj.tardis.impl.dfts.PODMeta

  [^czlabclj.tardis.core.sys.Elmt
   execv
   app
   ^File des mf]

  (let [^czlabclj.xlib.util.core.Muble
        ctx (.getCtx execv)
        ^ComponentRegistry
        apps (-> ^ComponentRegistry
                 (.getf ctx K_COMPS)
                 (.lookup K_APPS))
        ps (LoadJavaProps mf)
        vid (.getProperty ps "Implementation-Vendor-Id", "???")
        ver (.getProperty ps "Implementation-Version" "")
        cz (.getProperty ps "Main-Class" "") ]

    (log/info "Checking manifest for app: " app
              ", version: " ver
              ", main-class: " cz)

    ;; synthesize the pod meta component and register it
    ;; as a application.
    (let [^czlabclj.tardis.core.sys.Elmt
          m (-> (MakePodMeta app ver
                             cz vid
                             (io/as-url des))
                (SynthesizeComponent { :ctx ctx }))
          ^czlabclj.xlib.util.core.Muble
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
        mf (io/file des MN_FILE) ]
    (log/info "app dir : " des)
    (log/info "inspecting...")
    (TryC
      (PrecondDir (io/file des DN_CFG))
      (PrecondFile (io/file des CFG_APP_CF))
      (PrecondFile (io/file des CFG_ENV_CF))
      (PrecondDir (io/file des DN_CONF))
      (PrecondFile mf)
      (chkManifest execv app des mf) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic JMX support.
;;
(defn- startJmx ""

  [^czlabclj.tardis.core.sys.Elmt co cfg]

  (log/info "JMX config " cfg)
  (TryC
    (let [^czlabclj.xlib.util.core.Muble
          ctx (.getCtx co)
          port (or (:port cfg) 7777)
          host (nsb (:host cfg))
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

  [^czlabclj.tardis.core.sys.Elmt co]

  (TryC
    (let [^czlabclj.xlib.util.core.Muble
          ctx (.getCtx co)
          ^Startable
          jmx (.getf ctx K_JMXSVR) ]
      (when-not (nil? jmx)
        (.stop jmx))
      (.setf! ctx K_JMXSVR nil)))
  (log/info "JMX connection terminated."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod

  [^czlabclj.tardis.core.sys.Elmt co
   ^czlabclj.tardis.impl.dfts.PODMeta pod]

  (TryC
    (let [cache (.getAttr co K_CONTAINERS)
          cid (.id ^Identifiable pod)
          app (.moniker pod)
          ctr (MakeContainer pod)]
      (log/debug "Start pod cid = " cid ", app = " app)
      (.setAttr! co K_CONTAINERS (assoc cache cid ctr))
      true)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods ""

  [^czlabclj.tardis.core.sys.Elmt co]

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
(defn MakeExecvisor "Create a ExecVisor."

  ^czlabclj.tardis.impl.exec.ExecVisor
  [parObj]

  (log/info "Creating execvisor, parent = " parObj)
  (let [impl (MakeMMap {K_CONTAINERS {}}) ]
    (with-meta
      (reify

        Versioned
        (version [_] "1.0")

        Hierarchial
        (parent [_] parObj)

        Identifiable
        (id [_] K_EXECV )

        Elmt

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toEDN [_ ] (.toEDN impl))

        ExecVisor

        (getUpTimeInMillis [_] (- (System/currentTimeMillis) START-TIME))
        (getStartTime [_] START-TIME)
        (homeDir [this] (MaybeDir (.getCtx this) K_BASEDIR))
        (confDir [this] (MaybeDir (.getCtx this) K_CFGDIR))
        (blocksDir [this] (MaybeDir (.getCtx this) K_BKSDIR))
        (kill9 [this] (.stop ^Startable parObj))

        Startable
        (start [this]
          (->> (inspectApp this (GetCwd))
               (ignitePod this)))

        (stop [this]
            (stopJmx this)
            (stopPods this)) )

       { :typeid (ToKW "czc.tardis.impl" "ExecVisor") }
  )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/PODMeta

  [^czlabclj.tardis.core.sys.Elmt co]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Execvisor is the master controller of everthing.
;;
(defmethod CompInitialize :czc.tardis.impl/ExecVisor

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^czlabclj.tardis.impl.exec.ExecVisor exec co
        ^czlabclj.xlib.util.core.Muble
        ctx (.getCtx co)
        ^File base (.getf ctx K_BASEDIR)
        cf (.getf ctx K_PROPS)
        comps (K_COMPS cf)
        regs (K_REGS cf)
        jmx  (K_JMXMGM cf) ]

    (SetupCache (-> (io/file base DN_CFG
                             "app/mime.properties")
                    (io/as-url)))

    (log/info "Initializing component: ExecVisor: " co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (let [^File home (.homeDir exec)
          bks (io/file home DN_CFG DN_BLOCKS) ]
      (PrecondDir bks)
      (doto ctx
        (.setf! K_BKSDIR bks)))

    (let [^ComponentRegistry
          root (MakeRegistry :SystemRegistry K_COMPS "1.0" co)
          bks (MakeRegistry :BlocksRegistry K_BLOCKS "1.0" nil)
          apps (MakeRegistry :AppsRegistry K_APPS "1.0" nil)
          options { :ctx ctx } ]

      (.setf! ctx K_COMPS root)
      (.setf! ctx K_EXECV co)
      (.reg root apps)
      (.reg root bks)

      (SynthesizeComponent root options)
      (SynthesizeComponent bks options)
      (SynthesizeComponent apps options))

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

        Hierarchial
        (parent [_] nil)

        Elmt

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

        EmitMeta

        (enabled? [_] (not (false? (-> (.getf impl :metaInfo)
                                       (:enabled)))))
        (getName [_] (-> (.getf impl :metaInfo)
                         (:name)))
        (metaUrl [_] url) )

      { :typeid (ToKW "czc.tardis.impl" "EmitMeta") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Description of a Block.
;;
(defmethod CompInitialize :czc.tardis.impl/EmitMeta

  [^czlabclj.tardis.impl.dfts.EmitMeta block]

  (let [^czlabclj.tardis.core.sys.Elmt co block
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

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^czlabclj.xlib.util.core.Muble
        ctx (.getCtx co)
        ^File bDir (.getf ctx K_BKSDIR)
        fs (ListFiles bDir "meta" false) ]
    (doseq [^File f fs ]
      (let [^czlabclj.tardis.core.sys.Elmt
            b (-> (makeBlockMeta (io/as-url f))
                  (SynthesizeComponent {}) ) ]
        (.reg ^ComponentRegistry co b)
        (log/info "Added one block: " (.id ^Identifiable b)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/SystemRegistry

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "CompInitialize: SystemRegistry: " (.id ^Identifiable co))
  co
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.impl/AppsRegistry

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "CompInitialize: AppsRegistry: " (.id ^Identifiable co))
  co
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

