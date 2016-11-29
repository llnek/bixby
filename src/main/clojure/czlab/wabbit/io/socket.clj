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

  czlab.wabbit.io.socket

  (:require
    [czlab.xlib.process :refer [async!]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.io :refer [closeQ]]
    [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.io.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.sys.core])

  (:import
    [java.net InetAddress ServerSocket Socket]
    [czlab.xlib Muble Identifiable]
    [czlab.wabbit.io IoService SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(derive ::Socket :czlab.wabbit.io.core/Service)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::Socket
  [^IoService co {:keys [^Socket socket]}]

  (let [eeid (str "event#" (seqint2))
        impl (muble<>)]
    (with-meta
      (reify SocketEvent

        (checkAuthenticity [_] false)
        (id [_] eeid)
        (sockOut [_] (.getOutputStream socket))
        (sockIn [_] (.getInputStream socket))
        (source [_] co)
        (dispose [_] (closeQ socket)))

      {:typeid ::SocketEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init

  ::Socket
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (let
    [{:keys [timeoutMillis
             backlog host port]
      :as cfg}
     (merge (.config co) cfg0)
     ip (if (hgl? host)
          (InetAddress/getByName host)
          (InetAddress/getLocalHost))]
    (test-pos "socket port" port)
    (let
      [soc (ServerSocket. port
                          (int (or backlog 100)) ip)]
      (log/info "Server socket %s (bound?) %s" soc (.isBound soc))
      (.setReuseAddress soc true)
      (.setv (.getx co) :ssocket soc))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown

  ""
  [^IoService co ^Socket soc]

  (try!
    (.dispatch co (ioevent<> co {:socket soc}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::Socket
  [^IoService co]

  (logcomp "io->start" co)
  (if-some
    [^ServerSocket
     ssoc (.getv (.getx co) :ssocket)]
    (async!
      #(while (.isBound ssoc)
         (try
           (sockItDown co (.accept ssoc))
           (catch Throwable e#
             (log/exception e#)
             (closeQ ssoc)
             (.unsetv (.getx co) :ssocket))))
      {:cl (getCldr)}))
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::Socket
  [^IoService co]

  (logcomp "io->stop" co)
  (when-some
    [^ServerSocket
     ssoc (.getv (.getx co) :ssocket)]
    (closeQ ssoc)
    (.unsetv (.getx co) :ssocket))
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


