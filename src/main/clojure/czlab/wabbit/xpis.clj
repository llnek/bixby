;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.xpis

  (:require [czlab.basal.log :as l]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.basal.str :as s]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c]
            [czlab.basal.cljrt :as rt]
            [czlab.basal.proto :as po])

  (:import [javax.management ObjectName]
           [java.io File]
           [java.util Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol SqlAccess
  ""
  (dft-db-pool [_] "")
  (dft-db-api [_] "")
  (acquire-db-api [_ id] "")
  (acquire-db-pool [_ id] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol KeyAccess
  ""
  (^chars pkey-chars [_] "")
  (^bytes pkey-bytes [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol PlugletMsg
  ""
  (get-pluglet [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol JmxPluglet
  ""
  (^ObjectName jmx-reg [_ obj ^String domain ^String nname paths] "")
  (jmx-dereg [_ ^ObjectName nname] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Execvisor
  ""
  (has-child? [_ id] "")
  (get-child [_ id] "")
  (get-locale [_] "")
  (get-start-time [_] "")
  (kill9! [_] "")
  (cljrt [_] "")
  (get-scheduler [_] "")
  (get-home-dir [_] "")
  (uptime-in-millis [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Pluglet
  ""
  (get-conf [_] "")
  (err-handler [_] "")
  (user-handler [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- reify-plug
  [exec emType emAlias]
  (if (var? emType)
    (@emType exec emAlias)
    (let [clj (cljrt exec)
          emStr (s/kw->str emType)]
      (if (cs/index-of emStr "/")
        (rt/call* clj
                  emStr
                  (c/vargs* Object exec emAlias))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pluglet-via-type<>
  "Create a Service."
  [exec emType emAlias]
  (if-some [u (reify-plug exec emType emAlias)]
    nil
    (throw (ClassCastException. "Must be Pluglet."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-server
  "" [plug] (po/parent plug))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


