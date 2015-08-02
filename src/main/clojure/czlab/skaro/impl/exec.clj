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

  czlab.skaro.impl.exec

  (:require [czlab.xlib.util.str :refer [nsb strim hgl? ToKW]]
            [czlab.xlib.util.mime :refer [SetupCache]]
            [czlab.xlib.util.files :refer [ListFiles Unzip]]
            [czlab.xlib.util.process :refer [SafeWait]]
            [czlab.skaro.impl.dfts :refer [MakePodMeta]]
            [czlab.xlib.util.core
             :refer
             [LoadJavaProps
              test-nestr
              FPath
              tryletc tryc
              notnil?
              NewRandom
              GetCwd
              ConvLong
              MakeMMap
              Muble
              juid
              test-nonil]]
            [czlab.xlib.util.format :refer [ReadEdn]]
            [czlab.xlib.util.files
             :refer [Mkdirs ReadOneUrl]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.dfts]
        [czlab.xlib.jmx.core]
        [czlab.skaro.impl.ext])

  (:import  [org.apache.commons.io.filefilter DirectoryFileFilter]
            [org.apache.commons.io FilenameUtils FileUtils]
            [com.zotohlab.skaro.loaders AppClassLoader]
            [com.zotohlab.skaro.core Context]
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

  ^czlab.skaro.impl.dfts.PODMeta

  [^czlab.xlib.util.core.Muble
   execv
   app
   ^File des mf]

  (let [^czlab.xlib.util.core.Muble
        ctx (-> ^Context execv (.getx))
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
    (let [^czlab.xlib.util.core.Muble
          m (-> (MakePodMeta app ver
                             cz vid
                             (io/as-url des))
                (SynthesizeComponent { :ctx ctx }))
          ^czlab.xlib.util.core.Muble
          cx (-> ^Context m (.getx)) ]
      (.setf! cx K_EXECV execv)
      (.reg apps m)
      m)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Make sure the app setup is kosher.
;;
(defn- inspectApp  ""

  ^czlab.skaro.impl.dfts.PODMeta
  [execv ^File des]

  (let [app (FilenameUtils/getBaseName (FPath des))
        mf (io/file des MN_FILE) ]
    (log/info "app dir : " des)
    (log/info "inspecting...")
    (tryc
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

  [^czlab.xlib.util.core.Muble co cfg]

  (log/info "JMX config " cfg)
    (tryletc [^czlab.xlib.util.core.Muble
          ctx (-> ^Context co (.getx))
          port (or (:port cfg) 7777)
          host (nsb (:host cfg))
          jmx (MakeJmxServer host) ]
      (.setRegistryPort jmx port)
      (.start ^Startable jmx)
      (.reg jmx co "com.zotohlab" "execvisor" ["root=skaro"])
      (.setf! ctx K_JMXSVR jmx)
      (log/info "JMXserver listening on: " host " "  port))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Kill the internal JMX server.
;;
(defn- stopJmx ""

  [^czlab.xlib.util.core.Muble co]

    (tryletc [^czlab.xlib.util.core.Muble
          ctx (-> ^Context co (.getx))
          ^Startable
          jmx (.getf ctx K_JMXSVR) ]
      (when-not (nil? jmx)
        (.stop jmx))
      (.setf! ctx K_JMXSVR nil))
  (log/info "JMX connection terminated."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod

  [^czlab.xlib.util.core.Muble co
   ^czlab.skaro.impl.dfts.PODMeta pod]

    (tryletc [cache (.getf co K_CONTAINERS)
          cid (.id ^Identifiable pod)
          app (.moniker pod)
          ctr (MakeContainer pod)]
      (log/debug "Start pod cid = " cid ", app = " app)
      (.setf! co K_CONTAINERS (assoc cache cid ctr))
      true)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods ""

  [^czlab.xlib.util.core.Muble co]

  (log/info "Preparing to stop pods...")
  (let [cs (.getf co K_CONTAINERS) ]
    (doseq [[k v] (seq cs) ]
      (.stop ^Startable v))
    (doseq [[k v] (seq cs) ]
      (.dispose ^Disposable v))
    (.setf! co K_CONTAINERS {})
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeExecvisor "Create a ExecVisor."

  ^czlab.skaro.impl.exec.ExecVisor
  [parObj]

  (log/info "Creating execvisor, parent = " parObj)
  (let [impl (MakeMMap {K_CONTAINERS {}})
        ctxt (atom (MakeMMap)) ]

    (with-meta
      (reify

        Versioned
        (version [_] "1.0")

        Hierarchial
        (parent [_] parObj)

        Identifiable
        (id [_] K_EXECV )

        Context
        (setx [_ x] (reset! ctxt x))
        (getx [_] @ctxt)

        Muble

        (setf! [_ a v] (.setf! impl a v) )
        (clrf! [_ a] (.clrf! impl a) )
        (getf [_ a] (.getf impl a) )
        (seq* [_] )
        (clear! [_] (.clear! impl))
        (toEDN [_ ] (.toEDN impl))

        ExecVisor

        (getUpTimeInMillis [_] (- (System/currentTimeMillis) START-TIME))
        (getStartTime [_] START-TIME)
        (homeDir [this] (MaybeDir (.getx this) K_BASEDIR))
        (confDir [this] (MaybeDir (.getx this) K_CFGDIR))
        (blocksDir [this] (MaybeDir (.getx this) K_BKSDIR))
        (kill9 [this] (.stop ^Startable parObj))

        Startable
        (start [this]
          (->> (inspectApp this (GetCwd))
               (ignitePod this)))

        (stop [this]
            (stopJmx this)
            (stopPods this)) )

       { :typeid (ToKW "czc.skaro.impl" "ExecVisor") }
  )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/PODMeta

  [^czlab.xlib.util.core.Muble co]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Execvisor is the master controller of everthing.
;;
(defmethod CompInitialize :czc.skaro.impl/ExecVisor

  [^czlab.xlib.util.core.Muble co]

  (let [^czlab.skaro.impl.exec.ExecVisor exec co
        ^czlab.xlib.util.core.Muble
        ctx (-> ^Context co (.getx))
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

  (let [impl (MakeMMap)
        ctxt (atom (MakeMMap)) ]

    ;;(.setf! impl :id (keyword (juid)))
    (with-meta
      (reify

        Hierarchial
        (parent [_] nil)

        Context

        (setx [_ x] (reset! ctxt x))
        (getx [_] @ctxt)

        Muble

        (setf! [_ a v] (.setf! impl a v) )
        (clrf! [_ a] (.clrf! impl a) )
        (getf [_ a] (.getf impl a) )
        (seq* [_] )
        (clear! [_] (.clear! impl))
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

      { :typeid (ToKW "czc.skaro.impl" "EmitMeta") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Description of a Block.
;;
(defmethod CompInitialize :czc.skaro.impl/EmitMeta

  [^czlab.skaro.impl.dfts.EmitMeta block]

  (let [^czlab.xlib.util.core.Muble co block
        url (.metaUrl block)
        cfg (ReadEdn url)
        info (:info cfg)
        conf (:conf cfg)]
    (test-nonil "Invalid block-meta file, no info section." info)
    (test-nonil "Invalid block-meta file, no conf section." conf)
    (log/info "Initializing EmitMeta: " url)
    (.setf! co :metaInfo info)
    (.setf! co :dftOptions conf)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters.  each block has a meta data file describing
;; its functions and features.
;; This registry loads these meta files and adds them to the registry.
;;
(defmethod CompInitialize :czc.skaro.impl/BlocksRegistry

  [^czlab.xlib.util.core.Muble co]

  (let [^czlab.xlib.util.core.Muble
        ctx (-> ^Context co (.getx))
        ^File bDir (.getf ctx K_BKSDIR)
        fs (ListFiles bDir "meta" false) ]
    (doseq [^File f fs ]
      (let [^czlab.xlib.util.core.Muble
            b (-> (makeBlockMeta (io/as-url f))
                  (SynthesizeComponent {}) ) ]
        (.reg ^ComponentRegistry co b)
        (log/info "Added one block: " (.id ^Identifiable b)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/SystemRegistry

  [^czlab.xlib.util.core.Muble co]

  (log/info "CompInitialize: SystemRegistry: " (.id ^Identifiable co))
  co
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/AppsRegistry

  [^czlab.xlib.util.core.Muble co]

  (log/info "CompInitialize: AppsRegistry: " (.id ^Identifiable co))
  co
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

