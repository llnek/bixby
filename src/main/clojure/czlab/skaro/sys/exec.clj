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

  czlab.skaro.sys.exec

  (:require
    [czlab.xlib.mime :refer [setupCache]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.str :refer [strim hgl?]]
    [czlab.xlib.meta :refer [getCldr]]
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
             convLong
             sysProp!
             inst?
             fpath
             trylet!
             try!
             getCwd
             muble<>
             juid
             test-nonil]])

  (:use [czlab.skaro.sys.dfts]
        [czlab.skaro.etc.svcs]
        [czlab.skaro.sys.core]
        [czlab.skaro.sys.ext]
        [czlab.skaro.jmx.core])

  (:import
    [java.security SecureRandom]
    [clojure.lang Atom]
    [java.util Date]
    [java.io File]
    [java.net URL]
    [czlab.xlib
     Disposable
     Startable
     Muble
     Versioned
     Hierarchial
     Identifiable]
    [czlab.skaro.server
     ServiceGist
     Container
     Execvisor
     AppGist
     JmxServer
     Service
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
  ^AppGist
  [^Execvisor execv ^File des]

  (log/info "app dir : %s => inspecting..." des)
  ;;create the pod meta and register it
  ;;as a application
  (let
    [conf (io/file des CFG_APP_CF)
     dummy (precondFile conf)
     app (basename des)
     cf (readEdn conf)
     ctx (.getx execv)
     m (-> (podMeta app
                    cf
                    (io/as-url des)))]
    (comp->initialize m)
    (.setv ctx :app m)
    m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx

  "Basic JMX support"
  ^JmxServer
  [^Execvisor co cfg]

  (trylet!
    [jmx (jmxServer<> cfg)]
    (.start jmx)
    (.reg jmx co "czlab" "execvisor" ["root=skaro"])
    (-> (.getx co)
        (.setv :jmxServer jmx))
    jmx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopJmx

  "Kill the internal JMX server"
  ^Component
  [^Execvisor co]

  (trylet!
    [ctx (.getx co)
     jmx (.getv ctx :jmxServer)]
    (when (some? jmx)
      (.stop ^JmxServer jmx))
    (.unsetv ctx :jmxServer))
  (log/info "jmx terminated")
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod

  ""
  ^Execvisor
  [^Execvisor co ^AppGist gist]

  (trylet!
    [ctr (container<> co gist)
     app (.id gist)
     cid (.id ctr)]
    (log/debug "start pod = %s\ninstance = %s" app cid)
    (.setv (.getx co) :container ctr)
    (.start ctr))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods

  ""
  ^Execvisor
  [^Execvisor co]

  (log/info "preparing to stop pods...")
  (let [^Container
        c (.getv (.getx co) :container)]
    (.stop c)
    (.dispose c)
    (.unsetv (.getx co) :container)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>

  "Create a Execvisor"
  ^Execvisor
  []

  (let
    [impl (muble<> {:container nil
                    :app nil
                    :emitters {}})
     pid (juid)]
    (with-meta
      (reify

        Execvisor

        (uptimeInMillis [_]
          (- (System/currentTimeMillis) START-TIME))
        (id [_] (format "%s{%s}" "execvisor" pid))
        (homeDir [_] (.getv impl :basedir))
        (locale [_] (.getv impl :locale))
        (version [_] "1.0")
        (getx [_] impl)
        (startTime [_] START-TIME)
        (kill9 [_] (apply (.getv impl :stop!) []))

        (start [this]
          (->> (.getv impl :app)
               (ignitePod this )))

        (stop [this]
          (stopJmx this)
          (stopPods this)))

       {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- emitMeta

  ""
  ^ServiceGist
  [emsType gist]

  (let [{:keys [info conf]}
        gist
        pid (format "%s[%s]"
                    (juid)
                    (:name info))
        impl (muble<> conf)]
    (with-meta
      (reify

        ServiceGist

        (version [_] (:version info))
        (getx [_] impl)
        (type [_] emsType)

        (setParent [_ p] (.setv impl :execv p))
        (parent [_] (.getv impl :execv))

        (isEnabled [_]
          (not (false? (:enabled info))))

        (id [_] pid))

      {:typeid  ::ServiceGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->initialize

  ::ServiceGist
  [^ServiceGist co & [execv]]

  (log/info "comp->initialize: ServiceGist: %s" (.id co))
  (.setParent co execv)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoEmitters

  ""
  ^Execvisor
  [^Execvisor co]

  (let [ctx (.getx co)]
    (->>
      (persistent!
        (reduce
          #(let [b (emitMeta (first %2)
                             (last %2))]
             (comp->initialize b co)
             (assoc! %1 (.type b) b))
          (transient {})
          *emitter-defs*))
      (.setv ctx :emitters ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoApps

  ""
  ^Execvisor
  [^Execvisor co]

  (->> (.getv (.getx co) :appDir)
       (inspectPod co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Execvisor
  [^Execvisor co & [rootGist]]
  {:pre [(inst? Atom rootGist)]}

  (let [{:keys [basedir appDir jmx]}
        @rootGist]
    (log/info "com->initialize: Execvisor: %s" co)
    (test-nonil "conf file: jmx" jmx)
    (sysProp! "file.encoding" "utf-8")
    (.copy (.getx co) (muble<> @rootGist))
    (->> (io/file appDir
                  DN_ETC
                  "mime.properties")
         (io/as-url)
         (setupCache ))
    (regoEmitters co)
    (regoApps co)
    (startJmx co jmx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


