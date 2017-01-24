;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.ctl.core

  (:require [czlab.basal.logging :as log])

  (:use [czlab.wabbit.base.core]
        [czlab.basal.consts]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [czlab.wabbit.ctl Pluglet Pluggable]
           [czlab.wabbit.sys Execvisor]
           [czlab.wabbit.base Cljshim]
           [java.util Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyPlug
  ""
  ^Pluggable
  [^Cljshim clj emType]
  (let [emStr (strKW emType)]
    (if (neg? (.indexOf emStr "/"))
      nil
      (.call clj emStr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pluglet<>
  "Create a Service"
  ^Pluglet
  [^Execvisor parObj ^Pluggable plug emAlias]
  (let [emType (get-in (.spec plug)
                       [:conf :$pluggable])
        impl (muble<>)
        timer (atom nil)]
    (with-meta
      (reify Pluglet
        (isEnabled [this] (not (false?
                                 (:enabled? (.config this)))))
        (version [_] (str (get-in
                            (.spec plug)
                            [:info :version])))
        (config [_] (.config plug))
        (spec [_] (.spec plug))
        (server [this] parObj)
        (getx [_] impl)
        (hold [_ trig millis]
          (if (and (some? @timer)
                   (spos? millis))
            (let [k (tmtask<>
                      #(.fire trig nil))]
              (.schedule ^Timer @timer k millis)
              (.setTrigger trig k))))
        (id [_] emAlias)
        (dispose [_]
          (log/info "puglet [%s] is being disposed" emAlias)
          (some-> ^Timer @timer (.cancel))
          (rset! timer)
          (.dispose plug)
          (log/info "puglet [%s] disposed - ok" emAlias))
        (init [this cfg0]
          (log/info "puglet [%s] is initializing..." emAlias)
          (.setParent plug this)
          (.init plug cfg0)
          (log/info "puglet [%s] init'ed - ok" emAlias))
        (start [this arg]
          (log/info "puglet [%s] is starting..." emAlias)
          (rset! timer (Timer. true))
          (.start plug arg)
          (log/info "puglet [%s] config:" emAlias)
          (log/info "%s" (pr-str (.config this)))
          (log/info "puglet [%s] started - ok" emAlias))
        (stop [_]
          (log/info "puglet [%s] is stopping..." emAlias)
          (some-> ^Timer @timer (.cancel))
          (rset! timer)
          (.stop plug)
          (log/info "puglet [%s] stopped - ok" emAlias)))
      {:typeid emType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn plugletViaType<>
  "Create a Service"
  ^Pluglet
  [^Execvisor parObj emType emAlias]
  (pluglet<> parObj
             (reifyPlug (.cljrt parObj) emType) emAlias))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


