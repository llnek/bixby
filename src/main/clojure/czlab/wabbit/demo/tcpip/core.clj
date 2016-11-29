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
    ^{:author "Kenneth Leung"}

  czlab.wabbit.demo.tcpip.core


  (:require
    [czlab.xlib.process :refer [delayExec]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core :refer [try!]])

  (:use [czlab.wflow.core])

  (:import
    [java.io DataOutputStream DataInputStream BufferedInputStream]
    [czlab.wflow Job TaskDef WorkStream]
    [czlab.wabbit.io SocketEvent]
    [java.net Socket]
    [java.util Date]
    [czlab.xlib Muble]
    [czlab.wabbit.server Container ServiceProvider Service]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^String TEXTMsg "Hello World, time is ${TS} !")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demoClient

  ""
  ^WorkStream
  []

  ;; wait, then opens a socket and write something to server process.
  (workStream<>
    (postpone<> 3)
    (script<>
      (fn [_ ^Job j]
        (let
          [tcp (-> ^Container
                   (.server j)
                   (.service :default-sample))
           s (.replace TEXTMsg "${TS}" (.toString (Date.)))
           ^String host (.getv (.getx tcp) :host)
           bits (.getBytes ^String s "utf-8")
           port (.getv (.getx tcp) :port)]
          (println "TCP Client: about to send message" s)
          (with-open [soc (Socket. host (int port))]
            (let [os (.getOutputStream soc)]
              (-> (DataOutputStream. os)
                  (.writeInt (int (alength bits))))
              (doto os
                (.write bits)
                (.flush)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demoServer

  ""
  ^WorkStream
  []

  (workStream<>
    (script<>
      #(let [^SocketEvent ev (.event ^Job %2)
             dis (DataInputStream. (.sockIn ev))
             clen (.readInt dis)
             bf (BufferedInputStream. (.sockIn ev))
             ^bytes buf (byte-array clen) ]
         (.read bf buf)
         (.setv ^Job %2 "cmsg" (String. buf "utf-8"))
         ;; add a delay into the workflow before next step
         (postpone<> 1.5)))
    (script<>
      (fn [_ ^Job j]
        (println "Socket Server Received: "
                 (.getv j "cmsg"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


