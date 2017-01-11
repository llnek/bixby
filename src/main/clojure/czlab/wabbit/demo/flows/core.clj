;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:no-doc true
      :author "Kenneth Leung"}

  czlab.wabbit.demo.flows.core

  (:use [czlab.flux.wflow.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.flux.wflow
            ChoiceExpr
            BoolExpr
            Job
            TaskDef
            WorkStream]))

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
(defn- getAuthMtd
  ""
  ^TaskDef
  [t]
  (case t
    "facebook" (script<> #(let [x %2] (println "-> use facebook")))
    "google+" (script<> #(let [x %2] (println "-> use google+")))
    "openid" (script<> #(let [x %2] (println "-> use open-id")))
    (script<> #(let[x %2] (println "-> use internal db")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step1. choose a method to authenticate the user
;;here, we'll use a switch() to pick which method
(defn- authUser
  ""
  ^TaskDef
  []
  ;; hard code to use facebook in this example, but you
  ;; could check some data from the job,
  ;; such as URI/Query params
  ;; and decide on which value to switch() on
  (choice<>
    (reify ChoiceExpr
      (choose [_ _]
        (println "step(1): choose an authentication method")
        "facebook"))
    (getAuthMtd "db")
    "facebook"  (getAuthMtd "facebook")
    "google+" (getAuthMtd "google+")
    "openid" (getAuthMtd "openid")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step2
(def
  ^:private
  ^TaskDef
  GetProfile
  (script<> #(let [x %2]
               (println "step(2): get user profile\n"
                        "->user is superuser"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step3 we are going to dummy up a retry of 2 times to simulate network/operation
;;issues encountered with EC2 while trying to grant permission
;;so here , we are using a while() to do that
(def
  ^:private
  ^TaskDef
  provAmi
  (wloop<>
    (reify BoolExpr
      (ptest [_ job]
        (let [v (.getv job :ami_count)
              c (if (some? v) (inc v) 0)]
         (.setv job :ami_count c)
         (< c 3))))
    (script<>
      #(let [v (.getv ^Job %2 :ami_count)
             c (if (some? v) v 0)]
         (if (== 2 c)
           (println "step(3): granted permission for user "
                    "to launch this ami(id)")
           (println "step(3): failed to contact "
                    "ami- server, will retry again (" c ")"))
         nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step3'. we are going to dummy up a retry of 2 times to simulate network/operation
;;issues encountered with EC2 while trying to grant volume permission
;;so here , we are using a while() to do that
(def
  ^:private
  ^TaskDef
  provVol
  (wloop<>
    (reify BoolExpr
      (ptest [_ job]
        (let [v (.getv job :vol_count)
              c (if (some? v) (inc v) 0)]
          (.setv job :vol_count c)
          (< c 3))))
    (script<>
      #(let [v (.getv ^Job %2 :vol_count)
             c (if (some? v) v 0)]
         (if (== c 2)
           (println "step(3'): granted permission for user "
                    "to access/snapshot this volume(id)")
           (println "step(3'): failed to contact vol- server, "
                    "will retry again (" c ")"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step4. pretend to write stuff to db. again, we are going to dummy up the case
;;where the db write fails a couple of times
;;so again , we are using a while() to do that
(def
  ^:private
  ^TaskDef
  saveSdb
  (wloop<>
    (reify BoolExpr
      (ptest [_ job]
        (let [v (.getv job :wdb_count)
              c (if (some? v) (inc v) 0)]
          (.setv job :wdb_count c)
          (< c 3))))
    (script<>
      #(let [v (.getv ^Job %2 :wdb_count)
             c (if (some? v) v 0)]
         (if (== c 2)
           (println "step(4): wrote stuff to database successfully")
           (println "step(4): failed to contact db- server, "
                    "will retry again (" c ")"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;this is the step where it will do the provisioning of the AMI and the EBS volume
;;in parallel.  To do that, we use a split-we want to fork off both tasks in parallel.  Since
;;we don't want to continue until both provisioning tasks are done. we use a AndJoin to hold/freeze
;;the workflow
(def
  ^:private
  ^TaskDef
  Provision
  (group<>
    (fork<> {:join :and} provAmi provVol)
    saveSdb))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; this is the final step, after all the work are done, reply back to the caller.
;; like, returning a 200-OK
(def
  ^:private
  ^TaskDef
  ReplyUser
  (script<> #(let [x %2]
               (println "step(5): we'd probably return a 200 OK "
                        "back to caller here"))))

(def
  ^:private
  ^TaskDef
  ErrorUser
  (script<> #(let [x %2]
               (println "step(5): we'd probably return a 200 OK "
                        "but with errors"))))

;; do a final test to see what sort of response should we send back to the user.
(def
  ^:private
  ^TaskDef
  FinalTest
  (ternary<>
    (reify BoolExpr (ptest [_ _] true))
    ReplyUser
    ErrorUser))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn demo
  ""
  ^WorkStream
  []
  ;; the workflow is a small (4 step) workflow, with the 3rd step (Provision) being
  ;; a split, which forks off more steps in parallel.
  (workStream<>
    (group<> (authUser) GetProfile Provision FinalTest)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


