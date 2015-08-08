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

  czlab.skaro.io.socket

  (:require
    [czlab.xlib.util.core
    :refer [NextLong test-posnum ConvLong spos?]]
    [czlab.xlib.util.process :refer [Coroutine]]
    [czlab.xlib.util.meta :refer [GetCldr]]
    [czlab.xlib.util.io :refer [CloseQ]]
    [czlab.xlib.util.str :refer [strim hgl?]])

  (:require
    [czlab.xlib.util.logging :as log])

  (:use [czlab.skaro.io.core]
        [czlab.skaro.core.sys])

  (:import
    [java.net InetAddress ServerSocket Socket]
    [com.zotohlab.frwk.core Identifiable]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.skaro.io SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/SocketIO

  [co & args]

  (log/info "IOESReifyEvent: SocketIO: %s" (.id ^Identifiable co))
  (let [^Socket soc (first args)
        eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        SocketEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (getSockOut [_] (.getOutputStream soc))
        (getSockIn [_] (.getInputStream soc))
        (emitter [_] co)
        (dispose [_] (CloseQ soc)))

      {:typeid :czc.skaro.io/SocketEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/SocketIO

  [^Muble co cfg0]

  (log/info "compConfigure: SocketIO: %s" (.id ^Identifiable co))
  (test-posnum "socket-io port" (:port cfg0))
  (let [cfg (merge (.getv co :dftOptions) cfg0)
        tout (:timeoutMillis cfg)
        blog (:backlog cfg) ]
    (with-local-vars [cpy (transient cfg)]
      (var-set cpy (assoc! @cpy :backlog
                           (if (spos? blog) blog 100)))
      (var-set cpy (assoc! @cpy
                           :host (strim (:host cfg))))
      (var-set cpy (assoc! @cpy
                           :timeoutMillis
                           (if (spos? tout) tout 0)))
      (.setv co :emcfg (persistent! @cpy)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.io/SocketIO

  [^Muble co]

  (log/info "CompInitialize: SocketIO: %s" (.id ^Identifiable co))
  (let [cfg (.getv co :emcfg)
        backlog (:backlog cfg)
        host (:host cfg)
        port (:port cfg)
        ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost))
        soc (ServerSocket. port backlog ip) ]
    (log/info "Opened Server Socket %s (bound?) " soc (.isBound soc))
    (doto soc (.setReuseAddress true))
    (.setv co :ssocket soc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown ""

  [^czlab.skaro.io.core.EmitAPI co ^Socket soc]

  (.dispatch co (IOESReifyEvent co soc) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/SocketIO

  [^Muble co]

  (log/info "IOESStart: SocketIO: %s" (.id ^Identifiable co))
  (let [^ServerSocket ssoc (.getv co :ssocket)]
    (when (some? ssoc)
      (Coroutine #(while (.isBound ssoc)
                    (try
                      (sockItDown co (.accept ssoc))
                      (catch Throwable e#
                        (log/warn e# "")
                        (CloseQ ssoc)
                        (.setv co :ssocket nil))))
                 (GetCldr)))
    (IOESStarted co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/SocketIO

  [^Muble co]

  (log/info "IOESStop: SocketIO: %s" (.id ^Identifiable co))
  (let [^ServerSocket ssoc (.getv co :ssocket) ]
    (CloseQ ssoc)
    (.setv co :ssocket nil)
    (IOESStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

