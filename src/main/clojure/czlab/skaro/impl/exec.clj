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
      :author "kenl" }

  czlab.skaro.impl.exec

  (:require
    [czlab.xlib.files
     :refer [mkdirs
             readOneUrl
             listFiles]]
    [czlab.xlib.str :refer [strim hgl? toKW]]
    [czlab.xlib.mime :refer [setupCache]]
    [czlab.xlib.process :refer [safeWait]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.skaro.impl.dfts :refer [podMeta]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.core
     :refer [test-nestr fpath
             tryletc
             tryc
             newRandom
             getCwd
             convLong
             mubleObj! juid test-nonil]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.dfts]
        [czlab.skaro.jmx.core]
        [czlab.skaro.impl.ext])

  (:import
    [org.apache.commons.io.filefilter DirectoryFileFilter]
    [org.apache.commons.io FilenameUtils]
    [czlab.skaro.runtime ExecvisorAPI
     JMXServer
     PODMeta
     EmitMeta]
    [czlab.skaro.loaders AppClassLoader]
    [java.io File FileFilter]
    [java.security SecureRandom]
    [java.util.zip ZipFile]
    [java.net URL]
    [java.util Date]
    [czlab.xlib Muble
     Startable
     Disposable
     Versioned
     Hierarchial Identifiable]
    [czlab.skaro.server Context
     Component Registry]))

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

  (let [app (FilenameUtils/getBaseName (fpath des)) ]
    (log/info "app dir : %s" des)
    (log/info "inspecting...")
    (precondFile (io/file des CFG_APP_CF)
                 (io/file des CFG_ENV_CF))
    (precondDir (io/file des DN_CONF)
                (io/file des DN_CFG))
    (let [ps (readEdn (io/file des CFG_APP_CF))
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
            m (-> (podMeta app
                           info
                           (io/as-url des))
                  (synthesizeComponent {:ctx ctx})) ]
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
     jmx (mkJmxServer host)]
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
     ctr (mkContainer pod)]
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
(defn execvisor

  "Create a ExecVisor"

  ^ExecvisorAPI
  [parObj]

  (log/info "creating execvisor, parent = %s" parObj)
  (let [impl (mubleObj! {K_CONTAINERS {}})
        ctxt (atom (mubleObj!)) ]
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
        (homeDir [this] (maybeDir (.getx this) K_BASEDIR))
        (confDir [this] (maybeDir (.getx this) K_CFGDIR))
        (blocksDir [this] (maybeDir (.getx this) K_BKSDIR))
        (kill9 [this] (.stop ^Startable parObj))

        Startable

        (start [this]
          (->> (inspectApp this (getCwd))
               (ignitePod this)))

        (stop [this]
          (stopJmx this)
          (stopPods this)) )

       {:typeid (toKW "czc.skaro.impl" "ExecVisor") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.impl/PODMeta

  [^Muble co]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.impl/ExecVisor

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

    (setupCache (-> (io/file base DN_CFG
                             "app/mime.properties")
                    (io/as-url)))

    (System/setProperty "file.encoding" "utf-8")

    (let [bks (-> ^ExecvisorAPI
                   co
                   (.homeDir)
                   (io/file DN_CFG DN_BLOCKS)) ]
      (precondDir bks)
      (doto ctx
        (.setv K_BKSDIR bks)))

    (let [root (reifyRegistry :SystemRegistry K_COMPS "1.0" co)
          bks (reifyRegistry :BlocksRegistry K_BLOCKS "1.0" nil)
          apps (reifyRegistry :AppsRegistry K_APPS "1.0" nil)
          options {:ctx ctx} ]

      (.setv ctx K_COMPS root)
      (.setv ctx K_EXECV co)

      (doto root
        (.reg apps)
        (.reg bks))

      (synthesizeComponent root options)
      (synthesizeComponent bks options)
      (synthesizeComponent apps options))

    (startJmx co jmx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeBlockMeta ""

  ;; url points to block-meta file
  [blockType data ^URL url]

  (let [ctxt (atom (mubleObj!))
        {:keys [info conf]}
        data
        impl (mubleObj! {:metaInfo info
                         :dftOptions conf})]
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

        (version [_] (:version info))

        (id [_] blockType)

        EmitMeta

        (isEnabled [_] (not (false? (:enabled info))))

        (getName [_] (:name info))

        (metaUrl [_] url) )

      {:typeid (toKW "czc.skaro.impl" "EmitMeta") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod compConfigure :czc.skaro.impl/EmitMeta

  [^Muble co props]

  (when (some? props)
    (doseq [[k v] props]
      (.setv co k v)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Blocks are emitters,  each block has a meta data file describing
;; its functions and features
;; This registry loads these meta files and adds them to the registry
(defmethod compInitialize :czc.skaro.impl/BlocksRegistry

  [^Context co]

  (log/info "compInitialize: BlocksRegistry: \"%s\"" (.id ^Identifiable co))
  (let [^Muble ctx (.getx co)
        bDir (.getv ctx K_BKSDIR)
        fs (listFiles bDir "edn") ]
    (doseq [^File f fs]
      (doseq [[k v] (readEdn f)
              :let [^Muble
                    b (-> (makeBlockMeta k v (io/as-url f))
                          (synthesizeComponent {:props v})) ]]
        (.reg ^Registry co b)
        (log/info "added one block: %s" (.id ^Identifiable b)) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.impl/SystemRegistry

  [co]

  (log/info "compInitialize: SystemRegistry: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.impl/AppsRegistry

  [co]

  (log/info "compInitialize: AppsRegistry: \"%s\"" (.id ^Identifiable co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


