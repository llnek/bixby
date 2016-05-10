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
    [czlab.xlib.i18n.resources :refer [RStr]]
    [czlab.xlib.util.str :refer [ToKW]]
    [czlab.xlib.util.logging :as log])

  (:use
    [czlab.xlib.dbio.drivers]
    [czlab.xlib.dbio.core]
    [czlab.xlib.dbio.postgresql]
    [czlab.xlib.dbio.h2]
    [czlab.xlib.dbio.mysql]
    [czlab.xlib.dbio.sqlserver]
    [czlab.xlib.dbio.oracle])

  (:import
    [com.zotohlab.frwk.dbio JDBCInfo JDBCPool Schema]
    [java.sql Connection]
    [java.io File]
    [com.zotohlab.frwk.i18n I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String _NSP "czc.skaro.auth")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 _NSP StdAddress
  (WithDbFields
    {:addr1 {:size 255 :null false }
     :addr2 { }
     :city {:null false}
     :state {:null false}
     :zip {:null false}
     :country {:null false} })
  (WithDbIndexes
    {:i1 [ :city :state :country ]
     :i2 [ :zip :country ]
     :state [ :state ]
     :zip [ :zip ] } ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 _NSP AuthRole
  (WithDbFields
    {:name {:column "role_name" :null false }
     :desc {:column "description" :null false } })
  (WithDbUniques
    {:u1 [ :name ] }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 _NSP LoginAccount
  (WithDbFields
    {:acctid {:null false }
     :email {:size 128 }
      ;;:salt { :size 128 }
     :passwd {:null false :domain :Password } })
  (WithDbAssocs
    {:roles {:kind :M2M
             :joined (ToKW _NSP "AccountRole") }
     :addr {:kind :O2O
            :cascade true
            :other (ToKW _NSP "StdAddress") } })
  (WithDbUniques
    {:u2 [ :acctid ] }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefJoined2 _NSP AccountRole
           (ToKW _NSP "LoginAccount")
           (ToKW _NSP "AuthRole"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce AUTH-MCACHE

  (-> (reify Schema
        (getModels [_]
          [LoginAccount AccountRole
           StdAddress AuthRole]))
      (MetaCache* )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenerateAuthPluginDDL

  "Generate db ddl for the auth-plugin"

  ^String
  [dbtype]

  (GetDDL AUTH-MCACHE
    (case dbtype
      (:postgres :postgresql) Postgresql
      (:sqlserver :mssql) SQLServer
      :mysql MySQL
      :h2 H2
      :oracle Oracle
      (DbioError (RStr (I18N/getBase)
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
    (when-some [dbtype (MatchJdbcUrl (.getUrl this)) ]
      (with-open [conn (DbConnection* this) ]
        (UploadDdl conn (GenerateAuthPluginDDL dbtype)))))

  JDBCPool
  (applyDDL [this]
    (when-some [dbtype (MatchJdbcUrl (.dbUrl this)) ]
      (UploadDdl this (GenerateAuthPluginDDL dbtype)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExportAuthPluginDDL

  "Output the auth-plugin ddl to file"

  [dbtype ^File file]

  (spit file (GenerateAuthPluginDDL dbtype) :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

