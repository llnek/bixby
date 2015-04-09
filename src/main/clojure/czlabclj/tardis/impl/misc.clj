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
      :author "kenl" }

  czlabclj.tardis.impl.misc

  (:use [czlabclj.tardis.core.wfs :only [DefPTask]])

  (:import  [com.zotohlab.wflow Job FlowError]
            [com.zotohlab.wflow Pipeline Job PDelegate PTask Work]
            [com.zotohlab.gallifrey.io IOEvent HTTPEvent HTTPResult]
            [com.zotohlab.frwk.core Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal flows
(defn- make-work ""

  [s]

  (DefPTask
    (fn [cur ^Job job arg]
      (let [^HTTPEvent evt (.event job)
            ^HTTPResult
            res (.getResultObj evt) ]
        (.setStatus res s)
        (.replyResult evt)
        nil))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-internal-flow ""

  [^Pipeline pipe s]

  (let [evt (-> pipe (.job)(.event)) ]
    (if (instance? HTTPEvent evt)
      (make-work s)
      (throw (FlowError. (str "Unhandled event-type \""
                              (:typeid (meta evt))
                              "\"."))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype FatalErrorFlow [] PDelegate

  (startWith [_ pipe] (make-internal-flow pipe 500))
  (onStop [_ pipe ] nil)
  (onError [_ error cur] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype OrphanFlow [] PDelegate

  (startWith [_  pipe] (make-internal-flow pipe 501))
  (onStop [_  pipe] nil)
  (onError [_  error cur] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeFatalErrorFlow

  ^Startable
  [^Job job]

  (Pipeline. "Fatal Error" "czlabclj.tardis.etc.misc.FatalErrorFlow" job))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeOrphanFlow ""

  ^Startable
  [^Job job]

  (Pipeline. "Orphan Flow" "czlabclj.tardis.etc.misc.OrphanFlow" job))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private misc-eof nil)

