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
    [java.security SecureRandom]
    [czlab.skaro.runtime
     ExecvisorAPI
     JMXServer
     EmitterGist]
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

  "Make sure the app setup is ok"
  ^Component
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
                   (.getv ctx :components)
                   (.lookup :apps))
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
            (.setv :execv execv))
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
        (.setv :jmxServer jmx))
    (log/info "jmx-server listening on: %s:%s" host port)
    jmx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopJmx

  "Kill the internal JMX server"
  [^Context co]

  (trylet!
    [ctx (.getx co)
     jmx (.getv ctx :jmxServer)]
    (when (some? jmx)
      (.stop ^Startable jmx))
    (.unsetv ctx :jmxServer))
  (log/info "jmx connection terminated")
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod

  ""
  [^Context co ^Context pod]

  (trylet!
    [cc (.getv (.getx co) :containers)
     cid (.id ^Identifiable pod)
     app (.getv (.getx pod) :name)
     ctr (mkContainer pod)]
    (log/debug "start pod\ncid = %s\napp = %s" cid app)
    (.setv (.getx co) :containers (assoc cc cid ctr)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods

  ""
  [^Context co]

  (log/info "preparing to stop pods...")
  (doseq [[_ v]
          (.getv (.getx co) :containers)]
    (.stop ^Startable v)
    (.dispose ^Disposable v))
  (.setv (.getx co) :containers {})
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>

  "Create a ExecVisor"
  ^ExecvisorAPI
  [parObj]

  (log/info "creating execvisor, parent = %s" parObj)
  (let [impl (muble<> {:containers {}})]
    (with-meta
      (reify

        Versioned

        (version [_] "1.0")

        Hierarchial

        (parent [_] parObj)

        Identifiable

        (id [_] :execvisor)

        Context

        (getx [_] impl)

        ExecvisorAPI

        (uptimeInMillis [_] (- (System/currentTimeMillis) START-TIME))
        (startTime [_] START-TIME)
        (kill9 [this] ((:stop parObj)))
        (homeDir [this] (maybeDir impl :basedir))

        Startable

        (start [this]
          (->> (inspectApp this (getCwd))
               (ignitePod this)))

        (stop [this]
          (stopJmx this)
          (stopPods this)))

       {:typeid ::ExecVisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::ExecVisor
  [^Context co ^Muble arg]

  (let [{:keys [basedir skaroConf]}
        (.impl arg)
        ctx (.getx co)
        {:keys [components
                jmx
                registries]}
        skaroConf]
    (log/info "com->initialize: ExecVisor: %s" co)
    (test-nonil "conf file: components" components)
    (test-nonil "conf file: registries" registries)
    (test-nonil "conf file: jmx" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (->> "app/mime.properties"
         (io/file basedir DN_CFG)
         (io/as-url)
         (setupCache ))

    (.copy ctx arg)

    (let [root (registry<> ::SystemRego :root "1.0" co)
          bks (registry<> ::EmsRego :blocks "1.0" nil)
          apps (registry<> ::AppsRego :apps "1.0" nil)
          options {:ctx ctx} ]

      (doto ctx
        (.setv :components root)
        (.setv :execv co))

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
;; Each emitter has a meta data file describing
;; its functions and features
;; This registry loads these meta files and adds them to the registry
(defmethod comp->initialize

  ::EmsRego
  [^Context co arg]

  (log/info "comp->initialize: EmsRego \"%s\"" (.id ^Identifiable co))
  (doseq [[k v] *emitter-defs*
          :let [b (-> (emsMeta<> k v (io/as-url f))
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


