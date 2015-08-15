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

  czlab.skaro.impl.misc

  (:require
    [czlab.xlib.util.core :refer [trap!]]
    [czlab.xlib.util.wfs :refer [SimPTask]])

  (:import
    [com.zotohlab.wflow Activity Job
    FlowError PTask Work]
    [com.zotohlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal flows
(defn- mkWork ""

  [s]

  (SimPTask
    (fn [^Job job]
      (let [^HTTPEvent evt (.event job)
            ^HTTPResult
            res (.getResultObj evt) ]
        (.setStatus res s)
        (.replyResult evt)
        nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkInternalFlow ""

  ^Activity
  [^Job j s]

  (let [evt (.event j) ]
    (if (instance? HTTPEvent evt)
      (mkWork s)
      (trap! FlowError (str "Unhandled event-type \""
                              (:typeid (meta evt))
                              "\"")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFatalErrorFlow

  ^Activity
  [^Job job]

  (mkInternalFlow job 500))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeOrphanFlow ""

  ^Activity
  [^Job job]

  (mkInternalFlow job 501))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

