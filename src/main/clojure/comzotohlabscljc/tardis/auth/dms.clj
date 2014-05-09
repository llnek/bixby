;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.auth.dms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.dbio.drivers])
  (:use [comzotohlabscljc.dbio.core])
  (:use [comzotohlabscljc.dbio.postgresql])
  (:use [comzotohlabscljc.dbio.h2])
  (:use [comzotohlabscljc.dbio.mysql])
  (:use [comzotohlabscljc.dbio.sqlserver])
  (:use [comzotohlabscljc.dbio.oracle])

  (:import (com.zotohlabs.frwk.dbio JDBCInfo Schema))
  (:import (java.io File))
  (:import (org.apache.commons.io FileUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 "czc.tardis.auth" StdAddress
  (WithDbFields {
    :addr1 { :size 200 :null false }
    :addr2 { :size 64}
    :city { :null false}
    :state {:null false}
    :zip {:null false}
    :country {:null false}
                   })
  (WithDbIndexes { :i1 #{ :city :state :country }
    :i2 #{ :zip :country }
    :state #{ :state }
    :zip #{ :zip } } ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 "czc.tardis.auth"  AuthRole
  (WithDbFields
    { :name { :column "role_name" :null false }
      :desc { :column "description" :null false }
     })
  (WithDbUniques
    { :u1 #{ :name }
     }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2  "czc.tardis.auth" LoginAccount
  (WithDbFields
    { :acctid { :null false }
      :email { :size 128 }
      ;;:salt { :size 128 }
      :passwd { :null false :domain :Password }
     })
  (WithDbAssocs
    { :roles { :kind :M2M
               :joined :czc.tardis.auth/AccountRole }
      :addr { :kind :O2O
              :cascade true
              :rhs :czc.tardis.auth/StdAddress }
     })
  (WithDbUniques
    { :u2 #{ :acctid }
     }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefJoined2 "czc.tardis.auth" AccountRole
           :czc.tardis.auth/LoginAccount
           :czc.tardis.auth/AuthRole)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype AuthPluginSchema []

  Schema

  (getModels [_] [ StdAddress AuthRole LoginAccount AccountRole] ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def AUTH-MCACHE (MakeMetaCache (AuthPluginSchema.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenerateAuthPluginDDL ""

  ^String
  [dbtype]

  (GetDDL AUTH-MCACHE
    (case dbtype
      (:postgres :postgresql) Postgresql
      :mysql MySQL
      :h2 H2
      (:sqlserver :mssql) SQLServer
      :oracle Oracle
      (DbioError (str "Unsupported database type: " dbtype)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ApplyAuthPluginDDL ""

  [^JDBCInfo jdbc]

  (let [ dbtype (MatchJdbcUrl (.getUrl jdbc)) ]
    (UploadDdl jdbc (GenerateAuthPluginDDL dbtype))
  ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExportAuthPluginDDL ""

  [dbtype ^File file]

  (FileUtils/writeStringToFile file (GenerateAuthPluginDDL dbtype) "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private dms-eof nil)

