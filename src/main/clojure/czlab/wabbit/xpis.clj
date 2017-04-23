;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.xpis

  (:require [czlab.basal.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.basal.core]
        [czlab.basal.str])

  (:import [javax.management ObjectName]
           [czlab.jasal Schedulable]
           [czlab.basal Cljrt]
           [java.io File]
           [java.util Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol SqlAccess
  ""
  (acquire-db-pool [_ id] "")
  (acquire-db-api [_ id] "")
  (dft-db-pool [_] "")
  (dft-db-api [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol KeyAccess
  ""
  (^chars pkey-chars [_] "")
  (^bytes pkey-bytes [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PlugMsg ;;extends Idable, Disposable {
  ""
  (msg-source [_] "")
  (is-stale? [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Pluglet ;;extends Component, LifeCycle, Config {
  ""
  (hold-event [_ ^Triggerable t ^long millis] "")
  (plug-spec [_] "")
  (is-enabled? [_] "")
  (get-server [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol JmxPluglet ;;extends Pluglet, Resetable {
  ""
  (^ObjectName jmx-reg [_ obj ^String domain ^String nname paths] "")
  (jmx-dereg [_ ^ObjectName nname] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Execvisor ;;extends Component ,Config ,LifeCycle ,SqlAccess ,KeyAccess {
  ""
  (has-child? [_ id] "")
  (get-child [_ id] "")
  (^long uptime-in-millis [_] "")
  (^Locale get-locale [_] "")
  (^long get-start-time [_] "")
  (kill9! [_] "")
  (^Cljrt cljrt [_] "")
  (^Schedulable scheduler [_] "")
  (^File get-home-dir [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


