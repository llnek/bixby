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

  (:import [czlab.wabbit.server Cljshim Container]
           [czlab.wabbit.ctl Service]
           [java.util Timer TimerTask]
           [czlab.wabbit.ext Pluggable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pluggable<>
  "Create a Service"
  ^Service
  [^Container parObj
   emType
   emAlias
   {:keys [info conf] :as spec}]
  (let [timer (atom nil)
        impl (muble<>)
        plug (atom nil)]
    (with-meta
      (reify Service
        (setParent [_ p] (throwUOE "can't setParent"))
        (getx [_] impl)
        (isEnabled [_]
          (not (false? (:enabled? (.config ^Pluggable @plug)))))
        (server [this] (.parent this))
        (config [_] (.config ^Pluggable @plug))
        (hold [_ trig millis]
          (if (and (some? @timer)
                   (spos? millis))
            (let [k (tmtask<>
                      #(.fire trig nil))]
              (.schedule ^Timer @timer k millis)
              (.setTrigger trig k))))
        (version [_] (str (:version info)))
        (id [_] emAlias)
        (parent [_] parObj)
        (dispose [_]
          (log/info "service [%s] is being disposed" emAlias)
          (some-> ^Timer @timer (.cancel))
          (rset! timer)
          (.dispose ^Pluggable @plug)
          (log/info "service [%s] disposed - ok" emAlias))
        (init [this cfg0]
          (log/info "service [%s] is initializing..." emAlias)
          (let [c (-> (.cljrt parObj)
                      (.callEx (strKW emType)
                               (vargs* Object this spec)))]
            (rset! plug c)
            (.init ^Pluggable c cfg0))
          (log/info "service [%s] init'ed - ok" emAlias))
        (start [this arg]
          (log/info "service [%s] is starting..." emAlias)
          (rset! timer (Timer. true))
          (.start ^Pluggable @plug arg)
          (log/info "service [%s] config:" emAlias)
          (log/info "%s" (pr-str (.config this)))
          (log/info "service [%s] started - ok" emAlias))
        (stop [_]
          (log/info "service [%s] is stopping..." emAlias)
          (some-> ^Timer @timer (.cancel))
          (rset! timer)
          (.stop ^Pluggable @plug)
          (log/info "service [%s] stopped - ok" emAlias)))

      {:typeid emType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


