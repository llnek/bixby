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
             tmtask<>
             spos?
             seqint2
             cast?
             try!
             throwIOE
             muble<>
             convToJava]])

  (:use [czlab.xlib.consts]
        [czlab.wflow.core]
        [czlab.skaro.sys.core]
        [czlab.skaro.sys.misc])

  (:import
    [java.util.concurrent ConcurrentHashMap]
    [czlab.skaro.server
     EventTrigger
     Service
     Cljshim
     Component
     Container]
    [java.util Timer TimerTask]
    [czlab.skaro.io IOEvent]
    [czlab.server
     Emitter
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
    [czlab.wflow WorkStream Job TaskDef]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro s2ms
  ""
  {:no-doc true}
  [s]
  `(if (spos? ~s) (* 1000 ~s) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn meta???

  ""
  {:no-doc true}
  [a & xs] (:typeid (meta a)))

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
(defmulti io<stopped>
  "Called after a component has stopped" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti io<started>
  "Called after a component has started" meta???)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io<started>

  :default
  [^Service co]

  (when-some [cfg (-> (.getx co)
                      (.getv :emcfg))]
    (log/info "service config:\n%s" (pr-str cfg))
    (log/info "service %s started - ok" (gtid co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io<stopped>

  :default
  [^Service co]

  (log/info "service %s stopped - ok" (gtid co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->dispose

  :default
  [^Service co]

  (log/info "service %s disposed - ok" (gtid co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->suspend

  :default
  [^Service co]

  (throwIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->resume

  :default
  [^Service co]

  (throwIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkSvcHdr

  ""
  ^ServiceHandler
  [^Service svc & [trace?]]

  (when trace?
    (log/info "handler for %s created - ok"
              (.id svc)))
  (reify ServiceHandler

    (id [_] (.id svc))

    (dispose [_] )

    (handle [_ p1 p2]
      (let
        [^WorkStream w (cast? WorkStream p1)
         ^Job j (cast? Job p2)]
        (if (nil? w)
          (throwBadArg "Want WorkStream, got " (class p1)))
        (if (some? j)
          (throwBadArg "Want Job, got " (class p2)))
        (log/debug "job#%s - handled by %s"  (.id j) svc)
        (.setv j :wflow w)
        (.execWith w j)))

    (handleError [_ e] (log/error e ""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn job<+>

  ""
  ^Job
  [^Container co wf evt]

  (with-meta
    (job<> co wf evt) {:typeid ::Job}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent

  ""
  [^Container ctr ^Service src evt options]

  (let
    [c1 (str (:router options))
     cfg (-> (.getx src)
             (.getv :emcfg))
     hr (.handler src)
     c0 (str (:handler cfg))
     rts (.cljrt ctr)
     wf (try!
          (.call rts ^String (stror c1 c0)))
     job (job<+> ctr wf evt)]
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
(defn service<>

  "Create a Service"
  [^Container parObj emId emAlias]

  ;; holds all the events from this source
  (let [backlog (ConcurrentHashMap.)
        impl (muble<>)]
    (with-meta
      (reify Service

        (isEnabled [_]
          (not (false? (.getv impl :enabled))))

        (isActive [_]
          (not (false? (.getv impl :active))))

        (suspend [this] (io->suspend this))
        (resume [this] (io->resume this))
        (handler [_] (.getv impl :pipe))
        (config [_] (.getv impl :emcfg))
        (server [this] (.parent this))
        (dispatch [this ev]
          (try!
            (onEvent parObj this ev nil)))
        (dispatchEx [this ev arg]
          (try!
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

        Hierarchial

        (parent [_] parObj)
        (setParent [_ p])

        Disposable

        (dispose [this]
          (when-some
            [p (.getv impl :pipe)]
            (.dispose ^Disposable p)
            (.unsetv impl :pipe))
          (io->dispose this))

        Startable

        (start [this]
          (->> (mkSvcHdr this true)
               (.setv impl :pipe ))
          (io->start this))

        (stop [this] (io->stop this)))

      {:typeid emId})))

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
            (tmtask<> #(.onExpiry me))
            (long millis))))

      (onExpiry [this]
        (let [src (.emitter event) ]
          (.release src this)
          (.unsetv impl :timer)
          (.resumeWithError trigger) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map of emitter hierarchy
;;
(derive ::HTTP ::Emitter)
(derive ::Jetty ::HTTP)
(derive ::Netty ::HTTP)
;;(derive :czc.skaro.io/WebSockIO :czc.skaro.io/NettyIO)
(derive ::NettyMVC ::Netty)

(derive ::RepeatingTimer ::Emitter)
(derive ::OnceTimer ::Emitter)
(derive ::ThreadedTimer ::RepeatingTimer)

(derive ::FilePicker ::ThreadedTimer)
(derive ::IMAP ::ThreadedTimer)
(derive ::POP3 ::ThreadedTimer)

(derive ::JMS ::Emitter)
(derive ::Socket ::Emitter)
;;(derive ::SocketIO ::Emitter)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


