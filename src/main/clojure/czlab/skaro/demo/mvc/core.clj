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


(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.skaro.demo.mvc.core

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.skaro.sys.core]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wflow.core])

  (:import
    [czlab.skaro.io HttpEvent HttpResult]
    [czlab.wflow Job TaskDef WorkStream]
    [czlab.skaro.server Container]))

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
      #(let [tpl (:template (.getv ^Job %2 EV_OPTS))
             ^HttpEvent evt (.event ^Job %2)
             src (.source evt)
             co (.server src)
             {:keys [data ctype] }
             (.loadTemplate co
                            tpl
                            (ftlContext))
             ^HttpResult
             res (.resultObj evt) ]
         (.setHeader res "content-type" ctype)
         (.setContent res data)
         (.setStatus res 200)
         (.replyResult evt)))
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


