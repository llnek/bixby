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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns czlab.bixby.demo.mvc.core

  (:require [czlab.niou.core :as cc]
            [czlab.basal.core :as c]
            [czlab.bixby.core :as b]
            [czlab.bixby.plugs.mvc :as mvc])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ftl-context

  []

  {:landing {:title_line "Sample Web App"
             :title_2 "Demo Skaro"
             :tagline "Say something" }
   :about {:title "About Skaro demo" }
   :services {}
   :contact {:email "a@b.com"}
   :description "Default Skaro web app."
   :encoding "utf-8"
   :title "Skaro|Sample"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handler

  [evt res]

  (c/do-with
    [ch (:socket evt)]
    (let [ri (:route evt)
          tpl (:template ri)
          plug (c/parent evt)
          co (c/parent plug)
          {:keys [data ctype]}
          (mvc/load-template co tpl (ftl-context))]
      (->> (-> (cc/res-header-set res "content-type" ctype)
               (assoc :body data))
           cc/reply-result ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn main
  [_] (println "My AppMain called!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


