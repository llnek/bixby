;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.xpis

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.log :as l]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c]
            [czlab.basal.xpis :as po])

  (:import [java.io File]
           [java.util Locale]
           [javax.management ObjectName]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol PlugletMsg
  (get-pluglet [_] "Get reference to the pluglet."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol JmxPluglet
  (jmx-dereg [_ ^ObjectName nname] "")
  (^ObjectName jmx-reg [_ obj ^String domain ^String nname paths] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Execvisor
  (has-plugin? [_ id] "")
  (get-plugin [_ id] "")
  (get-locale [_] "")
  (get-start-time [_] "")
  (pkey-chars [_] "")
  (pkey-bytes [_] "")
  (kill9! [_] "")
  (cljrt [_] "")
  (get-scheduler [_] "")
  (get-home-dir [_] "")
  (uptime-in-millis [_] "")
  (acquire-dbapi?? [_] [_ id] "")
  (acquire-dbpool?? [_] [_ id] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Pluglet
  (gconf [_] "")
  (err-handler [_] "")
  (user-handler [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pluglet<>

  "Create a Service."
  [exec emAlias emType]

  (or (if (var? emType)
        (@emType exec emAlias)
        (let [emStr (c/kw->str emType)]
          (if (cs/index-of emStr "/")
            (u/call* (cljrt exec)
                     emStr
                     (c/vargs* Object exec emAlias)))))
      (c/trap! ClassCastException (c/fmt "Not pluglet: %s." emType))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-pod-key

  "Get the application's private key."
  ^bytes [evt]

  (-> evt get-pluglet po/parent pkey-bytes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


