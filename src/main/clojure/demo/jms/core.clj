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

  demo.jms.core

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.process :only [DelayExec]]
        [czlabclj.xlib.util.core :only [notnil?]]
        [czlabclj.xlib.util.str :only [nsb]])

  (:import  [com.zotohlab.wflow WHandler Job FlowNode PTask]
            [com.zotohlab.skaro.io JMSEvent]
            [javax.jms TextMessage]
            [java.util.concurrent.atomic AtomicInteger]
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

  (run [_  j]
    (require 'demo.jms.core)
    (let [^JMSEvent ev (.event ^Job j)
          ^TextMessage msg (.getMsg ev) ]
      (println "-> Correlation ID= " (.getJMSCorrelationID msg))
      (println "-> Msg ID= " (.getJMSMessageID msg))
      (println "-> Type= " (.getJMSType msg))
      (println "("
               (ncount)
               ") -> Message= "
               (.getText msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

