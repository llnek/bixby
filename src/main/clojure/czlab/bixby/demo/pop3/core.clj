;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns czlab.bixby.demo.pop3.core

  (:require [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.bixby.core :as b])

  (:import [javax.mail Message Message$RecipientType Multipart]
           [java.util.concurrent.atomic AtomicInteger]
           [javax.mail.internet MimeMessage]))

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
        plug (c/parent evt)
        ^Multipart p (.getContent msg)]
    (c/prn!! "######################## (%d)" (ncount))
    (c/prn! "Subj:%s\r\n" (.getSubject msg))
    (c/prn! "Fr:%s\r\n" (first (.getFrom msg)))
    (c/prn! "To:%s" (first (.getRecipients msg
                                           Message$RecipientType/TO)))
    (c/prn! "\r\n")
    (c/prn!! (i/x->str (-> (.getBodyPart p 0)
                           (.getInputStream)) "utf-8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn main
  [_]
  (System/setProperty "bixby.mock.mail.proto" "pop3s"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


