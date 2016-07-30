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

  czlab.skaro.io.core

  (:require
    [czlab.xlib.str :refer [stror strim]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [throwBadArg
             seqint
             cast?
             try!!
             throwIOE
             muble<>
             convToJava]])

  (:use [czlab.xlib.consts]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.misc]
        [czlab.skaro.core.consts])

  (:import
    [java.util.concurrent ConcurrentHashMap]
    [czlab.skaro.server
     CLJShim
     Context
     Container]
    [java.util Timer TimerTask]
    [czlab.skaro.io IOEvent]
    [czlab.skaro.server
     Component
     Service
     EventTrigger]
    [czlab.server
     EventEmitter
     EventHolder
     ServiceHandler]
    [czlab.xlib
     XData
     Versioned
     Hierarchial
     Muble
     Identifiable
     Disposable
     Startable]
    [czlab.wflow Job TaskDef]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- meta??? "" [a & args] (:typeid (meta a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioevent<> "Create an event" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->dispatch "Dispatch an event" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->dispose "Dispose a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->suspend "Suspend a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->start "Start a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->stop "Stop a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io->resume "Resume a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io<stopped> "Called after a component has stopped" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io<started> "Called after a component has started" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io<started>

  :default
  [^Context co]

  (when-some [cfg (-> (.getx co)
                      (.getv :emcfg))]
    (log/info "emitter config:\n%s" (pr-str cfg))
    (log/info "emitter %s started - ok" (:typeid (meta co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io<stopped>

  :default
  [co]

  (log/info "emitter %s stopped - ok" (:typeid (meta co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->dispose

  :default
  [co]

  (log/info "emitter %s disposed - ok" (:typeid (meta co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->suspend

  :default
  [co]

  (throwIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->resume

  :default
  [co]

  (throwIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkPipeline

  ""
  ^ServiceHandler
  [^Service svc trace?]

  (when trace?
    (log/info "pipeline## %s created - ok" service))

  (reify

    Identifiable
    (id [_] (.id svc))

    Disposable
    (dispose [_] )

    ServiceHandler
    (handle [_ arg more]
      (let
        [^Job j (cast? Job more)
         w (toWorkFlow arg)]
        (if (some? j)
          (log/debug "job#%s is being serviced by %s"  (.id j) service)
          (throwBadArg "Expected Job, got " (class more)))
        (.setv j :wflow w)
        (.execWith
          w
          (-> (.server ^EventEmitter svc)
              (.core))
          j)))

    (handleError [_ e] (log/error e ""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJob

  ""
  ^Job
  [co evt]

  (with-meta
    (job<> co evt)
    {:typeid ::Job}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent

  ""
  [^Container ctr ^Service src evt options]

  (let
    [hr (.handler src)
     cfg (-> (.getx ^Context src)
             (.getv :emcfg))
     c1 (str (:router options))
     c0 (str (:handler cfg))
     rts (.getCljRt ctr)
     wf (try!
          (.call rtx ^String (stror c1 c0)))
     job (job<> ctr evt)]
    (log/debug "event type = %s" (type evt))
    (log/debug "event opts = %s" options)
    (log/debug "event router = %s" c1)
    (log/debug "io-handler = %s" c0)
    (try
      (.setv job EV_OPTS options)
      (.handle hr wf job)
      (catch Throwable _
        (.handle hr (fatalErrorFlow<> job) job)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn emitter<>

  "Create an Emitter"
  [^Container parObj emId emAlias]

  ;; holds all the events from this source
  (let [backlog (ConcurrentHashMap.)
        impl (mubleObj!)]

    (with-meta
      (reify

        Context

        (getx [_] impl)

        Component

        (version [_] "1.0")
        (id [_] emAlias)

        Hierarchial

        (parent [_] parObj)

        Disposable

        (dispose [this] (io->dispose this))

        Startable

        (start [this]
          (->> (mkPipeline this true)
               (.setv impl :pipe ))
          (io->start this))

        (stop [this]
          (when-some
            [p (.getv impl :pipe)]
            (.dispose ^Disposable p)
            (io->stop this)
            (.unsetv impl :pipe)))

        Service

        (handler [_] (.getv impl :pipe))

        EventEmitter

        (config [_] (.getv impl :emcfg))
        (server [this] (.parent this))

        (isEnabled [_]
          (not (false? (.getv impl :enabled))))

        (isActive [_]
          (not (false? (.getv impl :active))))

        (suspend [this] (io->suspend this))
        (resume [this] (io->resume this))

        (dispatch [this ev options]
          (try!
            (onEvent parObj this ev options)))

        (release [_ wevt]
          (when (some? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "emitter releasing event, id: %s" wid)
              (.remove backlog wid))))

        (hold [_ wevt]
          (when (some? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "emitter holding event, id: %s" wid)
              (.put backlog wid wevt)))) )

      {:typeid (asFQKeyword (name emId))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn asyncWaitHolder<>

  "Create a async wait wrapper"
  ^EventHolder
  [^EventTrigger trigger
   ^IOEvent event ]

  (let [impl (muble<>)]
    (reify

      EventHolder

      (id [_] (.id event))

      (resumeOnResult [this res]
        (let
          [tm (.getv impl :timer)
           src (.emitter event) ]
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
            (proxy [TimerTask][]
              (run [] (.onExpiry me)))
            (long millis))))

      (onExpiry [this]
        (let [src (.emitter event) ]
          (.release src this)
          (.unsetv impl :timer)
          (.resumeWithError trigger) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->contextualize

  ::EventEmitter
  [co arg]

  (when (and (inst? Context co)
             (inst? Muble arg))
    (-> (.getx ^Context co)
        (.copy  ^Muble arg)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map of emitter hierarchy
;;
(derive ::HTTP ::EventEmitter)
(derive ::Jetty ::HTTP)
(derive ::Netty ::HTTP)
;;(derive :czc.skaro.io/WebSockIO :czc.skaro.io/NettyIO)
(derive ::NettyMVC ::Netty)

(derive ::RepeatingTimer ::EventEmitter)
(derive ::OnceTimer ::EventEmitter)
(derive ::ThreadedTimer ::RepeatingTimer)

(derive ::FilePicker ::ThreadedTimer)
(derive ::IMAP ::ThreadedTimer)
(derive ::POP3 ::ThreadedTimer)

(derive ::JMS ::EventEmitter)
(derive ::Socket ::EventEmitter)
;;(derive ::SocketIO ::EventEmitter)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


