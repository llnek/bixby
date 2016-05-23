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


(ns ^:no-doc
    ^{:author "kenl"}

  demo.tcpip.core


  (:require
    [czlab.xlib.util.process :refer [DelayExec]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.core :refer [try!]]
    [czlab.xlib.util.wfs :refer [SimPTask]])

  (:import
    [java.io DataOutputStream DataInputStream BufferedInputStream]
    [com.zotohlab.wflow WorkFlow Job FlowDot PTask Delay]
    [com.zotohlab.skaro.io SocketEvent]
    [com.zotohlab.skaro.core Muble Container]
    [java.net Socket]
    [java.util Date]
    [com.zotohlab.frwk.server ServiceProvider Service]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^String TEXTMsg "Hello World, time is ${TS} !")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DemoClient ""

  ^WorkFlow
  []

  (reify WorkFlow
    (startWith [_]
      ;; wait, then opens a socket and write something to server process.
      (-> (Delay/apply 3000)
          (.chain
            (SimPTask
              (fn [^Job j]
                (with-local-vars
                  [tcp (-> ^ServiceProvider
                           (.container j)
                           (.getService :default-sample))
                   s (.replace TEXTMsg "${TS}" (.toString (Date.)))
                   ssoc nil]
                  (println "TCP Client: about to send message" @s)
                  (try
                    (let [^String host (.getv ^Muble @tcp :host)
                          bits (.getBytes ^String @s "utf-8")
                          port (.getv ^Muble @tcp :port)
                          soc (Socket. host (int port))
                          os (.getOutputStream soc) ]
                      (var-set ssoc soc)
                      (-> (DataOutputStream. os)
                          (.writeInt (int (alength bits))))
                      (doto os
                        (.write bits)
                        (.flush)))
                    (finally
                      (try! (when (some? @ssoc)
                              (.close ^Socket @ssoc)))))
                  nil))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DemoServer ""

  ^WorkFlow
  []

  (reify WorkFlow
    (startWith [_]
      (-> (SimPTask
            (fn [^Job j]
              (let [^SocketEvent ev (.event j)
                    dis (DataInputStream. (.getSockIn ev))
                    clen (.readInt dis)
                    bf (BufferedInputStream. (.getSockIn ev))
                    ^bytes buf (byte-array clen) ]
                (.read bf buf)
                (.setv j "cmsg" (String. buf "utf-8"))
                ;; add a delay into the workflow before next step
                (Delay/apply 1500))))
          (.chain
            (SimPTask
              (fn [^Job j]
                (println "Socket Server Received: "
                         (.getv j "cmsg")))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

