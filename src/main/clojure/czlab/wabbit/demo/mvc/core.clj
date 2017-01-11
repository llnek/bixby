;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:no-doc true
      :author "Kenneth Leung"}

  czlab.wabbit.demo.mvc.core

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.convoy.net.core]
        [czlab.wabbit.etc.core]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.flux.wflow.core])

  (:import [czlab.flux.wflow Job TaskDef WorkStream]
           [czlab.convoy.net HttpResult]
           [czlab.wabbit.io HttpEvent]
           [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ftlContext
  ""
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
(defn handler
  ""
  ^WorkStream
  []
  (workStream<>
    (script<>
      #(let
         [^Job job %2
          tpl (:template (.getv job evt-opts))
          ^HttpEvent evt (.event job)
          co (.. evt source server)
          {:keys [data ctype]}
          (.loadTemplate co
                         tpl
                         (ftlContext))
          res (httpResult<> (.socket evt)(.msgGist evt))]
         (.setContentType res  ctype)
         (.setContent res data)
         (replyResult (.socket evt) res)))
    :catch
    (fn [_]
      (log/info "Oops, I got an error!"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn myAppMain
  ""
  []
  (log/info "My AppMain called!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


