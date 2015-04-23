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

  czlabclj.xlib.dbio.composite

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [test-nonil notnil? Try!]]
        [czlabclj.xlib.dbio.core]
        [czlabclj.xlib.dbio.sql]
        [czlabclj.xlib.util.str :only [hgl?]])

  (:import  [com.zotohlab.frwk.dbio Transactable SQLr MetaCache DBAPI]
            [java.sql Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doExtraSQL ""

  ^String
  [^String sql extra]

  ;;TODO: extra is a map of things.
  sql)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mk-tx "Make a transactable-sql object."

  ^SQLr
  [^czlabclj.xlib.dbio.sql.SQLProcAPI proc
   ^MetaCache metaCache
   ^Connection conn]

  (let []
    (reify SQLr

      (findAll [this model extra] (.findSome this model {} extra))
      (findAll [this model] (.findAll this model {}))

      (findOne [this model filters]
        (when-let [rset (.findSome this model filters {}) ]
          (when-not (empty? rset) (first rset))))

      (findSome [this model filters] (.findSome this model filters {}))
      (findSome [this model filters extraSQL]
        (let [mcz ((.metas this) model)
              [wc pms]
              (SqlFilterClause mcz filters)
              tbl (Tablename mcz)
              s (str "SELECT * FROM " (ese tbl)) ]
          (if (hgl? wc)
            (.doQuery proc conn
                      (doExtraSQL (str s " WHERE " wc) extraSQL)
                      pms model)
            (.doQuery proc conn (doExtraSQL s extraSQL) [] model))) )

      (select [_ model sql params] (.doQuery proc conn sql params model) )
      (select [_ sql params] (.doQuery proc conn sql params) )

      (update [_ obj] (.doUpdate proc conn obj) )
      (delete [_ obj] (.doDelete proc conn obj) )
      (insert [_ obj] (.doInsert proc conn obj) )

      (execWithOutput [_ sql pms]
        (.doExecWithOutput proc conn sql pms { :pkey COL_ROWID } ) )

      (exec [_ sql pms] (.doExec proc conn sql pms) )

      (metas [_] (.getMetas metaCache))

      (countAll [_ model] (.doCount proc conn model) )
      (purge [_ model] (.doPurge proc conn model) )
  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompositeSQLr "A composite supports transactions."

  ^Transactable
  [^DBAPI db ]

  (let [mcache (.getMetaCache db)
        proc (MakeProc db) ]
    (test-nonil "sql-proc!" proc)
    (reify Transactable

      (execWith [this func]
        (with-local-vars [rc nil]
          (with-open [conn (.begin this) ]
            (test-nonil "sql-connection" conn)
            (try
              (var-set rc (func (mk-tx proc mcache conn)))
              (.commit this conn)
              @rc
              (catch Throwable e#
                (do
                  (.rollback this conn)
                  (log/warn e# "")
                  (throw e#))) ))))

      (rollback [_ conn] (Try! (.rollback ^Connection conn)))
      (commit [_ conn] (.commit ^Connection conn))

      (begin [_]
        (let [conn (.open db) ]
          (.setAutoCommit conn false)
          ;;(.setTransactionIsolation conn Connection/TRANSACTION_READ_COMMITTED)
          (.setTransactionIsolation conn Connection/TRANSACTION_SERIALIZABLE)
          conn))
  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private composite-eof nil)

