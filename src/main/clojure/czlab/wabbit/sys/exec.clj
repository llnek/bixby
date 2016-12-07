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
      :author "Kenneth Leung"}

  czlab.wabbit.sys.exec

  (:require [czlab.convoy.net.mime :refer [setupCache]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.etc.svcs]
        [czlab.wabbit.sys.core]
        [czlab.wabbit.sys.extn]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [czlab.wabbit.io IoService IoGist]
           [java.security SecureRandom]
           [czlab.wabbit.etc Gist]
           [czlab.wabbit.server
            Container
            Execvisor]
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
            Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectPod
  "Make sure the pod setup is ok"
  ^Gist
  [^Execvisor execv desDir]
  (log/info "pod dir : %s => inspecting..." desDir)
  ;;create the pod meta and register it
  ;;as a application
  (let
    [_ (precondFile (io/file desDir CFG_POD_CF))
     cf (slurpXXXConf desDir CFG_POD_CF true)
     pod (basename desDir)
     ctx (.getx execv)
     m (podMeta pod
                cf
                (io/as-url desDir))]
    (comp->init m nil)
    (doto->>
      m
      (.setv ctx :pod ))))

;;(.reg jmx co "czlab" "execvisor" ["root=wabbit"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod
  ""
  [^Execvisor co ^Gist gist]
  (try!
    (let
      [ctr (container<> co gist)
       pod (.id gist)
       cid (.id ctr)]
      (log/debug "start pod = %s\ninstance = %s" pod cid)
      (doto->>
        ctr
        (.setv (.getx co) :container )
        (.start ))))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods
  ""
  [^Execvisor co]
  (log/info "preparing to stop pod...")
  (let [cx (.getx co)
        c (.getv cx :container)]
    (doto->>
      ^Container
      c
      (.stop )
      (.dispose ))
    (.unsetv cx :container)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>
  "Create a Execvisor"
  ^Execvisor
  []
  (let
    [impl (muble<> {:container nil
                    :pod nil
                    :emitters {}})
     pid (str "exec#" (seqint2))]
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
          (->> (.getv impl :pod)
               (ignitePod this )))

        (stop [this]
          (stopPods this)))

       {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- emitMeta
  ""
  ^IoGist
  [emsType gist]
  (let [{:keys [info conf]}
        gist
        pid (str "emit#"
                 (:name info))
        impl (muble<> conf)]
    (with-meta
      (reify

        IoGist

        (setParent [_ p] (.setv impl :execv p))
        (parent [_] (.getv impl :execv))

        (version [_] (:version info))
        (getx [_] impl)
        (type [_] emsType)

        (isEnabled [_]
          (not (false? (:enabled info))))

        (id [_] pid))

      {:typeid  ::IoGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->init
  ::IoGist
  [^IoGist co execv]

  (logcomp "com->init" co)
  (doto co
    (.setParent execv)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoEmitters
  ""
  ^Execvisor
  [^Execvisor co]
  (let [ctx (.getx co)]
    (->>
      (preduce<map>
        #(let [b (emitMeta (first %2)
                           (last %2))]
           (comp->init b co)
           (assoc! %1 (.type b) b))
        *emitter-defs*)
      (.setv ctx :emitters ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoApps
  ""
  ^Execvisor
  [^Execvisor co]
  (->> (.getv (.getx co) :podDir)
       (inspectPod co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::Execvisor
  [^Execvisor co rootGist]
  {:pre [(inst? Atom rootGist)]}

  (let [{:keys [basedir encoding podDir]}
        @rootGist]
    (sysProp! "file.encoding" encoding)
    (logcomp "com->init" co)
    (.copy (.getx co) (muble<> @rootGist))
    (-> (io/file podDir
                 DN_ETC
                 "mime.properties")
        (io/as-url)
        (setupCache ))
    (regoEmitters co)
    (regoApps co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


