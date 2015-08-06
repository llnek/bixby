;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^:no-doc
    ^{:author "kenl"}

  demo.http.websock

  (:require [czlab.xlib.util.logging :as log])

  (:require
    [czlab.xlib.util.process :refer [DelayExec]]
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
          (println "Got poked by websocket-bin: len = " (alength stuff))

          :else
          (println "Funky data from websocket????"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

