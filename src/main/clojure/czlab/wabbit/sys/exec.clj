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
            [czlab.basal.format :refer [readEdn]]
            [czlab.basal.meta :refer [getCldr]]
            [czlab.basal.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.shared.svcs]
        [czlab.wabbit.base.core]
        [czlab.wabbit.sys.runc]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str])

  (:import [czlab.wabbit.server Container Execvisor]
           [java.security SecureRandom]
           [czlab.wabbit.ctrl Service]
           [czlab.wabbit.base Gist]
           [clojure.lang Atom]
           [java.util Date]
           [java.io File]
           [java.net URL]
           [czlab.jasal Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private start-time (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pmeta<>
  "Create metadata for an application bundle"
  ^Gist
  [^Execvisor exe pod conf urlToPod]
  {:pre [(map? conf)]}
  (let
    [impl (muble<>
            (merge {:version "?" :main ""}
                   (:info conf)
                   {:name pod :path urlToPod}))
     pid (format "%s#%d" pod (seqint2))
     g (with-meta
         (reify
           Gist
           (version [_] (.getv impl :version))
           (parent [_] exe)
           (id [_] pid)
           (getx [_] impl))
         {:typeid  ::PodGist})]
    (log/info "pod-meta:\n%s" (.intern impl))
    (.setv (.getx exe) :pod g)
    g))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectPod
  "Make sure the pod setup is ok"
  ^Gist
  [^Execvisor exe desDir]
  (log/info "pod dir: %s => inspecting..." desDir)
  (->>
    (pmeta<> exe
             (basename desDir)
             (.getv (.getx exe) :env)
             (io/as-url desDir))))

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
      (log/debug "start app= %s\npod= %s" pod cid)
      (.setv (.getx co) :container ctr)
      (.start ctr nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods
  ""
  [^Execvisor co]
  (let [ctx (.getx co)]
    (log/info "stopping pod...")
    (when-some
      [c (.getv ctx :container)]
      (doto->>
        ^Container
        c
        (.stop )
        (.dispose ))
      (.unsetv ctx :container))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoPlugs
  "Add user defined pluggables and register all"
  [^Execvisor co]
  (let [ctx (.getx co)
        env (.getv ctx :env)]
    (->>
      (merge (emitterServices)
             (:plugs env))
      (.setv ctx :plugs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoApps
  ""
  [^Execvisor co]
  (inspectPod co
              (.getv (.getx co) :podDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>
  "Create an Execvisor"
  ^Execvisor
  []
  (let
    [impl (muble<> {:container nil
                    :pod nil
                    :plugs {}})
     pid (str "exec#" (seqint2))]
    (with-meta
      (reify Execvisor

        (version [_] (.getv impl :version))
        (id [_] pid)
        (getx [_] impl)

        (uptimeInMillis [_] (- (now<>) start-time))
        (kill9 [_] (apply (.getv impl :stop!) []))
        (homeDir [_] (.getv impl :podDir))
        (locale [_] (.getv impl :locale))
        (startTime [_] start-time)

        (init [this arg]
          (let [{:keys [encoding podDir]} @arg]
            (sysProp! "file.encoding" encoding)
            (logcomp "comp->init" this)
            (.copyEx impl @arg)
            (-> (io/file podDir
                         dn-etc "mime.properties")
                (io/as-url)
                (setupCache ))
            (log/info "loaded mime#cache - ok")
            (regoPlus this)
            (regoApps this)))
        (restart [_ _] )
        (stop [this] (stopPods this))
        (start [this _] (ignitePod this
                                   (.getv impl :pod))))
      {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


