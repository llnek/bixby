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

  (:require [czlab.xlib.process :refer [delayExec]]
            [czlab.xlib.logging :as log])

  (:use [czlab.flux.wflow.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [java.io DataOutputStream DataInputStream BufferedInputStream]
           [czlab.flux.wflow Job TaskDef WorkStream]
           [czlab.wabbit.io SocketEvent]
           [java.net Socket]
           [java.util Date]
           [czlab.xlib Muble]
           [czlab.wabbit.server Container ServiceProvider Service]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^String text-msg "Hello World, time is ${TS} !")

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
      #(let
         [^Job job %2
          tcp (-> ^Container
                  (.server job)
                  (.service :default-sample))
          s (.replace text-msg "${TS}" (str (Date.)))
          ^String host (.getv (.getx tcp) :host)
          bits (.getBytes s "utf-8")
          port (.getv (.getx tcp) :port)]
         (println "TCP Client: about to send message" s)
         (with-open [soc (Socket. host (int port))]
           (let [os (.getOutputStream soc)]
             (-> (DataOutputStream. os)
                 (.writeInt (int (alength bits))))
             (doto os
               (.write bits)
               (.flush))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demoServer
  ""
  ^WorkStream
  []
  (workStream<>
    (script<>
      #(let
         [^Job job %2
          ^SocketEvent ev (.event job)
          dis (DataInputStream. (.sockIn ev))
          clen (.readInt dis)
          bf (BufferedInputStream. (.sockIn ev))
          buf (byte-array clen)]
         (.read bf buf)
         (.setv job :cmsg (String. buf "utf-8"))
         ;; add a delay into the workflow before next step
         (postpone<> 1.5)))
    (script<>
      #(println "Socket Server Received: "
                (.getv ^Job %2 :cmsg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


