;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl"}

  demo.http.websock

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.process :only [DelayExec]]
        [czlabclj.xlib.util.core :only [notnil?]]
        [czlabclj.xlib.util.str :only [nsb]]
        [czlabclj.xlib.util.meta :only [IsBytes?]]
        [czlabclj.tardis.core.wfs :only [DefPTask]])

  (:import  [com.zotohlab.wflow FlowNode PTask PDelegate]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.gallifrey.io WebSockEvent
                                       WebSockResult]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow Job]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] PDelegate

  (startWith [_ pipe]
    (require 'demo.http.websock)
    (DefPTask
      (fn [cur ^Job job arg]
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

