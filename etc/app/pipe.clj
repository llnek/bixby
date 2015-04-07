(ns ^{:doc ""
      :author kenl }

  @@APPDOMAIN@@.pipe

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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private pipe-eof nil)


