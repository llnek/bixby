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
    [czlab.skaro.io IoTrigger IoService IoEvent]
    [czlab.wflow WorkStream Job TaskDef]
    [java.util Timer TimerTask]
    [czlab.skaro.server
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

  :czlab.skaro.io.core/Service
  [^IoService co args]

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
  [^IoService co]
  (log/info "service '%s' config:" (.id co))
  (log/info "%s" (pr-str (.config co)))
  (log/info "service '%s' started - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn io<stopped>
  ""
  [^IoService co]
  (log/info "service '%s' stopped - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->dispose

  :czlab.skaro.io.core/Service
  [^IoService co]

  (log/info "service '%s' disposed - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->error!

  :czlab.skaro.io.core/Service
  [^IoService co ^Job job ^Throwable e]

  (log/exception e)
  (some-> (fatalErrorFlow<> job)
          (.execWith job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private job<+>

  ""
  [co evt]

  `(vary-meta (job<> ~co nil ~evt)
              assoc :typeid :czlab.skaro.io.core/Job))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent

  ""
  [^IoService src evt args]

  (log/debug "service '%s' onevent called" (.id src))
  (let
    [c1 (:router args)
     ctr (.server src)
     cfg (.config src)
     rts (.cljrt ctr)
     c0 (:handler cfg)
     cb (stror c1 c0)
     job (job<+> ctr evt)
     wf (try! (.call rts cb))]
    (log/debug (str "event type = %s\n"
                    "event opts = %s\n"
                    "event router = %s\n"
                    "io-handler = %s")
               (type evt) args c1 c0)
    (try
      (if-not (inst? WorkStream wf)
        (throwBadArg "Want WorkStream, got %s" (class wf))
        (log/debug "job#%s => %s" (.id job) (.id src)))
      (.setv job EV_OPTS args)
      (.execWith ^WorkStream wf job)
      (catch Throwable e#
        (io->error! src job e#)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn service<>

  "Create a IO/Service"
  ^IoService
  [^Container parObj emType emAlias]
  {:pre [(keyword? emType)]}

  (let [timer (atom nil)
        impl (muble<>)]
    (with-meta
      (reify IoService

        (isEnabled [_]
          (not (false? (.getv impl :enabled))))

        (isActive [_]
          (not (false? (.getv impl :active))))

        (config [_] (.getv impl :emcfg))
        (server [this] (.parent this))

        (dispatch [this ev]
          (.dispatchEx this ev nil))

        (dispatchEx [this ev arg]
          (try! (onEvent this ev arg)))

        (hold [_ trig millis]
          (if (and (some? @timer)
                   (spos? millis))
            (let [t (tmtask<>
                      #(.fire trig nil))]
              (.schedule ^Timer @timer t millis)
              (.setTrigger trig t))))

        (version [_] "1.0")
        (getx [_] impl)
        (id [_] emAlias)

        (parent [_] parObj)
        (setParent [_ p]
          (throwUOE "can't set service parent"))

        (dispose [this]
          (some-> ^Timer @timer (.cancel))
          (reset! timer nil)
          (io->dispose this))

        (init [this cfg]
          (comp->init this cfg))

        (start [this]
          (reset! timer (Timer. true))
          (io->start this))

        (stop [this]
          (some-> ^Timer @timer (.cancel ))
          (reset! timer nil)
          (io->stop this)))

      {:typeid emType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


