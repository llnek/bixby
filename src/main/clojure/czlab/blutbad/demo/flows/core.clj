;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.demo.flows.core

  (:require [czlab.flux.core :as wf]
            [czlab.basal.core :as c]
            [czlab.blutbad.core :as b]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;; What this example demostrates is a webservice which takes in some user info, authenticate the
;; user, then exec some EC2 operations such as granting permission to access an AMI, and
;; permission to access/snapshot a given volume.  When all is done, a reply will be sent back
;; to the user.
;;
;; This flow showcases the use of conditional activities such a choices &amp; decision.
;; Shows how to loop using wloop, and how to use fork &amp; join.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- perf-auth-mtd

  [t]

  (case t
    "facebook" #(c/do#nil %2 (c/prn!! "-> use facebook"))
    "google+" #(c/do#nil %2 (c/prn!! "-> use google+"))
    "openid" #(c/do#nil % (c/prn!! "-> use open-id"))
    #(c/do#nil % (c/prn!! "-> use internal db"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step1. choose a method to authenticate the user
;;here, we'll use choice<> to pick which method
(defn- auth-user

  []

  ;; hard code to use facebook in this example, but you
  ;; could check some data from the job,
  ;; such as URI/Query params
  ;; and decide on which value to switch on
  (wf/choice<>
    #(let [_ %]
       (c/prn!! "step(1): choose an auth-method") "facebook")
    "facebook"  (perf-auth-mtd "facebook")
    "google+" (perf-auth-mtd "google+")
    "openid" (perf-auth-mtd "openid")
    :default
    (perf-auth-mtd "db")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step2
(c/def- get-profile
  #(c/do#nil %2 (c/prn!! "step(2): get user profile\n%s" "->user is superuser")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step3 we are going to dummy up a retry of 2 times to simulate network/operation
;;issues encountered with EC2 while trying to grant permission
;;so here , we are using a wloop to do that
(c/def- prov-ami
  (wf/while<>
    #(let [job %
           v (c/getv job :ami_count)
           c (if (some? v) (inc v) 0)]
       (c/setv job :ami_count c)
       (< c 3))
    #(c/do#nil
       (let [job %2
             v (c/getv job :ami_count)
             c (if (some? v) v 0)]
         (if (== 2 c)
           (c/prn!! "step(3): granted permission for user %s"
                    "to launch this ami(id)")
           (c/prn!! "step(3): failed to contact %s%s%s"
                    "ami- server, will retry again (" c ")"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step3'. we are going to dummy up a retry of 2 times to simulate network/operation
;;issues encountered with EC2 while trying to grant volume permission
;;so here , we are using a wloop to do that
(c/def- prov-vol
  (wf/while<>
    #(let [job %
           v (c/getv job :vol_count)
           c (if (some? v) (inc v) 0)]
       (c/setv job :vol_count c)
       (< c 3))
    #(c/do#nil
       (let [job %2
             v (c/getv job :vol_count)
             c (if (some? v) v 0)]
         (if (== c 2)
           (c/prn!! "step(3'): granted permission for user %s"
                    "to access/snapshot this volume(id)")
           (c/prn!! "step(3'): failed to contact vol- server, %s%s%s"
                    "will retry again (" c ")"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;step4. pretend to write stuff to db. again, we are going to dummy up the case
;;where the db write fails a couple of times
;;so again , we are using a wloop to do that
(c/def- save-sdb
  (wf/while<>
    #(let [job %
           v (c/getv job :wdb_count)
           c (if (some? v) (inc v) 0)]
          (c/setv job :wdb_count c)
          (< c 3))
    #(c/do#nil
       (let [job %2
             v (c/getv job :wdb_count)
             c (if (some? v) v 0)]
         (if (== c 2)
           (c/prn!! "step(4): wrote stuff to database successfully")
           (c/prn!! "step(4): failed to contact db- server, %s%s%s"
                    "will retry again (" c ")"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;this is the step where it will do the provisioning of the AMI and the EBS volume
;;in parallel.  To do that, we use a split-we want to fork off both tasks in parallel.  Since
;;we don't want to continue until both provisioning tasks are done. we use a AndJoin to hold/freeze
;;the workflow
(c/def- provision
  (wf/group<> (wf/split-join<> [:type :and] prov-ami prov-vol) save-sdb))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; this is the final step, after all the work are done, reply back to the caller.
;; like, returning a 200-OK
(c/def- reply-user
  #(c/do#nil
     (let [job %2]
       (c/prn!! "step(5): we'd probably return a 200 OK %s"
                "back to caller here"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- error-user
  #(c/do#nil
     (let [job %2]
       (c/prn!! "step(5): we'd probably return a 200 OK %s"
                "but with errors"))))

;; do a final test to see what sort of response should we send back to the user.
(c/def- final-test
  (wf/decision<> #(c/do#true %) reply-user error-user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn demo<>

  [evt]

  ;; this workflow is a small (4 step) workflow, with the 3rd step (Provision) being
  ;; a split, which forks off more steps in parallel.
  (let [p (c/parent evt)
        s (c/parent p)
        c (b/scheduler s)]
    (wf/exec (wf/workflow*
               (wf/group<> (auth-user)
                           get-profile provision final-test)) (wf/job<> c nil evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

