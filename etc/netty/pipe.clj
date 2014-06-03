(ns @@APPDOMAIN@@.pipe )

(import '( com.zotohlab.wflow
  FlowPoint Activity Pipeline PipelineDelegate PTask Work))
(import '(com.zotohlab.gallifrey.io HTTPEvent HTTPResult))
(import '(com.zotohlab.wflow.core Job))
(use '[clojure.tools.logging :only (info warn error debug)])

(deftype Handler [] PipelineDelegate
  (getStartActivity [_  pipe]
    (PTask. (reify Work
              (perform [_ fw job arg]
                (let [ ^HTTPEvent evt (.event job)
                       ^HTTPResult res (.getResultObj evt) ]
                  (.setStatus res 200)
                  (.setContent res "hello world")
                  (.setHeader res "content-type" "text/plain")
                  (.replyResult evt))))))

  (onStop [_ pipe]
    (info "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (info "Oops, I got an error!")))




(def ^:private pipe-eof nil)



