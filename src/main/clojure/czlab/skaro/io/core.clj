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
    [czlab.xlib.str :refer [stror strim]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [throwBadArg
             tmtask<>
             seqint2
             juid
             inst?
             spos?
             cast?
             try!!
             throwIOE
             muble<>
             convToJava]])

  (:use [czlab.xlib.consts]
        [czlab.wflow.core]
        [czlab.skaro.sys.core]
        [czlab.skaro.sys.dfts])

  (:import
    [java.util.concurrent ConcurrentHashMap]
    [czlab.wflow WorkStream Job TaskDef]
    [java.util Timer TimerTask]
    [czlab.skaro.io IoEvent]
    [czlab.skaro.server
     EventTrigger
     Service
     EventHolder
     Cljshim
     Component
     Container]
    [czlab.xlib
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
(defn meta??? "" {:no-doc true} [a & _] (:typeid (meta a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro s2ms

  "Convert seconds to milliseconds"
  {:no-doc true}
  [s]

  `(if (spos? ~s) (* 1000 ~s) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->dispatch "Dispatch an event" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->dispose "Dispose a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->start "Start a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->stop "Stop a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->error! "Handle error" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io<stopped>
  "Called after a component has stopped" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io<started>
  "Called after a component has started" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioevent<> "Create an event" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  :default
  [^Service co & args]

  (log/info "comp->initialize: %s: %s" (gtid co) (.id co))
  (if (and (not-empty args)
           (map? (first args)))
    (->> (merge (.config co) (first args))
         (.setv (.getx co) :emcfg )))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io<started>

  :default
  [^Service co & _]

  (when-some [cfg (.config co)]
    (log/info "service config:\n%s" (pr-str cfg))
    (log/info "service %s started - ok" (.id co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io<stopped>

  :default
  [^Service co & _]

  (log/info "service %s stopped - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->dispose

  :default
  [^Service co & _]

  (log/info "service %s disposed - ok" (.id co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->error!

  :default
  [^Service co & [^Job job ^Throwable e]]

  (log/error e "")
  (when-some [wf (fatalErrorFlow<> job)]
    (.execWith wf job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro job<+>

  ""
  {:private true
   :tag Job}
  [co wf evt]

  `(with-meta (job<> ~co ~wf ~evt) {:typeid ::Job}))

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
     wf (try!! nil
               (.call rts ^String cb))]
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
  [^Container parObj emType emAlias]
  {:pre [(keyword? emType)]}

  ;; holds all the events from this source
  (let [backlog (ConcurrentHashMap.)
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

        (release [_ wevt]
          (when (some? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "releasing event, id: %s" wid)
              (.remove backlog wid))))

        (hold [_ wevt]
          (when (some? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "holding event, id: %s" wid)
              (.put backlog wid wevt))))

        (version [_] "1.0")
        (getx [_] impl)
        (id [_] emAlias)

        (parent [_] parObj)
        (setParent [_ p])

        (dispose [this]
          (io->dispose this))

        (start [this]
          (io->start this))

        (stop [this]
          (io->stop this)))

      {:typeid emType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn asyncWaitHolder<>

  "Create a async wait wrapper"
  ^EventHolder
  [^EventTrigger trigger
   ^IoEvent event ]

  (let [impl (muble<>)]
    (reify

      EventHolder

      (id [_] (.id event))

      (resumeOnResult [this res]
        (let
          [tm (.getv impl :timer)
           src (.source event) ]
          (when (some? tm)
            (.cancel ^Timer tm))
          (.unsetv impl :timer)
          (.release src this)
          (.resumeWithResult trigger res)))

      (timeoutMillis [me millis]
        (let [tm (Timer. true) ]
          (.setv impl :timer tm)
          (.schedule
            tm
            (tmtask<> #(.onExpiry me))
            (long millis))))

      (onExpiry [this]
        (let [src (.source event) ]
          (.release src this)
          (.unsetv impl :timer)
          (.resumeWithError trigger) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


