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

  czlab.skaro.io.core

  (:require
    [czlab.xlib.str :refer [toKW stror strim]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [nextLong
             cast?
             throwBadArg
             trycr
             throwIOE
             mubleObj!
             convToJava
             tryc]])

  (:use [czlab.xlib.consts]
        [czlab.skaro.core.wfs]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.misc]
        [czlab.skaro.core.consts])

  (:import
    [java.util.concurrent ConcurrentHashMap]
    [czlab.skaro.server CLJShim
     Context
     Cocoon]
    [java.util Timer TimerTask]
    [czlab.skaro.io IOEvent]
    [czlab.skaro.server Component
     EventTrigger
     Service]
    [czlab.wflow.server Emitter
     EventHolder
     ServiceHandler]
    [czlab.xlib XData
     Versioned Hierarchial
     Muble
     Identifiable Disposable Startable]
    [czlab.wflow.dsl WorkFlow Job Nihil Activity]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn meta??? "" [a & args] (:typeid (meta a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioReifyEvent "Create an event" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioDispatch "Dispatch an event" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioDispose "Dispose a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioSuspend "Suspend a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioStart "Start a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioStop "Stop a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioResume "Resume a component" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioStopped "Called after a component has stopped" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ioStarted "Called after a component has started" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStarted :default

  [co]

  (when-some [cfg (-> ^Muble co
                      (.getv :emcfg))]
    (log/info "emitter config:\n%s" (pr-str cfg))
    (log/info "emitter %s started - ok" (:typeid (meta co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStopped :default

  [co]

  (log/info "emitter %s stopped - ok" (:typeid (meta co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioDispose :default

  [co]

  (log/info "emitter %s disposed - ok" (:typeid (meta co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioSuspend :default

  [co]

  (throwIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioResume :default

  [co]

  (throwIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkPipeline ""

  ^ServiceHandler
  [^Service service traceable]

  (when traceable
    (log/info "pipeline## %s created. ok" service))

  (reify

    Identifiable
    (id [_] (.id service))

    Disposable
    (dispose [_] )

    ServiceHandler
    (handle [_ arg more]
      (let [^Job j (cast? Job more)
            w (toWorkFlow arg)]
        (if (some? j)
          (log/debug "job##%s is being serviced by %s"  (.id j) service)
          (throwBadArg "Expected Job, got " (class more)))
        (.setv j :wflow w)
        (some-> ^Emitter service
                (.container)
                (.core)
                (.run (->> (.reify (Nihil/apply) j)
                           (.reify (.startWith w)))))))

    (handleError [_ e] (log/error e ""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJob ""

  ^Job
  [container evt]

  (with-meta
    (newJob container evt)
    {:typeid (toKW "czc.skaro.io" "Job") }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent ""

  [^Cocoon ctr ^Service src evt options]

  (let [^ServiceHandler hr (.handler src)
        cfg (-> ^Muble
                src (.getv :emcfg))
        c1 (str (:router options))
        c0 (str (:handler cfg))
        rts (.getCljRt ctr)
        wf (trycr nil (->> ^String
                           (stror c1 c0)
                           (.call rts)))
        job (mkJob ctr evt) ]
    (log/debug "event type = %s" (type evt))
    (log/debug "event options = %s" options)
    (log/debug "event router = %s" c1)
    (log/debug "io-handler = %s" c0)
    (try
      (.setv job EV_OPTS options)
      (.handle hr wf job)
      (catch Throwable _
        (.handle hr (makeFatalErrorFlow job) job)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mkEmitter

  "Create an Emitter"

  [^Cocoon parObj emId emAlias]

  ;; holds all the events from this source
  (let [backlog (ConcurrentHashMap.)
        impl (mubleObj!)
        ctxt (atom (mubleObj!)) ]

    (with-meta
      (reify

        Context

        (setx [_ x] (reset! ctxt x))
        (getx [_] @ctxt)

        Muble

        (setv [_ k v] (.setv impl (keyword k) v))
        (getv [_ k]
          (let [cfg (.getv impl :emcfg)
                kw (keyword k)
                v (.getv impl kw) ]
            (or v (get cfg kw))))

        (unsetv [_ a] (.unsetv impl a) )
        (seq [_])
        (clear [_] (.clear impl))
        (toEDN [_ ] (.toEDN impl))

        Component

        (version [_] "1.0")
        (id [_] emAlias)

        Hierarchial

        (parent [_] parObj)

        Disposable

        (dispose [this] (ioDispose this))

        Startable

        (start [this]
          (when-some [p (mkPipeline this true)]
            (.setv impl :pipe p)
            (ioStart this)))

        (stop [this]
          (when-some [p (.getv impl :pipe)]
            (.dispose ^Disposable p)
            (ioStop this)
            (.unsetv impl :pipe)))

        Service

        (handler [_] (.getv impl :pipe))

        Emitter

        (container [this] (.parent this))
        (getConfig [_]
          (.getv impl :emcfg))

        (isEnabled [_] (not (false? (.getv impl :enabled))))
        (isActive [_] (not (false? (.getv impl :active))))

        (suspend [this] (ioSuspend this))
        (resume [this] (ioResume this))

        (dispatch [this ev options]
          (tryc
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

      { :typeid emId })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn asyncWaitHolder

  "Create a async wait wrapper"

  ^EventHolder
  [^EventTrigger trigger
   ^IOEvent event ]

  (let [impl (mubleObj!) ]
    (reify

      EventHolder

      (id [_] (.getId event))

      (resumeOnResult [this res]
        (let [^Timer tm (.getv impl :timer)
              src (.emitter event) ]
          (when (some? tm) (.cancel tm))
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
          (.setv impl :timer nil)
          (.resumeWithError trigger) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compContextualize :czc.skaro.io/Emitter

  [co arg]

  (when (instance? Context arg)
    (->> (-> ^Context
             arg (.getx ))
         (compCloneContext co ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map of emitter hierarchy
;;
(derive :czc.skaro.io/HTTP :czc.skaro.io/Emitter)

(derive :czc.skaro.io/JettyIO :czc.skaro.io/HTTP)
(derive :czc.skaro.io/NettyIO :czc.skaro.io/HTTP)
;;(derive :czc.skaro.io/WebSockIO :czc.skaro.io/NettyIO)
(derive :czc.skaro.io/NettyMVC :czc.skaro.io/NettyIO)

(derive :czc.skaro.io/RepeatingTimer :czc.skaro.io/Emitter)
(derive :czc.skaro.io/OnceTimer :czc.skaro.io/Emitter)
(derive :czc.skaro.io/ThreadedTimer :czc.skaro.io/RepeatingTimer)

(derive :czc.skaro.io/FilePicker :czc.skaro.io/ThreadedTimer)
(derive :czc.skaro.io/IMAP :czc.skaro.io/ThreadedTimer)
(derive :czc.skaro.io/POP3 :czc.skaro.io/ThreadedTimer)

(derive :czc.skaro.io/JMS :czc.skaro.io/Emitter)
(derive :czc.skaro.io/SocketIO :czc.skaro.io/Emitter)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


