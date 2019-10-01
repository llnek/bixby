;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^:no-doc
  ^{:author "Kenneth Leung"}

  czlab.wabbit.demo.http.websock

  (:require [czlab.wabbit.xpis :as xp]
            [czlab.basal
             [log :as l]
             [core :as c]])

  (:import [czlab.basal XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn demo<>
  "" [evt]
  (c/do-with
    [ch (:socket evt)]
    (let [data (:body evt)
          stuff (some-> ^XData data .content)]
      (cond
        (string? stuff)
        (c/prn!! "Got poked by websocket-text: %s" stuff)
        (bytes? stuff)
        (c/prn!! "Got poked by websocket-bin: len = %s" (alength ^bytes stuff))
        :else
        (c/prn!! "Funky data from websocket????")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


