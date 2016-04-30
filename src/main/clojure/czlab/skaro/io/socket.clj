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

  czlab.skaro.io.socket

  (:require
    [czlab.xlib.util.core
    :refer [NextLong test-posnum ConvLong spos?]]
    [czlab.xlib.util.process :refer [Coroutine]]
    [czlab.xlib.util.meta :refer [GetCldr]]
    [czlab.xlib.util.io :refer [CloseQ]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [strim hgl?]])

  (:use [czlab.skaro.io.core]
        [czlab.skaro.core.sys])

  (:import
    [java.net InetAddress ServerSocket Socket]
    [com.zotohlab.frwk.server Emitter]
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

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (getSession [_] )
        (getId [_] eeid)
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

  (let [{:keys [timeoutMillis backlog host]
         :as cfg}
        (merge (.getv co :dftOptions) cfg0) ]

    (with-local-vars [cpy (transient cfg)]
      (var-set cpy (assoc! @cpy :backlog
                           (if (spos? backlog) backlog 100)))
      (var-set cpy (assoc! @cpy
                           :host (strim host)))
      (var-set cpy (assoc! @cpy
                           :timeoutMillis
                           (if (spos? timeoutMillis) timeoutMillis 0)))
      (.setv co :emcfg (persistent! @cpy)))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.io/SocketIO

  [^Muble co]

  (log/info "CompInitialize: SocketIO: %s" (.id ^Identifiable co))
  (let [{:keys [backlog host port]}
        (.getv co :emcfg)
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

  [^Emitter co ^Socket soc]

  (.dispatch co (IOESReifyEvent co soc) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/SocketIO

  [^Muble co & args]

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

  [^Muble co & args]

  (log/info "IOESStop: SocketIO: %s" (.id ^Identifiable co))
  (let [^ServerSocket ssoc (.getv co :ssocket) ]
    (CloseQ ssoc)
    (.setv co :ssocket nil)
    (IOESStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

