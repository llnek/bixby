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

  czlab.skaro.io.socket

  (:require
    [czlab.xlib.core
     :refer [test-posnum
             convLong
             muble<>
             seqint
             spos?]]
    [czlab.xlib.process :refer [async!]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.io :refer [closeQ]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [strim hgl?]])

  (:use [czlab.skaro.io.core]
        [czlab.skaro.core.sys])

  (:import
    [java.net InetAddress ServerSocket Socket]
    [czlab.server EventEmitter]
    [czlab.xlib Muble Identifiable]
    [czlab.skaro.io SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent

  ::Socket
  [^EventEmitter co & args]

  (log/info "ioevent: Socket: %s" (.id ^Identifiable co))
  (let [^Socket soc (first args)
        impl (muble<>)
        eeid (seqint2) ]
    (with-meta
      (reify

        Context
        (getx [_] impl)

        Identifiable
        (id [_] eeid)

        SocketEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (getSession [_] )
        (id [_] eeid)
        (getSockOut [_] (.getOutputStream soc))
        (getSockIn [_] (.getInputStream soc))
        (emitter [_] co)
        (dispose [_] (closeQ soc)))

      {:typeid ::SocketEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::Socket
  [^Context co cfg0]

  (log/info "comp->configure: Socket: %s" (.id ^Identifiable co))
  (test-posnum "socket-io port" (:port cfg0))

  (let [{:keys [timeoutMillis backlog host]
         :as cfg}
        (merge (.getv (.getx co) :dftOptions) cfg0) ]
    (with-local-vars [cpy (transient cfg)]
      (var-set cpy (assoc! @cpy :backlog
                           (if (spos? backlog) backlog 100)))
      (var-set cpy (assoc! @cpy
                           :host (strim host)))
      (var-set cpy (assoc! @cpy
                           :timeoutMillis
                           (if (spos? timeoutMillis) timeoutMillis 0)))
      (.setv (.getx co) :emcfg (persistent! @cpy)))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Socket
  [^Context co]

  (log/info "comp->initialize: Socket: %s" (.id ^Identifiable co))
  (let [{:keys [backlog host port]}
        (.getv (.getx co) :emcfg)
        ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost))
        soc (ServerSocket. port backlog ip) ]
    (log/info "Opened Server Socket %s (bound?) " soc (.isBound soc))
    (doto soc (.setReuseAddress true))
    (.setv (.getx co) :ssocket soc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown

  ""
  [^EventEmitter co ^Socket soc]

  (.dispatch co (ioevent<> co soc) nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::Socket
  [^Context co & args]

  (log/info "io->start: Socket: %s" (.id ^Identifiable co))
  (let [^ServerSocket
        ssoc (.getv (.getx co) :ssocket)]
    (when (some? ssoc)
      (async!
        #(while (.isBound ssoc)
           (try
             (sockItDown co (.accept ssoc))
             (catch Throwable e#
               (log/warn e# "")
               (closeQ ssoc)
               (.unsetv (.getx co) :ssocket ))))
        (getCldr)))
    (io->started co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::Socket
  [^Context co & args]

  (log/info "io->stop: Socket: %s" (.id ^Identifiable co))
  (let [^ServerSocket
        ssoc (.getv (.getx co) :ssocket) ]
    (closeQ ssoc)
    (.unsetv (.getx co) :ssocket )
    (io->stopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


