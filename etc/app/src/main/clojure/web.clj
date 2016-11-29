;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns ^{:doc ""
      :author "@@USER@@"}

  @@APPDOMAIN@@.core

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.sys.core]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wflow.core])

  (:import
    [czlab.wabbit.io HttpEvent HttpResult]
    [czlab.wflow Job TaskDef WorkStream]
    [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ftlContext

  ""
  []

  {:landing
             {:title_line "Sample Web App"
              :title_2 "Demo wabbit"
              :tagline "Say something" }
   :about
             {:title "About wabbit demo" }
   :services {}
   :contact {:email "a@b.com"}
   :description "wabbit web app"
   :encoding "utf-8"
   :title "wabbit|Sample"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn dftHandler

  ""
  ^WorkStream
  []

  (workStream<>
    (script<>
      (fn [_ ^Job job]
        (let
          [tpl (:template (.getv job EV_OPTS))
           ^HttpEvent evt (.event job)
           src (.source evt)
           co (.server src)
           {:keys [data ctype] }
           (.loadTemplate co tpl (ftlContext))
           ^HttpResult
           res (.resultObj evt) ]
          (.setHeader res "content-type" ctype)
          (.setContent res data)
          (.setStatus res 200)
          (.replyResult evt))))
    :catch
    (fn [ _ err]
      (log/info "Oops, I got an error!"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn myAppMain

  ""
  []

  (log/info "My AppMain called!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

