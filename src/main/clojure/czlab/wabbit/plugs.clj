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
(decl-mutable PlugletObj
  Versioned
  (version [me] (str (get-in @me [:pspec :info :version])))
  Config
  (config [me] (:conf @me))
  Idable
  (id [me] (:emAlias @me))
  Disposable
  (dispose [me]
    (let [{:keys [emAlias timer]} @me]
      (log/info "puglet [%s] is being disposed" emAlias)
      (some-> ^Timer timer .cancel)
      (log/info "puglet [%s] disposed - ok" emAlias)))
  Pluglet
  (is-enabled? [me] (!false? (get-in @me [:conf :enabled?])))
  (plug-spec [me] (:pspec @me))
  (get-server [me] (:parent @me))
  (hold-event [me ^Triggerable trig ^long millis]
    (if-some [t (:timer @me)]
      (if (spos? millis)
        (let [k (tmtask<> #(.fire trig))]
          (.schedule ^Timer t k millis)
          (.setTrigger trig k)))))
  Initable
  (init [me cfg0]
    (let [{:keys [plug emAlias]} @me]
      (log/info "puglet [%s] is initializing..." emAlias)
      (.init plug cfg0)
      (log/info "puglet [%s] init'ed - ok" emAlias)))
  Startable
  (start [me arg]
    (let [{:keys [plug emAlias]} @me]
      (log/info "puglet [%s] is starting..." emAlias)
      (.start ^Startable plug arg)
      (log/info "puglet [%s] config:" emAlias)
      (log/info "%s" (pr-str (.config me)))
      (log/info "puglet [%s] started - ok" emAlias)))
  (stop [me]
    (let [{:keys [plug timer emAlias]} @me]
      (log/info "puglet [%s] is stopping..." emAlias)
      (some-> ^Timer timer .cancel)
      (.stop ^Startable plug)
      (log/info "puglet [%s] stopped - ok" emAlias)))
  Object
  (toString [me]
    (str (strKW  (get-in @me
                         [:conf :$pluggable])) "#" (id?? me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private
  pluglet-vtbl
  (defvtbl*
    :version (fn [me]
               (str (get-in @me [:pspec :info  :version])))
    :config (fn [me] (:conf @me))
    :id (fn [me] (:emAlias @me))
    :dispose (fn [me]
               (let [{:keys [emAlias timer]} @me]
                 (log/info "puglet [%s] is being disposed" emAlias)
                 (some-> ^Timer timer .cancel)
                 (log/info "puglet [%s] disposed - ok" emAlias)))
    :isEnabled? (fn [me] (!false? (get-in @me [:conf :enabled?])))
    :plugSpec (fn [me] (:pspec @me))
    :getServer (fn [me] (:parent @me))
    :holdEvent (fn [me ^Triggerable trig ^long millis]
                 (if-some [t (:timer @me)]
                   (if (spos? millis)
                     (let [k (tmtask<> #(.fire trig))]
                       (.schedule ^Timer t k millis)
                       (.setTrigger trig k)))))
    :init (fn [me cfg0]
            (let [{:keys [plug emAlias]} @me]
              (log/info "puglet [%s] is initializing..." emAlias)
              (.init plug cfg0)
              (log/info "puglet [%s] init'ed - ok" emAlias)))
    :start (fn [me arg]
             (let [{:keys [plug emAlias]} @me]
               (log/info "puglet [%s] is starting..." emAlias)
               (.start ^Startable plug arg)
               (log/info "puglet [%s] config:" emAlias)
               (log/info "%s" (pr-str (.config me)))
               (log/info "puglet [%s] started - ok" emAlias)))
    :stop (fn [me]
            (let [{:keys [plug timer emAlias]} @me]
              (log/info "puglet [%s] is stopping..." emAlias)
              (some-> ^Timer timer .cancel)
              (.stop ^Startable plug)
              (log/info "puglet [%s] stopped - ok" emAlias)))
    :toString (fn [me]
                (str (strKW  (get-in @me
                         [:conf :$pluggable])) "#" (id?? me)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-object PlugletObj
  Versioned
  (version [me]
    (some-> (rvtbl (:vtbl @me) :pspec) :info :version))
  Config
  (config [me] (.getv ^Muble (:impl @me) :conf))
  Idable
  (id [me] (:emAlias @me))
  Disposable
  (dispose [me]
    (let [{:keys [vtbl emAlias timer]} @me]
      (log/info "puglet [%s] is being disposed" emAlias)
      (some-> ^Timer timer .cancel)
      (rvtbl vtbl :dispose me)
      (log/info "puglet [%s] disposed - ok" emAlias)))
  Pluglet
  (isEnabled? [me] (!false? (:enabled? (.config me))))
  (getServer [me] (:parent @me))
  (plugSpec [me] (rvtbl (:vtbl @me) :pspec))
  (holdEvent [me trig millis]
    (let [^Timer t (:timer @me)]
      (if (and t (spos? millis))
        (let [^Triggerable trig tirg
              k (tmtask<> #(.fire trig))]
          (.schedule t k ^long millis)
          (.setTrigger trig k)))))
  Initable
  (init [me arg]
    (let [{:keys [impl vtbl emAlias]} @me
          c (:conf (rvtbl vtbl :pspec))]
      (log/info "puglet [%s] is initializing..." emAlias)
      (->>
        (if (cvtbl? vtbl :init)
          (rvtbl vtbl :init me arg)
          (prevarCfg (merge c arg)))
        (.setv impl :conf))
      (log/info "puglet [%s] init'ed - ok" emAlias)))
  Startable
  (start [me arg]
    (let [{:keys [vtbl emAlias]} @me]
      (log/info "puglet [%s] is starting..." emAlias)
      (rvtbl vtbl :start me arg)
      (log/info "puglet [%s] config:" emAlias)
      (log/info "%s" (pr-str (.config me)))
      (log/info "puglet [%s] started - ok" emAlias)))
  (stop [me]
    (let [{:keys [vtbl timer emAlias]} @me]
      (log/info "puglet [%s] is stopping..." emAlias)
      (some-> ^Timer timer .cancel)
      (rvtbl vtbl :stop me)
      (log/info "puglet [%s] stopped - ok" emAlias)))
  Object
  (toString [me]
    (str (strKW  (:$pluggable (.config me))) "#" (id?? me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn plugletVar "" [plug kee] (-> (:impl @plug) (.getv kee)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn alterPluglet+
  "" [plug kee v] (-> (:impl @plug) (.setv kee v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn alterPluglet-
  "" [plug kee] (-> (:impl @plug) (.unsetv kee)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pluglet<>
  "Create a Service"
  ^czlab.wabbit.xpis.Pluglet
  [parObj plug emAlias]
  (object<> PlugletObj
            {:timer (Timer. true)
             :impl (muble<>)
             :parent parObj
             :vtbl plug
             :emAlias emAlias}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn plugletViaType<>
  "Create a Service"
  ^czlab.wabbit.xpis.Pluglet
  [exec emType emAlias]

  (let [u (reifyPlug exec emType emAlias)]
    (cond
      (satisfies? Pluglet u)
      u
      (map? u)
      (pluglet<> exec u emAlias)
      :else
      (throw
        (ClassCastException.
          "Must be Pluggable or Pluglet")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

