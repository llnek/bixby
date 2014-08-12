(ns ^{:doc ""
      :author "kenl"}

  @@APPDOMAIN@@.pipe

  (:require [clojure.tools.logging :as log :only (info warn error debug)])

  (:use [cmzlabclj.tardis.core.constants]
        [cmzlabclj.nucleus.util.str :only [nsb]]
        [cmzlabclj.tardis.core.wfs])

  (:import [com.zotohlab.wflow FlowNode Activity
                               Pipeline
                               PipelineDelegate PTask]
           [com.zotohlab.gallifrey.io HTTPEvent HTTPResult]
           [com.zotohlab.gallifrey.core Container]
           [java.util HashMap]
           [com.zotohlab.wflow.core Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Handler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (DefWFTask
      (fn [fw ^Job job arg]
        (let [tpl (:template (.getv job EV_OPTS))
              ^HTTPEvent evt (.event job)
              src (.emitter evt)
              co (.container src)
              [rdata ct]
              (.loadTemplate co
                             (nsb tpl)
                             (HashMap.))
              res (.getResultObj evt) ]
          (.setHeader res "content-type" ct)
          (.setContent res rdata)
          (.setStatus res 200)
          (.replyResult evt)))
    ))

  (onStop [_ pipe]
    (log/info "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/info "Oops, I got an error!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private pipe-eof nil)



