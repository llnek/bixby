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
    [czlab.xlib.util.files :refer [Mkdirs ReadOneUrl]]
    [czlab.xlib.util.files :refer [ListFiles Unzip]]
    [czlab.xlib.util.str :refer [strim hgl? ToKW]]
    [czlab.xlib.util.mime :refer [SetupCache]]
    [czlab.xlib.util.process :refer [SafeWait]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.skaro.impl.dfts :refer [PodMeta*]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.util.core
    :refer [test-nestr FPath
    tryletc tryc NewRandom GetCwd
    ConvLong MubleObj juid test-nonil]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.dfts]
        [czlab.xlib.jmx.core]
        [czlab.skaro.impl.ext])

  (:import
    [org.apache.commons.io.filefilter DirectoryFileFilter]
    [org.apache.commons.io FilenameUtils]
    [com.zotohlab.skaro.runtime ExecvisorAPI
    JMXServer PODMeta EmitMeta]
    [com.zotohlab.skaro.loaders AppClassLoader]
    [com.zotohlab.skaro.core Muble Context]
    [java.io File FileFilter]
    [java.security SecureRandom]
    [java.util.zip ZipFile]
    [java.net URL]
    [java.util Date]
    [com.zotohlab.frwk.core Startable Disposable
    Versioned Hierarchial Identifiable]
    [com.zotohlab.frwk.server Component Registry]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectApp

  "Make sure the app setup is kosher"

  ^PODMeta
  [^Context execv ^File des]

  (let [app (FilenameUtils/getBaseName (FPath des)) ]
    (log/info "app dir : %s" des)
    (log/info "inspecting...")
    (PrecondFile (io/file des CFG_APP_CF)
                 (io/file des CFG_ENV_CF))
    (PrecondDir (io/file des DN_CONF)
                (io/file des DN_CFG))
    (let [ps (ReadEdn (io/file des CFG_APP_CF))
          ^Muble ctx (.getx execv)
          ^Registry
          apps (-> ^Registry
                   (.getv ctx K_COMPS)
                   (.lookup K_APPS))
          info (:info ps) ]

      (log/info "checking conf for app: %s\n%s" app info)

      ;; synthesize the pod meta component and register it
      ;; as a application
      (let [^Context
            m (-> (PodMeta* app
                            info
                            (io/as-url des))
                  (SynthesizeComponent {:ctx ctx})) ]
        (-> ^Muble
            (.getx m)
            (.setv K_EXECV execv))
        (.reg apps m)
        m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx

  "Basic JMX support"

  [^Context co cfg]

  (log/info "jmx-config: %s" cfg)
  (tryletc
    [port (or (:port cfg) 7777)
     host (str (:host cfg))
     jmx (JmxServer* host)  ]
    (.setRegistryPort jmx (int port))
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
     ctr (Container* pod)]
    (log/debug "start pod\ncid = %s\napp = %s" cid app)
    (.setv co K_CONTAINERS (assoc cache cid ctr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods ""

  [^Muble co]

  (log/info "preparing to stop pods...")
  (let [cs (.getv co K_CONTAINERS) ]
    (doseq [[k v] cs]
      (.stop ^Startable v)
      (.dispose ^Disposable v))
    (.setv co K_CONTAINERS {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Execvisor*

  "Create a ExecVisor"

  ^ExecvisorAPI
  [parObj]

  (log/info "creating execvisor, parent = %s" parObj)
  (let [impl (MubleObj {K_CONTAINERS {}})
        ctxt (atom (MubleObj)) ]
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

  [^Context co]

  (let [^Muble ctx (.getx co)
        base (.getv ctx K_BASEDIR)
        cf (.getv ctx K_PROPS)
        comps (K_COMPS cf)
        regs (K_REGS cf)
        jmx  (K_JMXMGM cf) ]

    (log/info "initializing component: ExecVisor: %s" co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (SetupCache (-> (io/file base DN_CFG
                             "app/mime.properties")
                    (io/as-url)))

    (System/setProperty "file.encoding" "utf-8")

    (let [bks (-> ^ExecvisorAPI
                   co
                   (.homeDir)
                   (io/file DN_CFG DN_BLOCKS)) ]
      (PrecondDir bks)
      (doto ctx
        (.setv K_BKSDIR bks)))

    (let [root (ReifyRegistry :SystemRegistry K_COMPS "1.0" co)
          bks (ReifyRegistry :BlocksRegistry K_BLOCKS "1.0" nil)
          apps (ReifyRegistry :AppsRegistry K_APPS "1.0" nil)
          options {:ctx ctx} ]

      (.setv ctx K_COMPS root)
      (.setv ctx K_EXECV co)

      (doto root
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

  (let [ctxt (atom (MubleObj))
        impl (MubleObj) ]

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

        (version [_]
          (-> (.getv impl :metaInfo)
              (:version)))

        (id [_]
          (-> (.getv impl :metaInfo)
              (:blockType)
              (keyword)))

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

  [^EmitMeta co]

  (let [url (.metaUrl co)
        {:keys [info conf]}
        (ReadEdn url) ]
    (test-nonil "Invalid block-meta file, no info section" info)
    (test-nonil "Invalid block-meta file, no conf section" conf)
    (log/info "initializing EmitMeta: %s" url)
    (doto ^Muble co
      (.setv  :dftOptions conf)
      (.setv  :metaInfo info))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters,  each block has a meta data file describing
;; its functions and features
;; This registry loads these meta files and adds them to the registry
(defmethod CompInitialize :czc.skaro.impl/BlocksRegistry

  [^Context co]

  (log/info "compInitialize: BlocksRegistry: \"%s\"" (.id ^Identifiable co))
  (let [^Muble ctx (.getx co)
        bDir (.getv ctx K_BKSDIR)
        fs (ListFiles bDir "edn") ]
    (doseq [^File f fs
           :let [^Muble
                 b (-> (makeBlockMeta (io/as-url f))
                       (SynthesizeComponent {})) ]]
      (.reg ^Registry co b)
      (log/info "added one block: %s" (.id ^Identifiable b)) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/SystemRegistry

  [co]

  (log/info "compInitialize: SystemRegistry: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.impl/AppsRegistry

  [co]

  (log/info "compInitialize: AppsRegistry: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

