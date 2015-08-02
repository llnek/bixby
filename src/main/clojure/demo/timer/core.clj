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

(ns ^:no-doc
    ^{:author "kenl"}

  demo.timer.core

  (:require [czlab.xlib.util.process :refer [DelayExec]]
            [czlab.xlib.util.core :refer [notnil?]]
            [czlab.xlib.util.str :refer [nsb]]
            [czlab.xlib.util.wfs :refer [SimPTask]])

  (:require [clojure.tools.logging :as log])

  (:import  [com.zotohlab.wflow WHandler Job FlowDot PTask]
            [java.util.concurrent.atomic AtomicInteger]
            [java.util Date]
            [com.zotohlab.skaro.io TimerEvent]
            [com.zotohlab.skaro.core Container]))

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
(deftype Demo [] WHandler

  (run [_  j _]
    (require 'demo.timer.core)
    (let [^TimerEvent ev (.event ^Job j) ]
      (if (.isRepeating ev)
        (println "-----> (" (ncount) ") repeating-update: " (Date.))
        (println "-----> once-only!!: " (Date.))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


