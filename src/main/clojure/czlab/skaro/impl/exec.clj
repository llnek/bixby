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

  (:require
    [czlab.xlib.util.str :refer [strim hgl? ToKW]]
    [czlab.xlib.util.mime :refer [SetupCache]]
    [czlab.xlib.util.files :refer [ListFiles Unzip]]
    [czlab.xlib.util.process :refer [SafeWait]]
    [czlab.skaro.impl.dfts :refer [MakePodMeta]]
    [czlab.xlib.util.core
    :refer [LoadJavaProps test-nestr FPath tryletc tryc
    NewRandom GetCwd
    ConvLong MakeMMap juid test-nonil]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.xlib.util.files
    :refer [Mkdirs ReadOneUrl]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.dfts]
        [czlab.xlib.jmx.core]
        [czlab.skaro.impl.ext])

  (:import
    [com.zotohlab.skaro.runtime ExecvisorAPI PODMeta EmitMeta]
    [org.apache.commons.io.filefilter DirectoryFileFilter]
    [org.apache.commons.io FilenameUtils FileUtils]
    [com.zotohlab.skaro.loaders AppClassLoader]
    [com.zotohlab.skaro.core Muble Context]
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
(defn- chkManifest

  "Check the app's manifest file"

  ^PODMeta
  [^Context execv app ^File des]

  (let [ps (ReadEdn (io/file des CFG_APP_CF))
        ^Muble ctx (.getx execv)
        ^ComponentRegistry
        apps (-> ^ComponentRegistry
                 (.getv ctx K_COMPS)
                 (.lookup K_APPS))
        ver (get-in ps [:info :version])
        vid (get-in ps [:info :vendor])
        cz (get-in ps [:info :main]) ]

    (log/info (str "checking manifest for app: "
                   "%s\nversion: %s\nmain-class: %s")
              app ver cz)

    ;; synthesize the pod meta component and register it
    ;; as a application
    (let [^Context
          m (-> (MakePodMeta app ver
                             cz vid (io/as-url des))
                (SynthesizeComponent {:ctx ctx})) ]
      (-> ^Muble
          (.getx m)
          (.setv K_EXECV execv))
      (.reg apps m)
      m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- inspectApp

  "Make sure the app setup is kosher"

  ^PODMeta
  [execv ^File des]

  (let [app (FilenameUtils/getBaseName (FPath des)) ]
    (log/info "app dir : %s" des)
    (log/info "inspecting...")
    (tryc
      (PrecondFile (io/file des CFG_APP_CF))
      (PrecondFile (io/file des CFG_ENV_CF))
      (PrecondDir (io/file des DN_CONF))
      (PrecondDir (io/file des DN_CFG))
      (chkManifest execv app des) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx

  "Basic JMX support"

  [^Context co cfg]

  (log/info "jmx-config: %s" cfg)
  (tryletc
    [port (or (:port cfg) 7777)
     host (str (:host cfg))
     jmx (MakeJmxServer host)  ]
    (.setRegistryPort jmx port)
    (-> ^Startable jmx (.start))
    (.reg jmx co "com.zotohlab" "execvisor" ["root=skaro"])
    (-> ^Muble (.getx co)
        (.setv K_JMXSVR jmx))
    (log/info "jmx-server listening on: %s:%s" host port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopJmx

  "Kill the internal JMX server"

  [^Context co]

  (tryletc
    [^Muble ctx (.getx co)
     ^Startable
     jmx (.getv ctx K_JMXSVR) ]
    (when (some? jmx)
      (.stop jmx))
    (.setv ctx K_JMXSVR nil))
  (log/info "jmx connection terminated"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod ""

  [^Muble co ^PODMeta pod]

  (tryletc
    [cache (.getv co K_CONTAINERS)
     cid (.id ^Identifiable pod)
     app (.moniker pod)
     ctr (MakeContainer pod)]
    (log/debug "start pod cid = %s, app = %s" cid app)
    (.setv co K_CONTAINERS (assoc cache cid ctr))
    true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods ""

  [^Muble co]

  (log/info "preparing to stop pods...")
  (let [cs (.getv co K_CONTAINERS) ]
    (doseq [[k v] cs]
      (.stop ^Startable v))
    (doseq [[k v] cs]
      (.dispose ^Disposable v))
    (.setv co K_CONTAINERS {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeExecvisor

  "Create a ExecVisor"

  ^ExecvisorAPI
  [parObj]

  (log/info "creating execvisor, parent = %s" parObj)
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

        (setv [_ a v] (.setv impl a v) )
        (unsetv [_ a] (.unsetv impl a) )
        (getv [_ a] (.getv impl a) )
        (seq [_] )
        (clear [_] (.clear impl))
        (toEDN [_ ] (.toEDN impl))

        ExecvisorAPI

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

       {:typeid (ToKW "czc.skaro.impl" "ExecVisor") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/PODMeta

  [^Muble co]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/ExecVisor

  [^Muble co]

  (let [^Muble ctx (-> ^Context co (.getx))
        base (.getv ctx K_BASEDIR)
        cf (.getv ctx K_PROPS)
        comps (K_COMPS cf)
        regs (K_REGS cf)
        jmx  (K_JMXMGM cf) ]

    (SetupCache (-> (io/file base DN_CFG
                             "app/mime.properties")
                    (io/as-url)))

    (log/info "initializing component: ExecVisor: %s" co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (let [bks (-> ^ExecvisorAPI
                   co
                   (.homeDir)
                   (io/file DN_CFG DN_BLOCKS)) ]
      (PrecondDir bks)
      (doto ctx
        (.setv K_BKSDIR bks)))

    (let [root (MakeRegistry :SystemRegistry K_COMPS "1.0" co)
          bks (MakeRegistry :BlocksRegistry K_BLOCKS "1.0" nil)
          apps (MakeRegistry :AppsRegistry K_APPS "1.0" nil)
          options {:ctx ctx} ]

      (.setv ctx K_COMPS root)
      (.setv ctx K_EXECV co)

      (doto ^ComponentRegistry root
        (.reg apps)
        (.reg bks))

      (SynthesizeComponent root options)
      (SynthesizeComponent bks options)
      (SynthesizeComponent apps options))

    (startJmx co jmx)))

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

        (setv [_ a v] (.setv impl a v) )
        (unsetv [_ a] (.unsetv impl a) )
        (getv [_ a] (.getv impl a) )
        (seq [_] )
        (clear [_] (.clear impl))
        (toEDN [_ ] (.toEDN impl))

        Component

        (id [_] (-> (.getv impl :metaInfo)
                    (:blockType)
                    (keyword)))
        (version [_] (-> (.getv impl :metaInfo)
                         (:version)))

        EmitMeta

        (isEnabled [_] (not (false? (-> (.getv impl :metaInfo)
                                        (:enabled)))))
        (getName [_] (-> (.getv impl :metaInfo)
                         (:name)))
        (metaUrl [_] url) )

      {:typeid (ToKW "czc.skaro.impl" "EmitMeta") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod CompInitialize :czc.skaro.impl/EmitMeta

  [^EmitMeta block]

  (let [^Muble co block
        url (.metaUrl block)
        cfg (ReadEdn url)
        info (:info cfg)
        conf (:conf cfg)]
    (test-nonil "Invalid block-meta file, no info section" info)
    (test-nonil "Invalid block-meta file, no conf section" conf)
    (log/info "initializing EmitMeta: %s" url)
    (.setv co :dftOptions conf)
    (.setv co :metaInfo info)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters,  each block has a meta data file describing
;; its functions and features
;; This registry loads these meta files and adds them to the registry
(defmethod CompInitialize :czc.skaro.impl/BlocksRegistry

  [^Muble co]

  (let [^Muble ctx (-> ^Context co (.getx))
        bDir (.getv ctx K_BKSDIR)
        fs (ListFiles bDir "edn" false) ]
    (doseq [^File f fs
           :let [^Muble
                 b (-> (makeBlockMeta (io/as-url f))
                       (SynthesizeComponent {})) ]]
      (.reg ^ComponentRegistry co b)
      (log/info "added one block: %s" (.id ^Identifiable b)) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/SystemRegistry

  [co]

  (log/info "compInitialize: SystemRegistry: %s" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/AppsRegistry

  [co]

  (log/info "compInitialize: AppsRegistry: %s" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

