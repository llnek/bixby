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
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl"}

  demo.flows.core

  (:use [czlab.xlib.util.wfs])

  (:import
    [com.zotohlab.wflow PTask
    WorkFlow Job
    Activity FlowError If Split Switch While]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;; What this example demostrates is a webservice which takes in some user info, authenticate the
;; user, then exec some EC2 operations such as granting permission to access an AMI, and
;; permission to access/snapshot a given volume.  When all is done, a reply will be sent back
;; to the user.
;;
;; This flow showcases the use of conditional activities such a Switch() &amp; If().  Shows how to loop using
;; While(), and how to use Split &amp; Join.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAuthMtd ""

  ^Activity
  [t]

  (case t
    "facebook" (SimPTask (fn [_] (println "-> use facebook")))
    "google+" (SimPTask (fn [_] (println "-> use google+")))
    "openid" (SimPTask (fn [_] (println "-> use open-id")))
    (SimPTask (fn [_] (println "-> use internal db")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step1. choose a method to authenticate the user
;;here, we'll use a switch() to pick which method
(defn- auth-user ""

  ^Activity
  []

  ;; hard code to use facebook in this example, but you
  ;; could check some data from the job, such as URI/Query params
  ;; and decide on which mth-value to switch() on
  (doto
    (->> (DefChoiceExpr
           (fn [_]
             (println "step(1): choose an authentication method")
             "facebook"))
         (Switch/apply ))
    (.withChoice "facebook"  (getAuthMtd "facebook"))
    (.withChoice "google+" (getAuthMtd "google+"))
    (.withChoice "openid" (getAuthMtd "openid"))
    (.withDft  (getAuthMtd "db"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step2
(defonce ^:private ^Activity
  GetProfile
  (SimPTask (fn [_] (println "step(2): get user profile\n"
                             "->user is superuser"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step3 we are going to dummy up a retry of 2 times to simulate network/operation
;;issues encountered with EC2 while trying to grant permission
;;so here , we are using a while() to do that
(defonce ^:private ^Activity
  prov_ami
  (While/apply
    (DefBoolExpr
      (fn [^Job j]
        (let [v (.getv j :ami_count)
              c (if (some? v) (inc v) 0)]
         (.setv j :ami_count c)
         (< c 3))))
    (SimPTask
      (fn [^Job j]
        (let [v (.getv j :ami_count)
              c (if (some? v) v 0) ]
          (if (== 2 c)
            (println "step(3): granted permission for user "
                     "to launch this ami(id)")
            (println "step(3): failed to contact "
                     "ami- server, will retry again (" c ")")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step3'. we are going to dummy up a retry of 2 times to simulate network/operation
;;issues encountered with EC2 while trying to grant volume permission
;;so here , we are using a while() to do that
(defonce ^:private ^Activity
  prov_vol
  (While/apply
    (DefBoolExpr
      (fn [^Job j]
        (let [v (.getv j :vol_count)
              c (if (some? v) (inc v) 0) ]
          (.setv j :vol_count c)
          (< c 3))))
    (SimPTask
      (fn [^Job j]
        (let [v (.getv j :vol_count)
              c (if (some? v) v 0) ]
          (if (== c 2)
            (println "step(3'): granted permission for user "
                     "to access/snapshot this volume(id)")
            (println "step(3'): failed to contact vol- server, "
                     "will retry again (" c ")")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step4. pretend to write stuff to db. again, we are going to dummy up the case
;;where the db write fails a couple of times
;;so again , we are using a while() to do that
(defonce ^:private ^Activity
  save_sdb
  (While/apply
    (DefBoolExpr
      (fn [^Job j]
        (let [v (.getv j :wdb_count)
              c (if (some? v) (inc v) 0)]
          (.setv j :wdb_count c)
          (< c 3))))
    (SimPTask
      (fn [^Job j]
        (let [v (.getv j :wdb_count)
              c (if (some? v) v 0) ]
          (if (== c 2)
            (println "step(4): wrote stuff to database successfully")
            (println "step(4): failed to contact db- server, "
                   "will retry again (" c ")")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;this is the step where it will do the provisioning of the AMI and the EBS volume
;;in parallel.  To do that, we use a split-we want to fork off both tasks in parallel.  Since
;;we don't want to continue until both provisioning tasks are done. we use a AndJoin to hold/freeze
;;the workflow
(defonce ^:private ^Activity
  Provision
  (-> (Split/applyAnd save_sdb)
      (.includeMany (into-array Activity [prov_ami prov_vol]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; this is the final step, after all the work are done, reply back to the caller.
;; like, returning a 200-OK
(defonce ^:private ^Activity
  ReplyUser
  (SimPTask (fn [_] (println "step(5): we'd probably return a 200 OK "
                             "back to caller here"))))

(defonce ^:private ^Activity
  ErrorUser
  (SimPTask (fn [_] (println "step(5): we'd probably return a 200 OK "
                             "but with errors"))))

;; do a final test to see what sort of response should we send back to the user.
(defonce ^:private ^Activity
  FinalTest
  (If/apply
    (DefBoolExpr (fn [_] true))
    ReplyUser
    ErrorUser))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Demo ""

  ^WorkFlow
  []

  (reify WorkFlow
    (startWith [_]
      ;; so, the workflow is a small (4 step) workflow, with the 3rd step (Provision) being
      ;; a split, which forks off more steps in parallel.
      (-> (auth-user)
          (.chain GetProfile)
          (.chain Provision)
          (.chain FinalTest)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


