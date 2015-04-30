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

  czlabclj.xlib.dbio.simple

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.str :only [hgl?]]
        [czlabclj.xlib.dbio.core]
        [czlabclj.xlib.dbio.sql])

  (:import  [com.zotohlab.frwk.dbio DBAPI MetaCache SQLr]
            [java.sql Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- openDB "Connect to a database."

  ^Connection
  [^DBAPI db]

  (doto (.open db)
    (.setAutoCommit true)
    ;;(.setTransactionIsolation Connection/TRANSACTION_READ_COMMITTED)
    (.setTransactionIsolation Connection/TRANSACTION_SERIALIZABLE)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doExtraSQL ""

  ^String
  [^String sql extra]

  sql)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SimpleSQLr "Non transactional SQL object."

  ^SQLr
  [^DBAPI db]

  (let [^czlabclj.xlib.dbio.sql.SQLProcAPI
        proc (MakeProc db)]
    (reify SQLr

      (findAll [this model extra] (.findSome this model {} extra))
      (findAll [this model] (.findAll this model {}))

      (findOne [this model filters]
        (when-let [rset (.findSome this model filters {})]
          (when-not (empty? rset) (first rset))))

      (findSome [this  model filters] (.findSome this model filters {} ))

      (findSome [this model filters extraSQL]
        (with-open [conn (openDB db) ]
          (let [mcz ((.metas this) model)
                s (str "SELECT * FROM " (GTable mcz))
                [wc pms]
                (SqlFilterClause mcz filters) ]
            (if (hgl? wc)
              (.doQuery proc conn (doExtraSQL (str s " WHERE " wc)
                                              extraSQL)
                                  pms model)
              (.doQuery proc conn (doExtraSQL s extraSQL) [] model))) ))

      (metas [_] (-> db (.getMetaCache)(.getMetas)))

      (update [this obj]
        (with-open [ conn (openDB db) ]
          (.doUpdate proc conn obj) ))

      (delete [this obj]
        (with-open [conn (openDB db) ]
          (.doDelete proc conn obj) ))

      (insert [this obj]
        (with-open [conn (openDB db) ]
          (.doInsert proc conn obj) ))

      (select [this model sql params]
        (with-open [conn (openDB db) ]
          (.doQuery proc conn sql params model) ))

      (select [this sql params]
        (with-open [conn (openDB db) ]
          (.doQuery proc conn sql params) ))

      (execWithOutput [this sql pms]
        (with-open [conn (openDB db) ]
          (.doExecWithOutput proc conn
                                sql pms {:pkey COL_ROWID} )))

      (exec [this sql pms]
        (with-open [conn (openDB db) ]
          (doExec proc conn sql pms) ))

      (countAll [this model]
        (with-open [conn (openDB db) ]
          (.doCount proc conn model) ))

      (purge [this model]
        (with-open [conn (openDB db) ]
          (.doPurge proc conn model) ))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private simple-eof nil)

