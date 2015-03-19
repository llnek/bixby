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

(ns ^{:doc ""
      :author "kenl"}

  demo.steps.core

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [cmzlabclj.xlib.util.core :only [RandomBoolValue notnil?]]
        [cmzlabclj.xlib.util.str :only [nsb]]
        [cmzlabclj.tardis.core.wfs])

  (:import  [com.zotohlab.wflow FlowNode PTask Switch If Block
             Activity Split While
             PipelineDelegate]
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
    (DefWFTask (fn [c j a] (println "-> using facebook to login.\n")))

    "google+"
    (DefWFTask (fn [c j a] (println "-> using google+ to login.\n")))

    "openid"
    (DefWFTask (fn [c j a] (println "-> using open-id to login.\n")))

    (DefWFTask (fn [c j a] (println "-> using internal db to login.\n")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLoopXXX ""
  ;; we are going to dummy up so it will loop 3 times
  [^String fkey times]

  (fn [^Job j]
    (let [v (.getv j fkey)
          c (if (number? v) (+ 1 v) 0) ]
      (.setv j fkey c)
      (< c times))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeWhileBody ""

  [^String fkey limit ok nok]

  (fn [cur ^Job job arg]
    (let [obj (.getv job fkey)]
      (when (number? obj)
        (if (>= obj limit)
          (apply ok [obj])
          (apply nok [obj])))
      nil)
  ))

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
    (require 'demo.steps.core)
    ;; step1. choose a method to authenticate the user
    ;; here, we'll use a switch() to pick which method
    (let [AuthUser (-> (Switch/apply (DefChoiceExpr
                                       (fn [j]
                                         ;; hard code to use facebook in this example, but you
                                         ;; could check some data from the job, such as URI/Query params
                                         ;; and decide on which mth-value to switch() on.
                                         (println "Step(1): Choose an authentication method.")
                                         "facebook")))
                       (.withChoice "facebook" (getAuthMtd "facebook"))
                       (.withChoice "google+" (getAuthMtd "google+"))
                       (.withChoice "openid" (getAuthMtd "openid"))
                       (.withDft (getAuthMtd "db")))
    ;; step2.
          GetProfile (DefWFTask (fn [c j a] (println "Step(2): Get user profile\n"
                                          "-> user is superuser.\n")))
    ;; step3. we are going to dummy up a retry of 2 times to simulate network/operation
    ;; issues encountered with EC2 while trying to grant permission.
    ;; so here , we are using a while() to do that.
          prov_ami (While/apply
                     (DefBoolExpr (maybeLoopXXX "ami_count" 3))
                     (DefWFTask
                       (maybeWhileBody "ami_count" 3
                                       #(println "Step(3): Granted permission for user to launch "
                                                 "this ami(id).\n")
                                       #(println "Step(3): Failed to contact ami- server, "
                                                 "will retry again... (" % ") "))))
          ;; step3'. we are going to dummy up a retry of 2 times to simulate network/operation
          ;; issues encountered with EC2 while trying to grant volume permission.
          ;; so here , we are using a while() to do that.
          prov_vol (While/apply
                     (DefBoolExpr (maybeLoopXXX "vol_count" 3))
                     (DefWFTask
                       (maybeWhileBody "vol_count" 3
                                       #(println "Step(3): Granted permission for user to mount "
                                                 "this vol(id).\n")
                                       #(println "Step(3): Failed to contact vol - server, "
                                                 "will retry again... (" % ") "))))
          ;; step4. pretend to write stuff to db. again, we are going to dummy up the case
          ;; where the db write fails a couple of times.
          ;; so again , we are using a while() to do that.
          save_sdb (While/apply
                     (DefBoolExpr (maybeLoopXXX "wdb_count" 3))
                     (DefWFTask
                       (maybeWhileBody "wdb_count" 3
                                       #(println "Step(4): Wrote stuff to database successfully.\n")
                                       #(println "Step(4): Failed to contact db- server, "
                                                 "will retry again... (" % ") "))))
          ;; this is the step where it will do the provisioning of the AMI and the EBS volume
          ;; in parallel.  To do that, we use a split-we want to fork off both tasks in parallel.  Since
          ;; we don't want to continue until both provisioning tasks are done. we use a AndJoin to hold/freeze
          ;; the workflow.
          Provision (.includeMany (Split/applyAnd save_sdb)
                                  (into-array Activity [prov_ami prov_vol]))

          ;; do a final test to see what sort of response should we send back to the user.
          FinalTest (If/apply
                      (DefBoolExpr (fn [j] (RandomBoolValue)))
                      (DefWFTask
                        (fn [c j a] (println "Step(5): We'd probably return a 200 OK "
                                  "back to caller here.\n")))
                      (DefWFTask
                        (fn [c j a] (println "Step(5): We'd probably return a 200 OK "
                                  "but with errors.\n")))) ]
      ;;
      ;; so, the workflow is a small (4 step) workflow, with the 3rd step (Provision) being
      ;; a split, which forks off more steps in parallel.
      (.chainMany (Block/apply AuthUser)
                  (into-array Activity [GetProfile Provision FinalTest]))))

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


