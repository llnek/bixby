;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.plugs.socket

  "Implementation for TCP socket service."

  (:require [czlab.basal.proc :as p]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.log :as l]
            [czlab.basal.core :as c]
            [czlab.basal.xpis :as po]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.wabbit.plugs.core :as pc])

  (:import [java.net InetAddress ServerSocket Socket]
           [clojure.lang APersistentMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TcpConnectMsg []
  po/Idable
  (id [me] (:id me))
  xp/PlugletMsg
  (get-pluglet [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [plug ^Socket socket]

  (c/object<> TcpConnectMsg
              :socket socket
              :source plug
              :sockin (.getInputStream socket)
              :sockout (.getOutputStream socket)
              :id (str "TcpConnectMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sock-it

  [plug soc]
  (c/try! (l/debug "opened soc: %s." soc)
          (pc/dispatch! (evt<> plug soc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ssoc<>

  ^ServerSocket
  [{:keys [timeoutMillis backlog host port]}]

  (let [ip (if (c/nichts? host)
             (InetAddress/getLocalHost)
             (InetAddress/getByName host))]
    (assert (c/spos? port)
            (str "Bad socket port: " port))
    (c/do-with
      [soc (ServerSocket. port
                          (int (c/num?? backlog 100)) ip)]
      (.setReuseAddress soc true)
      (l/info "Server socket %s (bound?) %s" soc (.isBound soc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def SocketIOSpec
  {:conf {:$pluggable ::socket<>
          :host ""
          :port 7551
          :$handler nil}
   :info {:version "1.0.0"
          :name "TCP Socket Server"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet

  [server _id spec]

  (let [impl (atom {:ssoc nil
                    :info (:info spec)
                    :conf (:conf spec)})]
    (reify
      xp/Pluglet
      (user-handler [_]
        (get-in @impl [:conf :$handler]))
      (gconf [_] (:conf @impl))
      (err-handler [_]
        (get-in @impl [:conf :$error]))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Startable
      (start [me]
        (po/start me nil))
      (start [me _]
        (let [ssoc (ssoc<> (:conf @impl))]
          (swap! impl assoc :soc ssoc)
          (p/async!
            #(while (and ssoc
                         (not (.isClosed ssoc)))
               (try (sock-it me (.accept ssoc))
                    (catch Throwable t
                      (when-not
                        (->> ["closed" "socket"]
                             (c/hasic-all? (u/emsg t)))
                        (l/warn t "")))))) me))
      (stop [me]
        (i/klose (:soc @impl))
        (swap! impl assoc :soc nil) me)
      po/Finzable
      (finz [me] (po/stop me) me)
      po/Initable
      (init [me arg]
        (swap! impl
               update-in
               [:conf]
               #(-> (merge % arg)
                    b/expand-vars* b/prevar-cfg)) me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn socket<>

  ([_ id spec]
   (pluglet _ id spec))
  ([_ id]
   (socket<> _ id SocketIOSpec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


