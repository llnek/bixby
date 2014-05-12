;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.io.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.util.core :only [notnil? ThrowIOE MakeMMap TryC] ])
  (:use [comzotohlabscljc.util.str :only [nsb strim ] ])

  (:import (com.zotohlabs.frwk.server Component Service))
  (:import (java.util.concurrent ConcurrentHashMap))

  (:import (com.zotohlabs.frwk.core Versioned Hierarchial
                                    Identifiable Disposable Startable))
  (:import (com.zotohlabs.gallifrey.core Container))
  (:import (com.google.gson JsonObject JsonArray))
  (:import (com.zotohlabs.gallifrey.io ServletEmitter Emitter))
  (:import (java.util Map)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol EmitterAPI

  ""
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

  ""

  (timeoutMillis [_ millis] )
  (resumeOnResult [_ res] )
  (onExpiry [_])
  (timeoutSecs [_ secs] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol AsyncWaitTrigger

  ""

  (resumeWithResult [_ res] )
  (resumeWithError [_] )
  (emitter [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasHeader? ""

  ;; boolean
  [^JsonObject info ^String header]

  (let [ ^JsonObject h (if (nil? info) nil (.getAsJsonObject info "headers")) ]
    (and (notnil? h)
         (.has h (cstr/lower-case header)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasParam? ""

  ;; boolean
  [^JsonObject info ^String param]

  (let [ ^JsonObject h (if (nil? info) nil (.getAsJsonObject info "params")) ]
    (and (notnil? h)
         (.has h param))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetParameter ""

  ^String
  [^JsonObject info ^String pm]

  (let [ ^JsonObject h (if (nil? info) nil (.getAsJsonObject info "params"))
         hv (nsb pm)
         ^JsonArray a (if (and (notnil? h)
                                (.has h hv))
                          (.getAsJsonArray h hv)
                          nil) ]
    (if (and (notnil? a)
             (> (.size a) 0))
        (.get a 0)
        nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHeader ""

  ^String
  [^JsonObject info ^String header]

  (let [ ^JsonObject h (if (nil? info) nil (.getAsJsonObject info "headers"))
         hv (cstr/lower-case (nsb header))
         ^JsonArray a (if (and (notnil? h)
                                (.has h hv))
                          (.getAsJsonArray h hv)
                          nil) ]
    (if (and (notnil? a)
             (> (.size a) 0))
        (.get a 0)
        nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESReifyEvent "" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESDispatch "" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESDispose "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESSuspend "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStart "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStop "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESResume "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStopped "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IOESStarted "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStarted :default

  [co]

  (log/info "Emitter " (:typeid (meta co)) " started - OK"))

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
(defn MakeEmitter ""

  [^Container parObj emId emAlias]

  (let [ impl (MakeMMap)
         eeid emAlias ]
    (.setf! impl :backlog (ConcurrentHashMap.))
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )

        Component

        (version [_] "1.0")
        (id [_] eeid)

        Hierarchial

        (parent [_] parObj)

        Emitter

        (container [this] (.parent this))

        Disposable

        (dispose [this] (IOESDispose this))

        Startable

        (start [this] (IOESStart this))
        (stop [this] (IOESStop this))

        Service

        (setv [_ k v] (.setf! impl (keyword k) v))
        (getv [_ k] (.getf impl (keyword k)))

        EmitterAPI

        (enabled? [_] (if (false? (.getf impl :enabled)) false true ))
        (active? [_] (if (false? (.getf impl :active)) false true))

        (suspend [this] (IOESSuspend this))
        (resume [this] (IOESResume this))

        (dispatch [_ ev options]
          (TryC
              (.notifyObservers parObj ev options) ))

        (release [_ wevt]
          (when-not (nil? wevt)
            (let [ wid (.id ^Identifiable wevt)
                   b (.getf impl :backlog) ]
              (log/debug "emitter releasing an event with id: " wid)
              (.remove ^Map b wid))))

        (hold [_ wevt]
          (when-not (nil? wevt)
            (let [ wid (.id ^Identifiable wevt)
                   b (.getf impl :backlog) ]
              (log/debug "emitter holding an event with id: " wid)
              (.put ^Map b wid wevt)))) )

      { :typeid emId }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :czc.tardis.io/Emitter

  [co ctx]

  (let [ ^comzotohlabscljc.tardis.core.sys.Element c ctx ]
    (CompCloneContext co (.getCtx c))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(derive :czc.tardis.io/HTTP :czc.tardis.io/Emitter)

(derive :czc.tardis.io/JettyIO :czc.tardis.io/HTTP)
(derive :czc.tardis.io/NettyIO :czc.tardis.io/HTTP)

(derive :czc.tardis.io/WebSockIO :czc.tardis.io/NettyIO)
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
;;
(def ^:private core-eof nil)

