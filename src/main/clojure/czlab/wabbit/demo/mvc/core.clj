;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

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
           [czlab.wabbit.io IoEvent HttpEvent]
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
          tpl (:template (.getv job EV_OPTS))
          ^IoEvent evt (.event job)
          co (.. evt source server)
          {:keys [data ctype]}
          (.loadTemplate co
                         tpl
                         (ftlContext))
          res (httpResult<> (.socket evt))]
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


