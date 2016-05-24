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
      :author "kenl" }

  czlab.xlib.jmx.names

  (:require
    [czlab.xlib..logging :as log]
    [clojure.string :as cs])

  (:import
    [javax.management ObjectName]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn objectName

  "paths: [ \"a=b\" \"c=d\" ]
   domain: com.acme
   beanName: mybean"

  ^ObjectName
  [^String domain ^String beanName & [paths]]

  (let [paths (or paths [])
        sb (StringBuilder.)
        cs (seq paths) ]
    (doto sb
      (.append domain)
      (.append ":")
      (.append (cs/join "," cs)))
    (when-not (empty? cs) (.append sb ","))
    (doto sb
      (.append "name=")
      (.append beanName))
    (ObjectName. (.toString sb))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


