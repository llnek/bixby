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

  demo.pop3.core

  (:require [czlab.xlib.util.logging :as log])

  (:require
    [czlab.xlib.util.process :refer [DelayExec]])

  (:import
    [com.zotohlab.wflow WHandler Job FlowDot PTask]
    [org.apache.commons.io IOUtils]
    [java.util.concurrent.atomic AtomicInteger]
    [javax.mail Message Message$RecipientType Multipart]
    [javax.mail.internet MimeMessage]
    [com.zotohlab.skaro.runtime AppMain]
    [com.zotohlab.skaro.io EmailEvent]
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
(defn Demo ""

  ^WHandler
  []

  (reify WHandler
    (run [_  j _]
      (let [^EmailEvent ev (.event ^Job j)
            ^MimeMessage msg (.getMsg ev)
            ^Multipart p (.getContent msg) ]
        (println "######################## (" (ncount) ")" )
        (print "Subj:" (.getSubject msg) "\r\n")
        (print "Fr:" (first (.getFrom msg)) "\r\n")
        (print "To:" (first (.getRecipients msg
                                     Message$RecipientType/TO)))
        (print "\r\n")
        (println (IOUtils/toString (-> (.getBodyPart p 0)
                                       (.getInputStream))
                                   "utf-8"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MyAppMain ""

  ^AppMain
  []

  (reify AppMain
    (contextualize [_ ctr] )
    (initialize [_] )
    (configure [_ cfg]
      (System/setProperty "skaro.demo.pop3"
                          "com.zotohlab.mock.mail.MockPop3Store"))
    (start [_] )
    (stop [_] )
    (dispose [_] )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

