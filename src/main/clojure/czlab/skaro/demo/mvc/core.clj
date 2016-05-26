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
      :author "kenl"}

  czlab.skaro.demo.mvc.core

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.skaro.core.consts]
        [czlab.xlib.consts]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.skaro.core.wfs])

  (:import
    [czlab.skaro.io HTTPEvent HTTPResult]
    [czlab.wflow.dsl FlowDot Activity
     WorkFlow WorkFlowEx
     Job PTask]
    [czlab.skaro.runtime AppMain]
    [czlab.skaro.server Cocoon]))

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
(defn handler ""

  ^WorkFlowEx
  []

  (reify WorkFlowEx

    (startWith [_]
      (simPTask
        (fn [^Job job]
          (let [tpl (:template (.getv job EV_OPTS))
                ^HTTPEvent evt (.event job)
                src (.emitter evt)
                ^Cocoon
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
(defn myAppMain ""

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


