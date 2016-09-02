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


(ns ^{:doc "Implementation for TCP socket service."
      :author "Kenneth Leung" }

  czlab.skaro.io.socket

  (:require
    [czlab.xlib.core
     :refer [test-posnum
             convLong
             try!!
             spos?
             try!
             muble<>
             seqint2]]
    [czlab.xlib.process :refer [async!]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.io :refer [closeQ]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [strim hgl?]])

  (:use [czlab.skaro.io.core]
        [czlab.skaro.sys.core])

  (:import
    [java.net InetAddress ServerSocket Socket]
    [czlab.skaro.server Service]
    [czlab.xlib Muble Identifiable]
    [czlab.skaro.io SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(derive ::Socket :czlab.skaro.io.core/Service)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::Socket
  [^Service co & [^Socket soc]]

  (log/info "ioevent: %s: %s" (gtid co) (.id co))
  (let [impl (muble<>)
        eeid (seqint2) ]
    (with-meta
      (reify SocketEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (session [_] )
        (id [_] eeid)
        (sockOut [_] (.getOutputStream soc))
        (sockIn [_] (.getInputStream soc))
        (source [_] co)
        (dispose [_] (closeQ soc)))

      {:typeid ::SocketEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Socket
  [^Service co & [cfg0]]

  (log/info "comp->initialize: %s: %s" (gtid co) (.id co))
  (let
    [{:keys [timeoutMillis
             backlog host port]
      :as cfg}
     (merge (.config co) cfg0)
     ip (if (hgl? host)
          (InetAddress/getByName host)
          (InetAddress/getLocalHost))]
    (test-posnum "socket port" port)
    (let
      [soc (ServerSocket. port
                          (or backlog 100) ip)]
      (log/info "Server socket %s (bound?) %s" soc (.isBound soc))
      (.setReuseAddress soc true)
      (.setv (.getx co) :ssocket soc))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown

  ""
  [^Service co ^Socket soc]

  (try!
    (.dispatch co (ioevent<> co soc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::Socket
  [^Service co & _]

  (log/info "io->start: %s: %s" (gtid co) (.id co))
  (when-some
    [^ServerSocket
     ssoc (.getv (.getx co) :ssocket)]
    (async!
      #(while (.isBound ssoc)
         (try
           (sockItDown co (.accept ssoc))
           (catch Throwable e#
             (log/error e# "")
             (closeQ ssoc)
             (.unsetv (.getx co) :ssocket ))))
      {:cl (getCldr)} ))
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::Socket
  [^Service co & _]

  (log/info "io->stop: %s: %s" (gtid co) (.id co))
  (when-some
    [^ServerSocket
     ssoc (.getv (.getx co) :ssocket) ]
    (closeQ ssoc)
    (.unsetv (.getx co) :ssocket ))
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


