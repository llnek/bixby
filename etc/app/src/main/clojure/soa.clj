;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns ^{:doc ""
      :author "@@USER@@" }

  @@APPDOMAIN@@.core

  (:require [czlab.xlib.logging :as log])
  (:use [czlab.wflow.core])
  (:import
    [czlab.skaro.server AppMain]
    [czlab.wflow TaskDef Job WorkStream ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn dftHandler

  ""
  ^TaskDef
  []

  (script<>
    (fn [_ job]
      (log/info "I  just handled a job!"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn myAppMain

  ""
  (log/info "My AppMain called!"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

