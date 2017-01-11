;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

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


