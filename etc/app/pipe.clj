(ns ^{
      :doc ""
      :author kenl }

  @@APPDOMAIN@@.pipe

  (:require [clojure.tools.logging :as log :only (info warn error debug)])
  (:import ( com.zotohlabs.wflow FlowPoint Activity
                                 Pipeline PipelineDelegate
                                 PTask Work))
  (:import (com.zotohlabs.wflow.core Job)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Handler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (PTask. (reify Work
              (perform [_ fw job arg]
                (log/info "I  just handled a job!")))))

  (onStop [_ pipe]
    (log/info "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/info "Oops, I got an error!")))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private pipe-eof nil)


