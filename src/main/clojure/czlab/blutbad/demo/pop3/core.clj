;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.demo.pop3.core

  (:require [czlab.basal.log :as l]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c]
            [czlab.wabbit.xpis :as xp])

  (:import [javax.mail Message Message$RecipientType Multipart]
           [java.util.concurrent.atomic AtomicInteger]
           [javax.mail.internet MimeMessage]
           [org.apache.commons.io IOUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- ^AtomicInteger gint (AtomicInteger.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ncount
  [] (.incrementAndGet gint))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn demo

  [evt]

  (let [^MimeMessage msg (:message evt)
        ^Multipart p (.getContent msg)]
    (println "######################## (" (ncount) ")" )
    (print "Subj:" (.getSubject msg) "\r\n")
    (print "Fr:" (first (.getFrom msg)) "\r\n")
    (print "To:" (first (.getRecipients msg
                                        Message$RecipientType/TO)))
    (print "\r\n")
    (println (IOUtils/toString (-> (.getBodyPart p 0)
                                   (.getInputStream)) "utf-8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn main
  [_]
  (System/setProperty "wabbit.mock.mail.proto" "pop3s"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


