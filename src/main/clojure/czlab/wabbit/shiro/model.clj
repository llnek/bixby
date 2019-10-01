;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc ""
    :author "Kenneth Leung"}

  czlab.wabbit.shiro.model

  (:require [czlab.basal
             [util :as u]
             [io :as i]
             [log :as l]
             [core :as c]]
            [czlab.hoard
             [core :as hc]
             [drivers :as hd]]
            [czlab.wabbit.core :as b])

  (:import [java.sql Connection]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(hc/defschema
  auth-meta-cache
  (hc/dbmodel<> ::StdAddress
                (hc/dbfields {:addr1 {:size 255 :null false}
                              :addr2 {}
                              :state {:null false}
                              :city {:null false}
                              :zip {:null false}
                              :country {:null false}})
                (hc/dbindexes {:i1 #{:city :state :country}
                               :i2 #{:zip :country}
                               :state #{:state}
                               :zip #{:zip}}))
  (hc/dbmodel<> ::AuthRole
                (hc/dbfields {:name {:column "role_name" :null false}
                              :desc {:column "description" :null false}})
                (hc/dbuniques {:u1 #{:name}}))
  (hc/dbmodel<> ::LoginAccount
                (hc/dbfields {:acctid {:null false}
                              :email {:size 128}
                              ;;:salt { :size 128}
                              :passwd {:null false :domain :Password}})
                (hc/dbo2o :addr :cascade true :other ::StdAddress)
                (hc/dbuniques {:u2 #{:acctid}}))
  (hc/dbjoined<> ::AccountRoles ::LoginAccount ::AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn gen-auth-pluglet-ddl
  "Generate db ddl for the auth-plugin."
  ^String
  [spec]
  {:pre [(keyword? spec)]}
  (if (hc/match-spec?? spec)
    (hd/get-ddl auth-meta-cache spec)
    (hc/dberr! (u/rstr (b/get-rc-base) "db.unknown" (name spec)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn apply-ddl
  "" [arg]
  (cond
    (satisfies? hc/JdbcPool arg)
    (apply-ddl (:jdbc arg))
    (c/is? czlab.hoard.core.JdbcSpec arg)
    (when-some [t (hc/match-url?? (:url arg))]
      (c/wo* [^Connection c (hc/conn<> arg)]
        (hc/upload-ddl c (gen-auth-pluglet-ddl t))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn export-auth-pluglet-ddl
  "Output the auth-plugin ddl to file."
  [spec file]
  (i/spit-utf8 file (gen-auth-pluglet-ddl spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

