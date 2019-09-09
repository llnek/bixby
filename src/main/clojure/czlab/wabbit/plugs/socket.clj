;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for TCP socket service."
      :author "Kenneth Leung"}

  czlab.wabbit.plugs.socket

  (:require [czlab.basal.proc :as p]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.log :as l]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s]
            [czlab.wabbit.core :as b]
            [czlab.basal.proto :as po]
            [czlab.wabbit.plugs.core :as pc])

  (:import [java.net InetAddress ServerSocket Socket]
           [clojure.lang APersistentMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TcpMsg []
  xp/PlugletMsg
  (get-pluglet [me] (:source me))
  po/Idable
  (id [me] (:id me))
  po/Finzable
  (finz [me] (i/klose (:socket me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>
  [co ^Socket socket]
  (c/object<> TcpMsg
              :id (str "TcpMsg#" (u/seqint2))
              :socket socket
              :source co
              :sockin (.getInputStream socket)
              :sockout (.getOutputStream socket)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sock-it
  [co soc]
  (c/try! (l/debug "opened socket: %s." soc)
          (pc/dispatch! (evt<> co soc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ssoc<>
  ^ServerSocket
  [{:keys [timeoutMillis backlog host port]}]
  (let [ip (if (s/nichts? host)
             (InetAddress/getLocalHost)
             (InetAddress/getByName host))]
    (c/test-pos "socket port" port)
    (c/do-with
      [soc (ServerSocket. port
                          (int (or backlog 100)) ip)]
      (.setReuseAddress soc true)
      (l/info "Server socket %s (bound?) %s" soc (.isBound soc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def SocketIOSpec {:conf {:$pluggable ::socket<>
                          :host ""
                          :port 7551
                          :handler nil}
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
      (user-handler [_] (get-in @impl [:conf :handler]))
      (get-conf [_] (:conf @impl))
      (err-handler [_]
        (or (get-in @impl
                    [:conf :error]) (:error spec)))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Startable
      (start [me]
        (po/start me nil))
      (start [me _]
        (when-some [s (ssoc<> (:conf @impl))]
          (swap! impl #(assoc % :soc s))
          (p/async!
            #(loop []
               (when-not (.isClosed s)
                 (try (sock-it me (.accept s))
                      (catch Throwable t
                        (let [m (s/lcase (.getMessage t))]
                          (if-not (and (s/embeds? m "closed")
                                       (s/embeds? m "socket"))
                            (l/warn t "")))))
                 (recur))))))
      (stop [me]
        (i/klose (:soc @impl))
        (swap! impl #(assoc % :soc nil)))
      po/Finzable
      (finz [me] (po/stop me))
      po/Initable
      (init [_ arg]
        (swap! impl
               (c/fn_1 (update-in ____1
                                  [:conf]
                                  #(b/prevar-cfg (merge % arg)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn socket<>
  ""
  ([_ id]
   (socket<> _ id SocketIOSpec))
  ([_ id spec]
   (pluglet _ id (update-in spec
                            [:conf]
                            b/expand-vars-in-form))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


