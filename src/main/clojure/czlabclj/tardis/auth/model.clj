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
      :author "kenl" }

  czlabclj.tardis.auth.model

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.i18n.resources :only [RStr]]
        [czlabclj.xlib.util.str :only [ToKW]]
        [czlabclj.xlib.dbio.drivers]
        [czlabclj.xlib.dbio.core]
        [czlabclj.xlib.dbio.postgresql]
        [czlabclj.xlib.dbio.h2]
        [czlabclj.xlib.dbio.mysql]
        [czlabclj.xlib.dbio.sqlserver]
        [czlabclj.xlib.dbio.oracle])

  (:import  [com.zotohlab.frwk.dbio JDBCInfo JDBCPool Schema]
            [java.sql Connection]
            [java.io File]
            [com.zotohlab.frwk.i18n I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^String ^:private _NSP "czc.tardis.auth")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 _NSP StdAddress
  (WithDbFields {
    :addr1 { :size 255 :null false }
    :addr2 { }
    :city { :null false}
    :state {:null false}
    :zip {:null false}
    :country {:null false} })
  (WithDbIndexes { :i1 #{ :city :state :country }
    :i2 #{ :zip :country }
    :state #{ :state }
    :zip #{ :zip } } ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 _NSP  AuthRole
  (WithDbFields
    { :name { :column "role_name" :null false }
      :desc { :column "description" :null false } })
  (WithDbUniques
    { :u1 #{ :name } }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 _NSP LoginAccount
  (WithDbFields
    { :acctid { :null false }
      :email { :size 128 }
      ;;:salt { :size 128 }
      :passwd { :null false :domain :Password } })
  (WithDbAssocs
    { :roles { :kind :M2M
               :joined (ToKW _NSP "AccountRole") }
      :addr { :kind :O2O
              :cascade true
              :other (ToKW _NSP "StdAddress") } })
  (WithDbUniques
    { :u2 #{ :acctid } }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefJoined2 _NSP AccountRole
           (ToKW _NSP "LoginAccount")
           (ToKW _NSP "AuthRole"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype AuthPluginSchema []

  Schema

  (getModels [_] [StdAddress AuthRole LoginAccount AccountRole] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def AUTH-MCACHE (MakeMetaCache (AuthPluginSchema.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenerateAuthPluginDDL "Generate db ddl for the auth-plugin."

  ^String
  [dbtype]

  (GetDDL AUTH-MCACHE
    (case dbtype
      (:postgres :postgresql) Postgresql
      :mysql MySQL
      :h2 H2
      (:sqlserver :mssql) SQLServer
      :oracle Oracle
      (DbioError (RStr (I18N/getBase)
                       "db.unknown"
                       [ (name dbtype) ])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ApplyAuthPluginDDL "Upload the auth-plugin ddl to db." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ApplyAuthPluginDDL JDBCInfo

  [^JDBCInfo jdbc]

  (when-let [dbtype (MatchJdbcUrl (.getUrl jdbc)) ]
    (with-open [conn (MakeConnection jdbc) ]
      (UploadDdl conn (GenerateAuthPluginDDL dbtype)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ApplyAuthPluginDDL JDBCPool

  [^JDBCPool pool]

  (when-let [dbtype (MatchJdbcUrl (.dbUrl pool)) ]
    (UploadDdl pool (GenerateAuthPluginDDL dbtype))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExportAuthPluginDDL "Output the auth-plugin ddl to file."

  [dbtype ^File file]

  (spit file (GenerateAuthPluginDDL dbtype) :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ns-unmap *ns* '->AuthPluginSchema)
(def ^:private model-eof nil)

