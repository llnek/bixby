// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{:doc ""
      :author "kenl"}

  cmzlabclj.tardis.demo.steps.core


  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [notnil?] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.tardis.core.sys])


  (:import  [com.zotohlab.wflow FlowNode PTask Switch If Block
                                While PipelineDelegate]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow.core Job]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAuthMtd ""

  ^PTask
  [^String t]

  (condp = t
    "facebook"
    (DefWFTask #(println "-> using facebook to login.\n"))

    "google+"
    (DefWFTask #(println "-> using google+ to login.\n"))
    
    "openid"
    (DefWFTask #(println "-> using open-id to login.\n"))

    (DefWFTask #(println "-> using internal db to login.\n"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; What this example demostrates is a webservice which takes in some user info, authenticate the
;; user, then exec some EC2 operations such as granting permission to access an AMI, and
;; permission to access/snapshot a given volume.  When all is done, a reply will be sent back
;; to the user.
;;
;; This flow showcases the use of conditional activities such a Switch() &amp; If().  Shows how to loop using
;; While(), and how to use Split &amp; Join.
;;
(deftype Demo [] PipelineDelegate 

  (getStartActivity [_ pipe]
    (require 'cmzlabclj.tardis.demo.steps.core)
    ;; step1. choose a method to authenticate the user
    ;; here, we'll use a switch() to pick which method
    (let [AuthUser (-> (Switch/apply (DefChoiceExpr
                                       (fn [job]
                                         ;; hard code to use facebook in this example, but you
                                         ;; could check some data from the job, such as URI/Query params
                                         ;; and decide on which mth-value to switch() on.
                                         (println "Step(1): Choose an authentication method.")
                                         "facebook")))
                       (.withChoice "facebook" (getAuthMtd "facebook"))
                       (.withChoice "google+" (getAuthMtd "google+"))
                       (.withChoice "openid" (getAuthMtd "openid"))
                       (.withDef  (getAuthMtd "db")))
    ;; step2.
          GetProfile (DefWFTask #(println "Step(2): Get user profile\n"
                                          "-> user is superuser.\n"))
    ;; step3. we are going to dummy up a retry of 2 times to simulate network/operation
    ;; issues encountered with EC2 while trying to grant permission.
    ;; so here , we are using a while() to do that.
          prov_ami (While/apply
                     (DefBoolExpr
                       ;; we are going to dummy up so it will loop 3 times
                       (fn [j]
                         (let [v (.getv j "ami_count")
                               c (if (number? v) (+ 1 v) 0) ]
                           (.setv j "ami_count" c)
                           (< c 3))))
                     (DefWFTask
                       (fn [cur job arg]
                         (let [obj (.getv job "ami_count")]
                           (when (number? obj)
                             (if (== 2 obj)
                               (println "Step(3): Granted permission for user to launch "
                                        "this ami(id).\n")
                               (println "Step(3): Failed to contact ami- server, "
                                        "will retry again... (" obj ") ")))
                           nil))))
          ;; step3'. we are going to dummy up a retry of 2 times to simulate network/operation
          ;; issues encountered with EC2 while trying to grant volume permission.
          ;; so here , we are using a while() to do that.
          prov_vol (While/apply
                     (DefBoolExpr
                       ;; we are going to dummy up so it will loop 3 times
                       (fn [j]
                         (let [v (.getv j "vol_count")
                               c (if (number? v) (+ 1 v) 0) ]
                           (.setv j "vol_count" c)
                           (< c 3))))
                     (DefWFTask
                       (fn [cur job arg]
                         (let [obj (.getv job "vol_count")]
                           (when (number? obj)
                             (if (== 2 obj)
                               (println "Step(3): Granted permission for user to mount "
                                        "this vol(id).\n")
                               (println "Step(3): Failed to contact vol - server, "
                                        "will retry again... (" obj ") ")))
                           nil))))
          ;; step4. pretend to write stuff to db. again, we are going to dummy up the case
          ;; where the db write fails a couple of times.
          ;; so again , we are using a while() to do that.
          save_sdb (While/apply
                     (DefBoolExpr
                       (fn [job]
                         ;; we are going to dummy up so it will loop 3 times
                         (let [v (.getv j "wdb_count")
                               c (if (number? v) (+ 1 v) 0) ]
                           (.setv j "wdb_count" c)
                           (< c 3))))
                     (DefWFTask
                       (fn [cur job arg]
                         (let [obj (.getv job "wdb_count")]
                           (when (number? obj)
                             (if (== 2 obj)
                               (println "Step(4): Wrote stuff to database successfully.\n")
                               (println "Step(4): Failed to contact db- server, "
                                        "will retry again... (" obj ") ")))
                           nil))))

          ;; this is the step where it will do the provisioning of the AMI and the EBS volume
          ;; in parallel.  To do that, we use a split-we want to fork off both tasks in parallel.  Since
          ;; we don't want to continue until both provisioning tasks are done. we use a AndJoin to hold/freeze
          ;; the workflow.
          Provision (-> (Split/applyAnd save_sdb)
                        (.includeMany prov_ami prov_vol))

          ;; do a final test to see what sort of response should we send back to the user.
          FinalTest (If/apply
                      (DefBoolExpr #(RandomBoolValue))
                      (DefWFTask 
                        #(println "Step(5): We'd probably return a 200 OK "
                                  "back to caller here.\n"))
                      (DefWFTask
                        #(println "Step(5): We'd probably return a 200 OK "
                                  "but with errors.\n"))) ]
      ;;
      ;; so, the workflow is a small (4 step) workflow, with the 3rd step (Provision) being
      ;; a split, which forks off more steps in parallel.
      (-> (Block/apply AuthUser)
          (.chainMany GetProfile Provision FinalTest))))

  (onError [_ err c] nil)
  (onStop [_ pipe] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoMain [] cmzlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ c] )

  (initialize [_]
    (println "Demo a set of workflow control features..." ))

  (configure [_ cfg] )

  (start [_] )

  (stop [_] )

  (dispose [_] ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)


