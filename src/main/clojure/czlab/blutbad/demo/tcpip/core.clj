;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.demo.tcpip.core

  (:require [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c]
            [czlab.blutbad.core :as b])

  (:import [java.io
            OutputStream
            DataOutputStream
            DataInputStream
            BufferedInputStream]
           [java.net Socket]
           [java.util Date]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- ^String text-msg "current time=${TS}")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn dclient

  [evt]

  (let [plug (c/parent evt)
        svr (c/parent plug)
        tcp (b/get-plugin svr :sample)
        s (.replace text-msg "${TS}" (str (Date.)))
        {:keys [host port]}
        (:conf tcp)
        bits (.getBytes s "utf-8")]
    (c/prn!! "TCP Client: about to send message= %s" s)
    (with-open [soc (Socket. ^String host (int port))]
      ;(c/prn!! "client soc = %s." soc)
      (let [os (.getOutputStream soc)]
        (-> (DataOutputStream. os)
            (.writeInt (int (alength bits))))
        (doto os (.write bits) .flush))
      (let [is (.getInputStream soc)
            dis (DataInputStream. is)
            clen (.readInt dis)
            bf (BufferedInputStream. is)
            buf (byte-array clen)]
        (.read bf buf)
        (c/prn!! "Client received: %s" (String. buf "utf-8"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn dserver

  [evt]

  (let [dis (DataInputStream. (:in evt))
        clen (.readInt dis)
        ;_ (c/prn!! "clen = %d" clen)
        bf (BufferedInputStream. (:in evt))
        buf (byte-array clen)
        s (String. (c/doto->> buf
                              (.read bf)) "utf-8")
        r (str "confirmed time= " (last (c/split s "current time=")))
        ^OutputStream os (:out evt)
        reply (.getBytes r "utf-8")
        rlen (alength reply)]
    (c/prn!! "Server received: %s" s)
    (-> (DataOutputStream. os)
        (.writeInt (int rlen)))
    (doto os
      (.write reply) .flush)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


