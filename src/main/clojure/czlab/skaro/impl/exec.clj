;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "Kenneth Leung" }

  czlab.skaro.impl.exec

  (:require
    [czlab.skaro.impl.dfts :refer [podMeta]]
    [czlab.xlib.process :refer [safeWait]]
    [czlab.xlib.str :refer [strim hgl?]]
    [czlab.xlib.mime :refer [setupCache]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.files
     :refer [basename
             mkdirs
             readUrl
             listFiles]]
    [czlab.xlib.core
     :refer [test-nestr
             srandom<>
             fpath
             trylet!
             try!
             getCwd
             convLong
             muble<>
             juid
             test-nonil]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.dfts]
        [czlab.skaro.jmx.core]
        [czlab.skaro.impl.ext])

  (:import
    [czlab.skaro.loaders AppClassLoader]
    [java.security SecureRandom]
    [java.io File FileFilter]
    [java.util.zip ZipFile]
    [czlab.skaro.runtime
     ExecvisorAPI
     AppManifest
     JMXServer
     EmitterManifest]
    [java.net URL]
    [java.util Date]
    [czlab.xlib
     Disposable
     Startable
     Muble
     Versioned
     Hierarchial
     Identifiable]
    [czlab.skaro.server
     Context
     Component
     Registry]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectApp

  "Make sure the app setup is kosher"
  ^AppManifest
  [^Context execv ^File des]

  (let [app (basename des)]
    (log/info "app dir : %s" des)
    (log/info "inspecting...")
    (precondFile (io/file des CFG_APP_CF)
                 (io/file des CFG_ENV_CF))
    (precondDir (io/file des DN_CONF)
                (io/file des DN_CFG))
    (let [ps (readEdn (io/file des CFG_APP_CF))
          ctx (.getx execv)
          ^Registry
          apps (-> ^Registry
                   (.getv ctx K_COMPS)
                   (.lookup K_APPS))
          info (:info ps)]
      (log/info "checking conf for app: %s\n%s" app info)
      ;; synthesize the pod meta component and register it
      ;; as a application
      (let [^Context
            m (-> (podMeta app
                           info
                           (io/as-url des))
                  (comp->synthesize {:ctx ctx}))]
        (-> (.getx m)
            (.setv K_EXECV execv))
        (.reg apps m)
        m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx

  "Basic JMX support"
  [^Context co cfg]

  (log/info "jmx-config: %s" cfg)
  (trylet!
    [port (or (:port cfg) 7777)
     host (str (:host cfg))
     jmx (jmxServer<> host)]
    (.setRegistryPort jmx (int port))
    (.start ^Startable jmx)
    (.reg jmx co "com.zotohlab" "execvisor" ["root=skaro"])
    (-> (.getx co)
        (.setv K_JMXSVR jmx))
    (log/info "jmx-server listening on: %s:%s" host port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopJmx

  "Kill the internal JMX server"
  [^Context co]

  (trylet!
    [ctx (.getx co)
     jmx (.getv ctx K_JMXSVR)]
    (when (some? jmx)
      (.stop ^Startable jmx))
    (.setv ctx K_JMXSVR nil))
  (log/info "jmx connection terminated"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod

  ""
  [^Muble co ^AppManifest pod]

  (trylet!
    [cid (.id ^Identifiable pod)
     cc (.getv co K_CONTAINERS)
     app (.name pod)
     ctr (mkContainer pod)]
    (log/debug "start pod\ncid = %s\napp = %s" cid app)
    (.setv co K_CONTAINERS (assoc cc cid ctr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods

  ""
  [^Muble co]

  (log/info "preparing to stop pods...")
  (let [cs (.getv co K_CONTAINERS)]
    (doseq [[k v] cs]
      (.stop ^Startable v)
      (.dispose ^Disposable v))
    (.setv co K_CONTAINERS {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>

  "Create a ExecVisor"
  ^ExecvisorAPI
  [parObj]

  (log/info "creating execvisor, parent = %s" parObj)
  (let [impl (muble<> {K_CONTAINERS {}})
        ctxt (muble<>)]
    (with-meta
      (reify

        Versioned

        (version [_] "1.0")

        Hierarchial

        (parent [_] parObj)

        Identifiable

        (id [_] K_EXECV )

        Context

        (getx [_] ctxt)

        MubleParent

        (muble [_] impl)

        ExecvisorAPI

        (getUpTimeInMillis [_] (- (System/currentTimeMillis) START-TIME))
        (getStartTime [_] START-TIME)
        (kill9 [this] ((:stop parObj)))
        (homeDir [this] (maybeDir (.getx this) K_BASEDIR))
        (confDir [this] (maybeDir (.getx this) K_CFGDIR))
        (blocksDir [this] (maybeDir (.getx this) K_BKSDIR))

        Startable

        (start [this]
          (->> (inspectApp this (getCwd))
               (ignitePod this)))

        (stop [this]
          (stopJmx this)
          (stopPods this)) )

       {:typeid ::ExecVisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :czlab.skaro.impl/AppManifest
  [co]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :czlab.skaro.impl/ExecVisor
  [^Context co]

  (let [ctx (.getx co)
        base (.getv ctx K_BASEDIR)
        cf (.getv ctx K_PROPS)
        comps (K_COMPS cf)
        regs (K_REGS cf)
        jmx  (K_JMXMGM cf)]

    (log/info "initializing component: ExecVisor: %s" co)
    (test-nonil "conf file: components" comps)
    (test-nonil "conf file: registries" regs)
    (test-nonil "conf file: jmx mgmt" jmx)

    (->> "app/mime.properties"
         (io/file base DN_CFG)
         (io/as-url)
         (setupCache ))

    (System/setProperty "file.encoding" "utf-8")

    (let [bks (-> ^ExecvisorAPI
                   co
                   (.homeDir)
                   (io/file DN_CFG DN_BLOCKS)) ]
      (precondDir bks)
      (doto ctx
        (.setv K_BKSDIR bks)))

    (let [root (registry<> :SystemRego K_COMPS "1.0" co)
          bks (registry<> :BlocksRego K_BLOCKS "1.0" nil)
          apps (registry<> :AppsRego K_APPS "1.0" nil)
          options {:ctx ctx} ]

      (.setv ctx K_COMPS root)
      (.setv ctx K_EXECV co)

      (doto root
        (.reg apps)
        (.reg bks))

      (comp->synthesize root options)
      (comp->synthesize bks options)
      (comp->synthesize apps options))

    (startJmx co jmx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- blockMeta<>

  ""
  ;; url points to block-meta file
  [blockType data ^URL url]

  (let [ctxt (muble<>)
        {:keys [info conf]}
        data
        impl (muble<> {:metaInfo info
                       :dftOptions conf})]
    (with-meta
      (reify

        Hierarchial

        (parent [_] nil)

        Context

        (getx [_] ctxt)

        MubleParent

        (muble [_] impl)

        Component

        (version [_] (:version info))

        (id [_] blockType)

        EmitterManifest

        (isEnabled [_] (not (false? (:enabled info))))

        (name [_] (:name info))

        (content [_] url) )

      {:typeid ::EmitterManifest})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->configure

  :czlab.skaro.impl/EmitterManifest
  [^Muble co props]

  (when (some? props)
    (doseq [[k v] props]
      (.setv co k v)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters,  each block has a meta data file describing
;; its functions and features
;; This registry loads these meta files and adds them to the registry
(defmethod comp->initialize

  :czlab.skaro.impl/BlocksRego
  [^Context co]

  (log/info "comp->initialize: BlocksRego \"%s\"" (.id ^Identifiable co))
  (let [ctx (.getx co)
        bDir (.getv ctx K_BKSDIR)
        fs (listFiles bDir "edn") ]
    (doseq [f fs]
      (doseq [[k v] (readEdn f)
              :let [b (-> (blockMeta<> k v (io/as-url f))
                          (comp->synthesize {:props v}))]]
        (.reg ^Registry co b)
        (log/info "added one block: %s" (.id ^Identifiable b)) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :czlab.skaro.impl/SystemRego
  [co]

  (log/info "comp->initialize: SystemRego: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :czlab.skaro.impl/AppsRego
  [co]

  (log/info "comp->initialize: AppsRego: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


