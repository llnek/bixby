;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.sys.exec

  (:require [czlab.convoy.net.mime :refer [setupCache]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.common.core]
        [czlab.wabbit.common.svcs]
        [czlab.wabbit.etc.core]
        [czlab.wabbit.sys.cont]
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
            Muble
            Versioned
            Hierarchial
            Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private start-time (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- podMeta
  "Create metadata for an application bundle"
  ^Gist
  [^String pod conf urlToPod]
  {:pre [(map? conf)]}
  (let [impl (muble<>
               (merge {:version "?" :main ""}
                      (:info conf)
                      {:name pod :path urlToPod}))
        pid (format "%s#%d" pod (seqint2))]
    (log/info "pod-meta:\n%s" (.intern impl))
    (with-meta
      (reify
        Gist
        (setParent [_ p] (.setv impl :parent p))
        (parent [_] (.getv impl :parent))
        (version [_] (.getv impl :version))
        (id [_] pid)
        (getx [_] impl))
      {:typeid  ::PodGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectPod
  "Make sure the pod setup is ok"
  ^Gist
  [^Execvisor execv desDir]
  (log/info "pod dir : %s => inspecting..." desDir)
  ;;create the meta and register it
  ;;as a pod
  (let
    [pod (basename desDir)
     {:keys [env]}
     (.intern (.getx execv))]
    (doto
      (podMeta pod
               env
               (io/as-url desDir))
      (comp->init execv))))

;;(.reg jmx co "czlab" "execvisor" ["root=wabbit"])
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::PodGist
  [^Gist co ^Execvisor ec]
  (logcomp "com->init" co)
  (doto (.getx ec)
    (.setv :pod co))
  (doto co
    (.setParent ec)))

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
      (log/debug "start pod = %s\ncontainer = %s" pod cid)
      (.setv (.getx co) :container ctr)
      (.start  ctr nil)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods
  ""
  [^Execvisor co]
  (log/info "preparing to stop container...")
  (let [cx (.getx co)
        c (.getv cx :container)]
    (when (some? c)
      (doto->>
        ^Container
        c
        (.stop )
        (.dispose ))
      (.unsetv cx :container))
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
                    :services {}})
     pid (str "exec#" (seqint2))]
    (with-meta
      (reify

        Execvisor

        (uptimeInMillis [_]
          (- (System/currentTimeMillis) start-time))
        (id [_] pid)
        (homeDir [_] (.getv impl :basedir))
        (locale [_] (.getv impl :locale))
        (version [_] (.getv impl :version))
        (getx [_] impl)
        (startTime [_] start-time)
        (kill9 [_] (apply (.getv impl :stop!) []))

        (restart [this _] this)

        (start [this _]
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
        pid (format "emit#%d{%s}"
                    (seqint2) (:name info))
        impl (muble<> conf)]
    (with-meta
      (reify

        IoGist

        (setParent [_ p] (.setv impl :parent p))
        (parent [_] (.getv impl :parent))

        (isEnabled [_]
          (not (false? (.getv impl :enabled?))))

        (version [_] (:version info))
        (getx [_] impl)
        (type [_] emsType)

        (id [_] pid))

      {:typeid  ::IoGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->init
  ::IoGist
  [^IoGist co ec]
  (logcomp "com->init" co)
  (doto co (.setParent ec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoEmitters
  ""
  [^Execvisor co]
  (let [ctx (.getx co)
        env (.getv ctx :env)
        defs (merge (emitterServices)
                    (:emitters env))]
    ;;add user defined emitters and register all
    (->>
      (preduce<map>
        #(let [b (emitMeta (first %2)
                           (last %2))]
           (comp->init b co)
           (assoc! %1 (.type b) b))
        defs)
      (.setv ctx :services ))))

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
    (.copyEx (.getx co) @rootGist)
    (-> (io/file podDir
                 dn-etc
                 "mime.properties")
        (io/as-url)
        (setupCache ))
    (log/info "loaded mime#cache - ok")
    (regoEmitters co)
    (regoApps co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


