;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns ^{:doc ""
      :author "kenl"}

  demo.jetty.core

  (:require [czlab.xlib.util.logging :as log])

  (:use [czlab.skaro.core.consts]
        [czlab.xlib.util.consts]
        [czlab.xlib.util.core]
        [czlab.xlib.util.str]
        [czlab.xlib.util.wfs])

  (:import
    [com.zotohlab.skaro.io HTTPEvent HTTPResult]
    [com.zotohlab.wflow FlowDot Activity
    WorkFlow WorkFlowEx
    Job PTask]
    [com.zotohlab.skaro.runtime AppMain]
    [com.zotohlab.skaro.core Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ftlContext ""

  []

  {:landing
             {:title_line "Sample Web App"
              :title_2 "Demo Skaro"
              :tagline "Say something" }
   :about
             {:title "About Skaro demo" }
   :services {}
   :contact {:email "a@b.com"}
   :description "Default Skaro web app."
   :encoding "utf-8"
   :title "Skaro|Sample"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Handler ""

  ^WorkFlowEx
  []

  (reify WorkFlowEx

    (startWith [_]
      (SimPTask
        (fn [^Job job]
          (let [tpl (:template (.getv job EV_OPTS))
                ^HTTPEvent evt (.event job)
                src (.emitter evt)
                ^Container
                co (.container src)
                {:keys [data ctype] }
                (.loadTemplate co
                               tpl
                               (ftlContext))
                res (.getResultObj evt) ]
            (.setHeader res "content-type" ctype)
            (.setContent res data)
            (.setStatus res 200)
            (.replyResult evt)))))

    (onError [ _ err]
      (log/info "Oops, I got an error!"))))

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

