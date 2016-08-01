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

  czlab.skaro.core.exec

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

  (:use [czlab.skaro.core.dfts]
        [czlab.skaro.core.sys]
        [czlab.skaro.jmx.core]
        [czlab.skaro.core.ext])

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
     Component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectPod

  "Make sure the pod setup is ok"
  ^Component
  [^Component execv ^File des]

  (let [app (basename des)]
    (log/info "app dir : %s" des)
    (log/info "inspecting...")
    (precondFile (io/file des CFG_APP_CF)
                 (io/file des CFG_ENV_CF))
    (precondDir (io/file des DN_CONF)
                (io/file des DN_ETC))
    (let [ps (readEdn (io/file des CFG_APP_CF))
          ctx (.getx execv)]
      (log/info "checking conf for app: %s" app)
      ;; create the pod meta and register it
      ;; as a application
      (let [m (-> (podMeta app
                           (:info ps)
                           (io/as-url des))
                  (comp->initialize  execv))]
        (->> (-> (.getv ctx :apps)
                 (assoc (.id ^Identifiable m) m))
             (.setv ctx :apps))
        m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx

  "Basic JMX support"
  ^JMXServer
  [^Component co cfg]

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
  ^Component
  [^Component co]

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
  ^Component
  [^Component co ^Component gist]

  (trylet!
    [cc (.getv (.getx co) :containers)
     app (.id ^Identifiable gist)
     ctr (mkContainer gist)
     cid (.id ^Identifiable ctr)]
    (log/debug (str "start pod = %s\n
                    instance = %s") app cid)
    (->> (assoc cc cid ctr)
         (.setv (.getx co) :containers)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods

  ""
  ^Component
  [^Component co]

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

        Component

        (id [_] :execvisor)
        (version [_] "1.0")
        (getx [_] impl)

        Hierarchial

        (parent [_] parObj)
        (setParent [_ p])

        ExecvisorAPI

        (uptimeInMillis [_] (- (System/currentTimeMillis) START-TIME))
        (startTime [_] START-TIME)
        (kill9 [_] ((:stop parObj)))
        (homeDir [_] (maybeDir impl :basedir))

        Startable

        (start [this]
          (->> (inspectPod this (getCwd))
               (ignitePod this)))

        (stop [this]
          (stopJmx this)
          (stopPods this)))

       {:typeid ::ExecVisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::ExecVisor
  [^Component co & args]

  (let [{:keys [basedir skaroConf]}
        (.impl (first args))
        ctx (.getx co)
        {:keys [components jmx ]}
        skaroConf]
    (log/info "com->initialize: ExecVisor: %s" co)
    (test-nonil "conf file: components" components)
    (test-nonil "conf file: jmx" jmx)

    (System/setProperty "file.encoding" "utf-8")

    (->> "app/mime.properties"
         (io/file basedir DN_ETC)
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
(defn- emsMeta<>

  ""
  [emsType data]

  (let [{:keys [info conf]}
        data
        impl (muble<> conf)]
    (with-meta
      (reify

        Hierarchial

        (setParent [_ p])
        (parent [_] nil)

        Context

        (getx [_] impl)

        Component

        (version [_] (:version info))

        (id [_] emsType)

        EmitterGist

        (isEnabled [_]
          (not (false? (:enabled info))))

        (name [_] (:name info)))

      {:typeid ::EmitterGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->configure

  ::EmitterGist
  [^Context co props]

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
  (doseq
    [[k v] *emitter-defs*
     :let [b (emsMeta<> k v)]]
    (.reg ^Registry co b)
    (log/info "added emitter: %s" (.id ^Identifiable b)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::SystemRego
  [^Context co arg]

  (log/info "comp->initialize: SystemRego: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :czlab.skaro.core.dfts/AppGist
  [^Component co & args]

  (log/info "comp->initialize: AppGist: \"%s\"" (.id ^Identifiable co))
  (-> (.getx co)
      (.setv :execv (first args)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


