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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evt<>
  ""
  [^IoService co {:keys [^Socket socket]}]

  (let [eeid (str "event#" (seqint2))]
    (log/debug "opened socket: %s" socket)
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
(defn- init
  ""
  [conf cfg0]
  (let
    [{:keys [timeoutMillis
             backlog host port]
      :as cfg}
     (merge conf cfg0)
     ip (if (hgl? host)
          (InetAddress/getByName host)
          (InetAddress/getLocalHost))]
    (test-pos "socket port" port)
    (let
      [soc (ServerSocket. port
                          (int (or backlog 100)) ip)]
      (log/info "Server socket %s (bound?) %s" soc (.isBound soc))
      (.setReuseAddress soc true)
      [cfg soc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown
  ""
  [^IoService co ^Socket soc]
  (try!
    (.dispatch co (evt<> co {:socket soc}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeSocket
  ""
  [^Muble m kee]
  (when-some
    [ssoc (.getv m kee)] (closeQ ssoc) (.unsetv m kee)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Socket
  ""
  ^LifeCycle
  [co {:keys [conf] :as spec}]
  (let
    [see (keyword (juid))
     impl (muble<>)]
    (reify
      LifeCycle
      (init [_ arg]
        (let [[cfg soc]
              (init conf arg)]
          (.copyEx impl cfg)
          (.setv impl see soc)))
      (start [_ _]
        (if-some
          [^ServerSocket
           ssoc (.getv impl see)]
          (async!
            #(while (not (.isClosed ssoc))
               (try
                 (sockItDown co (.accept ssoc))
                 (catch Throwable _ (closeSocket impl see))))
            {:cl (getCldr)})))
      (stop [_]
        (closeSocket impl see)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


