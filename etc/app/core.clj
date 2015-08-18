;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns ^{:doc ""
      :author "@@USER@@" }

  @@APPDOMAIN@@.core

  (:require [czlab.xlib.util.logging :as log])
  (:use [czlab.xlib.util.wfs])
  (:import
    [com.zotohlab.skaro.runtime AppMain]
    [com.zotohlab.wflow FlowDot Activity Job
    WHandler ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Handler ""

  ^WHandler
  []

  (reify WHandler
    (run [_ job args]
      (log/info "I  just handled a job!"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MyAppMain ""

  ^AppMain
  []

  (reify AppMain

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
      (log/info "My AppMain finz'ed"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

