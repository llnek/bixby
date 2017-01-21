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

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.wabbit.sys Cljshim Execvisor]
           [czlab.wabbit.ctl Puglet Pluggable]
           [java.util Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyPlug
  ""
  ^Pluggable
  [^Execvisor co emType]
  (let [emStr (strKW emType)]
    (if (neg? (.indexOf emStr "/"))
      nil
      (-> (.cljrt co)
          (.callEx emStr
                   (vargs* Object co))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pluggable<>
  "Create a Service"
  ^Puglet
  [^Execvisor parObj emType emAlias]
  (let
    [plug (reifyPlug parObj emType)
     {:keys [info conf] :as spec}
     (.spec plug)
     impl (muble<>)
     timer (atom nil)]
    (with-meta
      (reify Puglet
        (getx [_] impl)
        (isEnabled [_]
          (not (false? (:enabled? (.config plug)))))
        (server [this] parObj)
        (config [_] (.config plug))
        (hold [_ trig millis]
          (if (and (some? @timer)
                   (spos? millis))
            (let [k (tmtask<>
                      #(.fire trig nil))]
              (.schedule ^Timer @timer k millis)
              (.setTrigger trig k))))
        (version [_] (str (:version info)))
        (id [_] emAlias)
        (dispose [_]
          (log/info "puglet [%s] is being disposed" emAlias)
          (some-> ^Timer @timer (.cancel))
          (rset! timer)
          (.dispose plug)
          (log/info "puglet [%s] disposed - ok" emAlias))
        (init [this cfg0]
          (log/info "puglet [%s] is initializing..." emAlias)
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
;;EOF


