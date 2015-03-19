(ns ^{:doc ""
      :author "kenl"}

  @@APPDOMAIN@@.pipe

  (:require [clojure.tools.logging :as log :only (info warn error debug)])

  (:use [czlabclj.tardis.core.wfs])

  (:import [com.zotohlab.wflow FlowNode Activity
                                 Pipeline PipelineDelegate PTask Work]
           [com.zotohlab.gallifrey.io HTTPEvent HTTPResult]
           [com.zotohlab.wflow.core Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Handler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (DefWFTask
      (fn [cur ^Job job arg]
        (let [^HTTPEvent evt (.event job)
              res (.getResultObj evt) ]
          (doto res
            (.setStatus 200)
            (.setContent "Bonjour Skaro!")
            (.setHeader "content-type" "text/plain"))
          (.replyResult evt)
          nil))))

  (onStop [_ pipe]
    (log/info "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/info "Oops, I got an error!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private pipe-eof nil)


