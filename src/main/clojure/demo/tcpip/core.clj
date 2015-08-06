;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^:no-doc
    ^{:author "kenl"}

  demo.tcpip.core

  (:require [czlab.xlib.util.logging :as log])

  (:require
    [czlab.xlib.util.process :refer [DelayExec]]
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


