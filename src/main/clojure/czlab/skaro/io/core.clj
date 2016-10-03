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

(ns ^{:doc "Core functions for all IO services."
      :author "Kenneth Leung" }

  czlab.skaro.io.core

  (:require
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.logging :as log])

  (:use [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wflow.core]
        [czlab.skaro.sys.core]
        [czlab.skaro.sys.dfts])

  (:import
    [czlab.wflow WorkStream Job TaskDef]
    [java.util Timer TimerTask]
    [czlab.skaro.io IoEvent]
    [czlab.skaro.server
     EventTrigger
     Service
     Cljshim
     Component
     Container]
    [czlab.xlib
     Context
     XData
     Versioned
     Hierarchial
     Muble
     Identifiable
     Disposable
     Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro s2ms

  "Convert seconds to milliseconds"
  {:no-doc true}
  [s]

  `(let [t# ~s] (if (spos?  t#) (* 1000 t#) 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->dispose
  "Dispose a io-service" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->start
  "Start a io-service" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->stop
  "Stop a io-service" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->error!
  "Handle io-service error" (fn [a b c] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioevent<>
  "Create an event" (fn [a args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init

  :default
  [^Service co args]

  (logcomp "comp->init" co)
  (if (and (not-empty args)
           (map? args))
    (->> (merge (.config co) args)
         (.setv (.getx co) :emcfg )))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn io<started>
  ""
  [^Service co]
  (log/info "service %s config:\n%s\nstarted - ok"
            (.id co)
            (pr-str (.config co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn io<stopped>
  ""
  [^Service co]
  (log/info "service %s stopped - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->dispose

  :default
  [^Service co]

  (log/info "service %s disposed - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->error!

  :default
  [^Service co ^Job job ^Throwable e]

  (log/exception e)
  (some-> (fatalErrorFlow<> job)
          (.execWith job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro job<+>

  ""
  {:private true
   :tag Job}
  [co wf evt]

  `(with-meta (job<> ~co ~wf ~evt) {:typeid :czlab.skaro.io/Job}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent

  ""
  [^Container ctr ^Service src evt args]

  (log/debug "service '%s' onevent called" (.id src))
  (let
    [^Job job (job<+> ctr nil evt)
     c1 (str (:router args))
     cfg (.config src)
     rts (.cljrt ctr)
     c0 (str (:handler cfg))
     cb (stror c1 c0)
     wf (try! (.call rts cb))]
    (log/debug "event type = %s" (type evt))
    (log/debug "event opts = %s" args)
    (log/debug "event router = %s" c1)
    (log/debug "io-handler = %s" c0)
    (try
      (if-not (inst? WorkStream wf)
        (throwBadArg "Want WorkStream, got " (class wf)))
      (log/debug "job#%s => %s" (.id job) (.id src))
      (.setv job EV_OPTS args)
      (.execWith ^WorkStream wf job)
      (catch Throwable e#
        (io->error! src job e#)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn service<>

  "Create a Service"
  ^Service
  [^Container parObj emType emAlias]
  {:pre [(keyword? emType)]}

  ;; holds all the events from this source
  (let [timer (atom nil)
        impl (muble<>)]
    (with-meta
      (reify Service

        (isEnabled [_]
          (not (false? (.getv impl :enabled))))

        (isActive [_]
          (not (false? (.getv impl :active))))

        (config [_] (.getv impl :emcfg))
        (server [this] (.parent this))

        (dispatch [this ev]
          (.dispatchEx this ev nil))

        (dispatchEx [this ev arg]
          (try!!
            nil
            (onEvent parObj this ev arg)))

        (hold [_ wevt millis]
          (when (and (some? @timer)
                     (some? wevt))
            (let [t (tmtask<>
                      #(.resumeOnExpiry wevt))]
              (log/debug "holding event: %s"
                         (.id ^Identifiable wevt))
              (.schedule ^Timer @timer t (long millis))
              (.setv (.getx ^Context wevt) :ttask t))))

        (version [_] "1.0")
        (getx [_] impl)
        (id [_] emAlias)

        (parent [_] parObj)
        (setParent [_ p])

        (dispose [this]
          (some-> ^Timer @timer (.cancel))
          (reset! timer nil)
          (io->dispose this))

        (init [this cfg]
          (comp->init this cfg))

        (start [this]
          (io->start this)
          (reset! timer (Timer. true)))

        (stop [this]
          (some-> ^Timer @timer (.cancel ))
          (reset! timer nil)
          (io->stop this)))

      {:typeid emType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


