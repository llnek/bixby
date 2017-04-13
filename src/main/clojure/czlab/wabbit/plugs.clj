;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.plugs

  (:require [czlab.basal.logging :as log])

  (:use [czlab.wabbit.base.core]
        [czlab.wabbit.xpis]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [czlab.jasal
            Disposable
            Versioned
            Config
            Startable
            Idable
            Initable
            Hierarchial
            Triggerable]
           [czlab.basal Cljrt]
           [java.util Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyPlug
  "" [exec emType emAlias]
  (let [^Cljrt clj (cljrt exec)
        emStr (strKW emType)]
    (if-not (neg? (.indexOf emStr "/"))
      (.callEx clj
               emStr
               (vargs* Object exec emAlias)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defstateful PlugletObj
  Versioned
  (version [_] (str (get-in
                          (pluggableSpec (:plug @data))
                          [:info :version])))
  Config
  (config [_] (.config ^Config (:plug @data)))
  Idable
  (id [_] (:emAlias @data))
  Disposable
  (dispose [me]
    (let [{:keys [emAlias plug timer]} @data]
      (log/info "puglet [%s] is being disposed" emAlias)
      (some-> ^Timer timer .cancel)
      ;;(alterStateful me dissoc :timer)
      (.dispose ^Disposable plug)
      (log/info "puglet [%s] disposed - ok" emAlias)))
  Pluglet
  (isEnabled? [me] (!false? (:enabled? (.config me))))
  (plugSpec [_] (pluggableSpec (:plug @data)))
  (getServer [_] (:parent @data))
  (holdEvent [_ trig millis]
    (if-some [t (:timer @data)]
      (if (spos? millis)
        (let [k (tmtask<> #(.fire ^Triggerable trig))]
          (.schedule ^Timer t k ^long millis)
          (.setTrigger ^Triggerable trig k)))))
  Initable
  (init [me cfg0]
    (let [{:keys [plug emAlias]} @data]
      (log/info "puglet [%s] is initializing..." emAlias)
      (.setParent ^Hierarchial plug me)
      (.init ^Initable plug cfg0)
      (log/info "puglet [%s] init'ed - ok" emAlias)))
  Startable
  (start [me arg]
    (let [{:keys [plug emAlias]} @data]
      (log/info "puglet [%s] is starting..." emAlias)
      (alterStateful me
                     assoc
                     :timer (Timer. true))
      (.start ^Startable plug arg)
      (log/info "puglet [%s] config:" emAlias)
      (log/info "%s" (pr-str (.config me)))
      (log/info "puglet [%s] started - ok" emAlias)))
  (stop [_]
    (let [{:keys [plug timer emAlias]} @data]
      (log/info "puglet [%s] is stopping..." emAlias)
      (some-> ^Timer timer .cancel)
      (.stop ^Startable plug)
      (log/info "puglet [%s] stopped - ok" emAlias)))
  Object
  (toString [me]
    (str (strKW  (get-in (plugSpec me)
                         [:conf :$pluggable])) "#" (id?? me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pluglet<>
  "Create a Service"
  ^czlab.wabbit.xpis.Pluglet
  [parObj plug emAlias]
  (entity<> PlugletObj
            {:parent parObj
             :plug plug
             :emAlias emAlias}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn plugletViaType<>
  "Create a Service"
  ^czlab.wabbit.xpis.Pluglet
  [exec emType emAlias]

  (let [u (reifyPlug exec emType emAlias)]
    (cond
      (satisfies? Pluggable u)
      (pluglet<> exec u emAlias)
      (satisfies? Pluglet u)
      u
      :else
      (throw
        (ClassCastException.
          "Must be Pluggable or Pluglet")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

