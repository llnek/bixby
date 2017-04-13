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
  (acquireDbPool [_ id] "")
  (acquireDbAPI [_ id] "")
  (dftDbPool [_] "")
  (dftDbAPI [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol KeyAccess
  ""
  (^chars pkeyChars [_] "")
  (^bytes pkeyBytes [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PlugMsg ;;extends Idable, Disposable {
  ""
  (msgSource [_] "")
  (isStale? [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Pluggable ;;extends LifeCycle, Hierarchial, Config {
  ""
  (pluggableSpec [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Pluglet ;;extends Component, LifeCycle, Config {
  ""
  (holdEvent [_ ^Triggerable t ^long millis] "")
  (plugSpec [_] "")
  (isEnabled? [_] "")
  (getServer [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol JmxPluglet ;;extends Pluglet, Resetable {
  ""
  (^ObjectName jmxReg [_ obj ^String domain ^String nname paths] "")
  (jmxDereg [_ ^ObjectName nname] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Execvisor ;;extends Component ,Config ,LifeCycle ,SqlAccess ,KeyAccess {
  ""
  (hasChild? [_ id] "")
  (getChild [_ id] "")
  (^long uptimeInMillis [_] "")
  (^Locale getLocale [_] "")
  (^long getStartTime [_] "")
  (kill9 [_] "")
  (^Cljrt cljrt [_] "")
  (^Schedulable scheduler [_] "")
  (^File getHomeDir [_] ""))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


