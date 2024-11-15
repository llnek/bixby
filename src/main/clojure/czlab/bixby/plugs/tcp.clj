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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns czlab.bixby.plugs.tcp

  "Implementation for TCP socket service."

  (:require [czlab.basal.proc :as p]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.bixby.core :as b])

  (:import [java.net InetAddress ServerSocket Socket]
           [clojure.lang APersistentMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TcpConnectMsg []
  c/Idable
  (id [me] (:id me))
  c/Hierarchical
  (parent [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [plug ^Socket socket]

  (c/object<> TcpConnectMsg
              :socket socket
              :source plug
              :in (.getInputStream socket)
              :out (.getOutputStream socket)
              :id (str "TcpConnectMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sock-it

  [plug soc]

  (c/try!
    (c/debug "opened soc: %s." soc)
    (b/dispatch (evt<> plug soc))))

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
      (c/info "Server socket %s (bound?) %s" soc (.isBound soc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TCPPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Startable
  (start [me]
    (c/start me nil))
  (start [me _]
    (let [ssoc (ssoc<> conf)]
      (p/async!
        #(while (and ssoc
                     (not (.isClosed ssoc)))
           (try (sock-it me (.accept ssoc))
                (catch Throwable t
                  (if-not (c/hasic-all?
                            (u/emsg t)
                            ["closed" "socket"])
                    (c/warn t "socket error"))))) {:daemon? true})
      (assoc me :soc ssoc)))
  (stop [me]
    (i/klose (:soc me))
    (assoc me :soc nil))
  c/Finzable
  (finz [me] (c/stop me))
  c/Initable
  (init [me arg]
    (update-in me
               [:conf]
               #(-> (c/merge+ % arg)
                    b/expand-vars* b/prevar-cfg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def
  ^{:doc ""}

  TCPSpec

  {:conf {:$pluggable ::socket<>
          :host ""
          :port 7551
          :$error nil
          :$action nil}
   :info {:version "1.0.0"
          :name "TCP Socket Server"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn socket<>

  "Create a TCP Socket Plugin."
  {:arglists '([server id]
               [server id spec])}

  ([_ id]
   (socket<> _ id TCPSpec))

  ([ctr id {:keys [info conf]}]
   (TCPPlugin. ctr id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

