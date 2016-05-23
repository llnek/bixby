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


(ns ^:no-doc
    ^{:author "kenl"}

  demo.file.core


  (:require
    [czlab.xlib.util.core :refer [try!]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.util.str :refer [hgl?]])

  (:import
    [com.zotohlab.wflow WHandler Job FlowDot PTask]
    [com.zotohlab.skaro.core Container Muble]
    [com.zotohlab.skaro.io FileEvent]
    [com.zotohlab.frwk.server ServiceProvider Service]
    [java.util.concurrent.atomic AtomicInteger]
    [org.apache.commons.io FileUtils]
    [java.util Date]
    [java.lang StringBuilder]
    [java.io File IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(let [ctr (AtomicInteger.)]
  (defn- ncount ""
    []
    (.incrementAndGet ctr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DemoGen ""

  ^WHandler
  []

  (reify WHandler
    (run [_  j _]
      (let [^Muble p (-> ^ServiceProvider
                         (.container ^Job j)
                         (.getService :default-sample))
            s (str "Current time is " (Date.)) ]
        (spit (io/file (.getv p :targetFolder)
                       (str "ts-" (ncount) ".txt"))
              s :encoding "utf-8")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DemoPick ""

  ^WHandler
  []

  (reify WHandler
    (run [_  j _]
      (let [f (-> ^FileEvent (.event ^Job j)
                  (.getFile)) ]
        (println "picked up new file: " f)
        (println "content: " (slurp f :encoding "utf-8"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

