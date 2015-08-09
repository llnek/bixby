;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.io.core

  (:require
    [czlab.xlib.util.str :refer [ToKW stror strim]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.core
    :refer [NextLong Cast? ThrowBadArg
    trycr ThrowIOE MakeMMap ConvToJava tryc]])

  (:use [czlab.xlib.util.consts]
        [czlab.xlib.util.wfs]
        [czlab.skaro.core.sys]
        [czlab.skaro.impl.misc]
        [czlab.skaro.core.consts])

  (:import
    [com.zotohlab.skaro.core Context Container Muble]
    [java.util.concurrent ConcurrentHashMap]
    [com.zotohlab.skaro.etc CliMain]
    [com.zotohlab.skaro.io IOEvent]
    [com.zotohlab.frwk.server Component
    Emitter
    ServiceHandler Service]
    [java.util Timer TimerTask]
    [com.zotohlab.frwk.io XData]
    [com.zotohlab.frwk.core Versioned Hierarchial
    Identifiable Disposable Startable]
    [com.zotohlab.wflow WorkFlow Job Nihil Activity]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol EmitAPI

  "Emitter API"

  (dispatch [_ evt options] )

  (enabled? [_] )
  (active? [_] )

  (suspend [_] )
  (resume [_] )

  (release [_ wevt] )
  (hold [_ wevt] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol WaitEventHolder

  "Wrapper to hold an event"

  (timeoutMillis [_ millis] )
  (resumeOnResult [_ res] )
  (onExpiry [_])
  (timeoutSecs [_ secs] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol AsyncWaitTrigger

  "Trigger to rerun a waiting event"

  (resumeWithResult [_ res] )
  (resumeWithError [_] )
  (emitter [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESReifyEvent

  "Create an event"

  (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESDispatch

  "Dispatch an event"

  (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESDispose

  "Dispose a component"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESSuspend

  "Suspend a component"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStart

  "Start a component"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStop

  "Stop a component"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESResume

  "Resume a component"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStopped

  "Called after a component has stopped"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStarted

  "Called after a component has started"

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStarted :default

  [^Muble co]

  (when-some [cfg (.getv co :emcfg)]
    (log/info "emitter config:\n%s" (pr-str cfg))
    (log/info "emitter %s started - ok" (:typeid (meta co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStopped :default

  [co]

  (log/info "emitter %s stopped - ok" (:typeid (meta co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESDispose :default

  [co]

  (log/info "emitter %s disposed - ok" (:typeid (meta co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESSuspend :default

  [co]

  (ThrowIOE "Not Implemented"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESResume :default

  [co]

  (ThrowIOE "Not Implemented"))

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
      (let [^Job j (Cast? Job more)
            w (ToWorkFlow arg)]
        (if (some? j)
          (log/debug "job##%s is being serviced by %s"  (.id j) service)
          (ThrowBadArg "Expected Job, got " (class more)))
        (.setv j :wflow w)
        (-> ^Emitter service
            (.container)
            (.core)
            (.run (->> (.reify (Nihil/apply) j)
                       (.reify (.startWith w)))))))

    (handleError [_ e]
      (log/error e ""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJob ""

  ^Job
  [container evt]

  (with-meta
    (NewJob container evt)
    {:typeid (ToKW "czc.skaro.io" "Job") }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEvent ""

  [^Muble ctr ^Muble src evt options]

  (let [^CliMain rts (.getv ctr :cljshim)
        ^ServiceHandler
        hr (.handler ^Service src)
        cfg (.getv src :emcfg)
        c0 (str (:handler cfg))
        c1 (str (:router options))
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
        (.handle hr (MakeFatalErrorFlow job) job)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeEmitter

  "Create an Emitter"

  [^Container parObj emId emAlias]

  ;; holds all the events from this source
  (let [backlog (ConcurrentHashMap.)
        impl (MakeMMap)
        ctxt (atom (MakeMMap)) ]

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

        Emitter

        (container [this] (.parent this))
        (getConfig [_]
          (.getv impl :emcfg))

        Disposable

        (dispose [this] (IOESDispose this))

        Startable

        (start [this]
          (when-some [p (mkPipeline this true)]
            (.setv impl :pipe p)
            (IOESStart this)))

        (stop [this]
          (when-some [p (.getv impl :pipe)]
            (.dispose ^Disposable p)
            (IOESStop this)
            (.unsetv impl :pipe)))

        Service

        (handler [_] (.getv impl :pipe))

        EmitAPI

        (enabled? [_] (if (false? (.getv impl :enabled)) false true ))
        (active? [_] (if (false? (.getv impl :active)) false true))

        (suspend [this] (IOESSuspend this))
        (resume [this] (IOESResume this))

        (dispatch [this ev options]
          (tryc
            (onEvent parObj this ev options)))

        (release [_ wevt]
          (when (some? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "emitter releasing an event with id: %s" wid)
              (.remove backlog wid))))

        (hold [_ wevt]
          (when (some? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "emitter holding an event with id: %s" wid)
              (.put backlog wid wevt)))) )

      { :typeid emId })))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeAsyncWaitHolder

  "Create a async wait wrapper"

  [^czlab.skaro.io.core.AsyncWaitTrigger trigger
   ^IOEvent event ]

  (let [impl (MakeMMap) ]
    (reify

      Identifiable

      (id [_] (.getId event))

      WaitEventHolder

      (resumeOnResult [this res]
        (let [^Timer tm (.getv impl :timer)
              ^czlab.skaro.io.core.EmitAPI
              src (.emitter event) ]
          (when (some? tm) (.cancel tm))
          (.release src this)
          ;;(.mm-s impl :result res)
          (.resumeWithResult trigger res)))

      (timeoutMillis [me millis]
        (let [tm (Timer. true) ]
          (.setv impl :timer tm)
          (.schedule
            tm
            (proxy [TimerTask][]
              (run [] (.onExpiry me))) ^long millis)))

      (timeoutSecs [this secs]
        (timeoutMillis this (* 1000 secs)))

      (onExpiry [this]
        (let [^czlab.skaro.io.core.EmitAPI
              src (.emitter event) ]
          (.release src this)
          (.setv impl :timer nil)
          (.resumeWithError trigger) )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :czc.skaro.io/Emitter

  [co arg]

  ;; arg is Container here
  (when-some [^Context c arg ]
    (CompCloneContext co (.getx c))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map of emitter hierarchy.
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

