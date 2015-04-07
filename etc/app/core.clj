;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns  ^{:doc ""
       :author "@@USER@@" }

  @@APPDOMAIN@@.core

  (:require [clojure.tools.logging :as log :only (info warn error debug)])
  (:use [czlabclj.tardis.core.wfs])
  (:import ( com.zotohlab.wflow FlowNode Activity
                                 Pipeline PDelegate
                                 PTask Work))
  (:import (com.zotohlab.wflow Job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Handler [] PDelegate

  (getStartActivity [_  pipe]
    (DefWFTask
      (fn [cur job arg]
        (log/info "I  just handled a job!"))))

  (onStop [_ pipe]
    (log/info "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/info "Oops, I got an error!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype MyAppMain [] czlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ container]
    (log/info "My AppMain contextualized by container " container))

  (configure [_ options]
    (log/info "My AppMain configured with options " options))

  (initialize [_]
    (log/info "My AppMain initialized!"))

  (start [_]
    (log/info "My AppMain started"))

  (stop [_]
    (log/info "My AppMain stopped"))

  (dispose [_]
    (log/info "My AppMain finz'ed"))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

