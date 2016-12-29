;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;auto-generated

(ns ^{:doc ""
      :author "@@USER@@"}

  @@APPDOMAIN@@.core

  (:require [czlab.xlib.logging :as log]
            [czlab.wabbit.etc.core :as etc :refer :all])

  (:use [czlab.convoy.net.core]
        [czlab.wabbit.sys.core]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.flux.wflow.core])

  (:import [czlab.flux.wflow Job TaskDef WorkStream]
           [czlab.convoy.net RouteInfo HttpResult]
           [czlab.wabbit.io HttpEvent]
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
          [^HttpEvent evt (.event job)
           ri (:info (.routeGist evt))
           tpl (.template ^RouteInfo ri)
           co (.. evt source server)
           {:keys [data ctype]}
           (.loadTemplate co tpl (ftlContext))
           res (httpResult<>)]
          (.setContentType res ctype)
          (.setContent res data)
          (replyResult (.socket evt) res))))
    :catch
    (fn [_ err]
      (log/error "Oops, I got an error!" err))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn myAppMain
  ""
  []
  (log/info "My AppMain called!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

