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

  demo.http.websock


  (:require
    [czlab.xlib.util.process :refer [DelayExec]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.core :refer :all]
    [czlab.xlib.util.str :refer :all]
    [czlab.xlib.util.meta :refer [IsBytes?]])

  (:import
    [com.zotohlab.wflow WHandler Job FlowDot PTask]
    [com.zotohlab.frwk.io XData]
    [com.zotohlab.skaro.io WebSockEvent
    WebSockResult]
    [com.zotohlab.skaro.core Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Demo ""

  ^WHandler
  []

  (reify WHandler
    (run [_  j _]
      (let [^WebSockEvent ev (.event ^Job j)
            res (.getResultObj ev)
            data (.getData ev)
            stuff (when (and (some? data)
                             (.hasContent data))
                    (.content data)) ]
        (cond
          (instance? String stuff)
          (println "Got poked by websocket-text: " stuff)

          (IsBytes? (class stuff))
          (println "Got poked by websocket-bin: len = " (alength ^bytes stuff))

          :else
          (println "Funky data from websocket????"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

