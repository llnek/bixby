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

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.io.triggers

  (:require [czlabclj.xlib.util.core
             :refer
             [ThrowIOE
              MakeMMap
              Stringify
              notnil?
              Try!]]
            [czlabclj.xlib.util.str :refer [nsb]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.tardis.io.core])

  (:import  [com.zotohlab.skaro.io HTTPEvent HTTPResult]
            [java.io OutputStream IOException]
            [java.util List Timer TimerTask]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.frwk.core Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeAsyncWaitHolder "Create a async wait wrapper."

  [^czlabclj.tardis.io.core.AsyncWaitTrigger trigger
   ^HTTPEvent event ]

  (let [impl (MakeMMap) ]
    (reify

      Identifiable
      (id [_] (.getId event))

      WaitEventHolder

      (resumeOnResult [this res]
        (let [^Timer tm (.getf impl :timer)
              ^czlabclj.tardis.io.core.EmitAPI
              src (.emitter event) ]
          (when-not (nil? tm) (.cancel tm))
          (.release src this)
          ;;(.mm-s impl :result res)
          (.resumeWithResult trigger res)))

      (timeoutMillis [me millis]
        (let [tm (Timer. true) ]
          (.setf! impl :timer tm)
          (.schedule tm (proxy [TimerTask][]
                          (run [] (.onExpiry me))) ^long millis)))

      (timeoutSecs [this secs]
        (timeoutMillis this (* 1000 secs)))

      (onExpiry [this]
        (let [^czlabclj.tardis.io.core.EmitAPI
              src (.emitter event) ]
          (.release src this)
          (.setf! impl :timer nil)
          (.resumeWithError trigger) ))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

