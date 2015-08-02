;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns ^{:doc ""
      :author "@@USER@@"}

  @@APPDOMAIN@@.core

  (:require [clojure.tools.logging :as log :only (info warn error debug)])

  (:use [czlab.skaro.core.constants]
        [czlab.xlib.util.str :only [nsb]]
        [czlab.xlib.util.wfs])

  (:import [com.zotohlab.wflow FlowNode Activity
                               Pipeline Job
                               PDelegate PTask]
           [com.zotohlab.skaro.io HTTPEvent HTTPResult]
           [com.zotohlab.skaro.core Container]
           [java.util HashMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ftlContext ""

  []

  (let [ctx (HashMap.)
        p1 (HashMap.)
        p2 (HashMap.)
        p3 (HashMap.)
        p4 (HashMap.)]
    (doto ctx
      (.put "landing" p1)
      (.put "about" p2)
      (.put "services" p3)
      (.put "contact" p4))
    (.put p1 "title_line" "Sample Web App")
    (.put p1 "title_2" "Demo Skaro")
    (.put p1 "tagline" "Say something")
    (.put p2 "title" "About Skaro demo")
    (.put p4 "email" "a@b.com")

    (.put ctx "description" "Default Skaro web app.")
    (.put ctx "encoding" "utf-8")
    (.put ctx "title" "Skaro|Sample")
    ctx
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Handler [] PDelegate

  (onStop [_ pipe]
    (log/info "nothing to be done here, just stop please."))

  (startWith [_  pipe]
    (require '@@APPDOMAIN@@.core)
    (DefPTask
      (fn [fw ^Job job arg]
        (let [tpl (:template (.getv job EV_OPTS))
              ^HTTPEvent evt (.event job)
              src (.emitter evt)
              co (.container src)
              [rdata ct]
              (.loadTemplate co
                             (nsb tpl)
                             (ftlContext))
              res (.getResultObj evt) ]
          (.setHeader res "content-type" ct)
          (.setContent res rdata)
          (.setStatus res 200)
          (.replyResult evt)))
    ))

  (onError [ _ err curPt]
    (log/info "Oops, I got an error!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype MyAppMain [] czlab.skaro.impl.ext.CljAppMain

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

