(ns  ^{:doc ""
       :author "kenl" }

  @@APPDOMAIN@@.core

  (:require [clojure.tools.logging :as log :only (info warn error debug)]))


(deftype MyAppMain [] cmzlabclj.tardis.impl.ext.CljAppMain

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

