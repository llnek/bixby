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

  czlabclj.tardis.io.core

  (:require [czlabclj.xlib.util.core
             :refer
             [NextLong
              notnil?
              ThrowIOE
              MakeMMap
              ConvToJava
              tryc]]
            [czlabclj.xlib.util.str :refer [nsb strim]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.wfs]
        [czlabclj.tardis.core.sys])

  (:import  [com.zotohlab.frwk.server Component
             Emitter
             ServiceHandler Service]
            [java.util.concurrent ConcurrentHashMap]
            [com.zotohlab.frwk.core Versioned Hierarchial
             Identifiable Disposable Startable]
            [com.zotohlab.skaro.core Container]
            [com.zotohlab.wflow WorkFlow Job Nihil Activity]
            [com.google.gson JsonObject JsonArray]
            [java.util Map]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol EmitAPI

  "Emitter API."

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

  "Wrapper to hold an event."

  (timeoutMillis [_ millis] )
  (resumeOnResult [_ res] )
  (onExpiry [_])
  (timeoutSecs [_ secs] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol AsyncWaitTrigger

  "Trigger to rerun a waiting event."

  (resumeWithResult [_ res] )
  (resumeWithError [_] )
  (emitter [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESReifyEvent

  "Create an event."

  (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESDispatch

  "Dispatch an event."

  (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESDispose

  "Dispose a component."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESSuspend

  "Suspend a component."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStart

  "Start a component."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStop

  "Stop a component."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESResume

  "Resume a component."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStopped

  "Called after a component has stopped."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStarted

  "Called after a component has started."

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStarted :default

  [^czlabclj.tardis.core.sys.Elmt co]

  (when-let [cfg (.getAttr co :emcfg)]
    (log/info "Emitter config:\n" (pr-str cfg))
    (log/info "Emitter " (:typeid (meta co)) " started - OK")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStopped :default

  [co]

  (log/info "Emitter " (:typeid (meta co))  " stopped - OK"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESDispose :default

  [co]

  (log/info "Emitter " (:typeid (meta co))  " disposed - OK"))

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
    (log/info "Pipeline##" service " created. OK."))

  (reify

    Identifiable
    (id [_] (.id service))

    Disposable
    (dispose [_] )

    ServiceHandler
    (handle [_ arg options]
      (let [^Job j (when (instance? Job options) options)
            w (ToWorkFlow arg)]
        (when-not (nil? j)
          (log/debug "Job##" (.id j)
                     " is being serviced by " service))
        (-> ^Emitter service
            (.container)
            (.core)
            (.run (->> (.reify (Nihil/apply) j)
                       (.reify (.startWith w)))))))

    (handleError [_ e])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeEmitter "Create an Emitter."

  [^Container parObj emId emAlias]

  ;; holds all the events from this source.
  (let [backlog (ConcurrentHashMap.)
        impl (MakeMMap) ]
    (with-meta
      (reify

        Elmt

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toEDN [_ ] (.toEDN impl))

        Component

        (version [_] "1.0")
        (id [_] emAlias)

        Hierarchial

        (parent [_] parObj)

        Emitter

        (container [this] (.parent this))
        (getConfig [_]
          (when-let [cfg (.getf impl :emcfg)]
            (ConvToJava cfg)))

        Disposable

        (dispose [this] (IOESDispose this))

        Startable

        (start [this]
          (when-let [p (mkPipeline this true)]
            (.setf! impl :pipe p)
            (IOESStart this)))

        (stop [this]
          (when-let [p (.getf impl :pipe)]
            (.dispose ^Disposable p)
            (IOESStop this)
            (.clrf! impl :pipe)))

        Service

        (setv [_ k v] (.setf! impl (keyword k) v))
        (getv [_ k]
          (let [cfg (.getf impl :emcfg)
                kw (keyword k)
                v (.getf impl kw) ]
            (or v (get cfg kw))))

        (handler [_] (.getf impl :pipe))

        EmitAPI

        (enabled? [_] (if (false? (.getf impl :enabled)) false true ))
        (active? [_] (if (false? (.getf impl :active)) false true))

        (suspend [this] (IOESSuspend this))
        (resume [this] (IOESResume this))

        (dispatch [_ ev options]
          (tryc
            (-> (.eventBus parObj)
                (.onEvent ev options))))

        (release [_ wevt]
          (when-not (nil? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "Emitter releasing an event with id: " wid)
              (.remove backlog wid))))

        (hold [_ wevt]
          (when-not (nil? wevt)
            (let [wid (.id ^Identifiable wevt)]
              (log/debug "Emitter holding an event with id: " wid)
              (.put backlog wid wevt)))) )

      { :typeid emId }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :czc.tardis.io/Emitter

  [co arg]

  ;; arg is Container here
  (when-let [^czlabclj.tardis.core.sys.Elmt c arg ]
    (CompCloneContext co (.getCtx c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map of emitter hierarchy.
;;
(derive :czc.tardis.io/HTTP :czc.tardis.io/Emitter)

(derive :czc.tardis.io/JettyIO :czc.tardis.io/HTTP)
(derive :czc.tardis.io/NettyIO :czc.tardis.io/HTTP)
;;(derive :czc.tardis.io/WebSockIO :czc.tardis.io/NettyIO)
(derive :czc.tardis.io/NettyMVC :czc.tardis.io/NettyIO)

(derive :czc.tardis.io/RepeatingTimer :czc.tardis.io/Emitter)
(derive :czc.tardis.io/OnceTimer :czc.tardis.io/Emitter)
(derive :czc.tardis.io/ThreadedTimer :czc.tardis.io/RepeatingTimer)

(derive :czc.tardis.io/FilePicker :czc.tardis.io/ThreadedTimer)
(derive :czc.tardis.io/IMAP :czc.tardis.io/ThreadedTimer)
(derive :czc.tardis.io/POP3 :czc.tardis.io/ThreadedTimer)

(derive :czc.tardis.io/JMS :czc.tardis.io/Emitter)
(derive :czc.tardis.io/SocketIO :czc.tardis.io/Emitter)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

