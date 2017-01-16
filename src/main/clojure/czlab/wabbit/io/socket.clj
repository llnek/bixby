;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for TCP socket service."
      :author "Kenneth Leung"}

  czlab.wabbit.io.socket

  (:require [czlab.xlib.process :refer [async!]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.io :refer [closeQ]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.io.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.base.core])

  (:import [java.net InetAddress ServerSocket Socket]
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

  (log/debug "opened socket: %s" socket)
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
(defn- finxSocket
  ""
  [^IoService co]

  (when-some
    [ssoc (.getv (.getx co) :ssocket)]
    (closeQ ssoc)
    (.unsetv (.getx co) :ssocket)))

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
      #(while (not (.isClosed ssoc))
         (try
           (sockItDown co (.accept ssoc))
           (catch Throwable _ (finxSocket co))))
      {:cl (getCldr)}))
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop
  ::Socket
  [^IoService co]
  (logcomp "io->stop" co)
  (finxSocket co)
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


