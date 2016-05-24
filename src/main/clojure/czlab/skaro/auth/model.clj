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
      :author "kenl" }

  czlab.skaro.auth.model

  (:require
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.str :refer [toKW]]
    [czlab.xlib.logging :as log])

  (:use
    [czlab.dbio.drivers]
    [czlab.dbio.core]
    [czlab.dbio.postgresql]
    [czlab.dbio.h2]
    [czlab.dbio.mysql]
    [czlab.dbio.sqlserver]
    [czlab.dbio.oracle])

  (:import
    [czlab.dbio JDBCInfo JDBCPool Schema]
    [java.sql Connection]
    [java.io File]
    [czlab.xlib I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String _NSP "czc.skaro.auth")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defModel2 _NSP StdAddress
  (withFields
    {:addr1 {:size 255 :null false }
     :addr2 { }
     :city {:null false}
     :state {:null false}
     :zip {:null false}
     :country {:null false} })
  (withIndexes
    {:i1 [ :city :state :country ]
     :i2 [ :zip :country ]
     :state [ :state ]
     :zip [ :zip ] } ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defModel2 _NSP AuthRole
  (withFields
    {:name {:column "role_name" :null false }
     :desc {:column "description" :null false } })
  (withUniques
    {:u1 [ :name ] }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defModel2 _NSP LoginAccount
  (withFields
    {:acctid {:null false }
     :email {:size 128 }
      ;;:salt { :size 128 }
     :passwd {:null false :domain :Password } })
  (withAssocs
    {:roles {:kind :M2M
             :joined (toKW _NSP "AccountRole") }
     :addr {:kind :O2O
            :cascade true
            :other (toKW _NSP "StdAddress") } })
  (withUniques
    {:u2 [ :acctid ] }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defJoined2 _NSP AccountRole
           (toKW _NSP "LoginAccount")
           (toKW _NSP "AuthRole"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce AUTH-MCACHE

  (-> (reify Schema
        (getModels [_]
          [LoginAccount AccountRole
           StdAddress AuthRole]))
      (mkMetaCache )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn generateAuthPluginDDL

  "Generate db ddl for the auth-plugin"

  ^String
  [dbtype]

  (getDDL AUTH-MCACHE
    (case dbtype
      (:postgres :postgresql) Postgresql
      (:sqlserver :mssql) SQLServer
      :mysql MySQL
      :h2 H2
      :oracle Oracle
      (mkDbioError (rstr (I18N/getBase)
                       "db.unknown"
                       (name dbtype))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PluginDDL

  "Upload the auth-plugin ddl to db"

  (applyDDL [_]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(extend-protocol PluginDDL

  JDBCInfo
  (applyDDL [this]
    (when-some [dbtype (matchJdbcUrl (.getUrl this)) ]
      (with-open [conn (dbConnection this)]
        (uploadDdl conn (generateAuthPluginDDL dbtype)))))

  JDBCPool
  (applyDDL [this]
    (when-some [dbtype (matchJdbcUrl (.dbUrl this)) ]
      (uploadDdl this (generateAuthPluginDDL dbtype)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn exportAuthPluginDDL

  "Output the auth-plugin ddl to file"

  [dbtype ^File file]

  (spit file (generateAuthPluginDDL dbtype) :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


