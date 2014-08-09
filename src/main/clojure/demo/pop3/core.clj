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

  demo.pop3.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.process :only [DelayExec] ]
        [cmzlabclj.nucleus.util.core :only [notnil?] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.tardis.core.wfs :only [DefWFTask]])


  (:import  [com.zotohlab.wflow FlowNode PTask
                                PipelineDelegate]

            [org.apache.commons.io IOUtils]
            [java.util.concurrent.atomic AtomicInteger]
            [javax.mail Message Multipart]
            [javax.mail.internet MimeMessage]
            [com.zotohlab.gallifrey.io EmailEvent]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow.core Job]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(System/setProperty "skaro.demo.pop3"
                    "com.zotohlab.mock.mail.MockPop3Store")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^AtomicInteger _count (AtomicInteger.))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ncount ""

  []

  (.incrementAndGet _count))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] PipelineDelegate

  (getStartActivity [_ pipe]
    (require 'demo.pop3.core)
    (DefWFTask
      (fn [cur ^Job job arg]
        (let [^EmailEvent ev (.event job)
              ^MimeMessage msg (.getMsg ev)
              ^Multipart p (.getContent msg) ]
          (println "######################## (" (ncount) ")" )
          (print "Subj:" (.getSubject msg) "\r\n")
          (print "Fr:" (first (.getFrom msg)) "\r\n")
          (print "To:" (.getRecipients msg 0))
          (print "\r\n")
          (println (IOUtils/toString (-> (.getBodyPart p 0)
                                        (.getInputStream))
                                     "utf-8"))
          nil))))

  (onStop [_ p] )
  (onError [_ err c] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoMain [] cmzlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ c] )

  (initialize [_]
    (println "Demo receiving POP3 emails..." ))

  (configure [_ cfg] )

  (start [_] )

  (stop [_] )

  (dispose [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

