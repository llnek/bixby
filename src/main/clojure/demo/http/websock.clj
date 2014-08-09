;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{:doc ""
      :author "kenl"}

  demo.http.websock

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.process :only [DelayExec] ]
        [cmzlabclj.nucleus.util.core :only [notnil?] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.nucleus.util.meta :only [IsBytes?] ]
        [cmzlabclj.tardis.core.wfs :only [DefWFTask]])


  (:import  [com.zotohlab.wflow FlowNode PTask
                                PipelineDelegate]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.gallifrey.io WebSockEvent
                                       WebSockResult]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow.core Job]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] PipelineDelegate

  (getStartActivity [_ pipe]
    (require 'demo.http.websock)
    (DefWFTask
      (fn [cur job arg]
        (let [^WebSockEvent ev (.event job)
              res (.getResultObj ev)
              data (.getData ev)
              stuff (if (and (notnil? data)
                             (.hasContent data))
                      (.content data)
                      nil) ]
          (cond
            (instance? String stuff)
            (println "Got poked by websocket-text: " stuff)

            (IsBytes? (class stuff))
            (println "Got poked by websocket-bin: len = " (alength stuff))

            :else
            (println "Funky data from websocket????"))
          nil))))

  (onStop [_ p] )
  (onError [_ e c] nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

