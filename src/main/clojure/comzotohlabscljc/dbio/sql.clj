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

  comzotohlabscljc.dbio.sql

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [FlattenNil notnil? NowJTstamp nnz] ])
  (:use [comzotohlabscljc.util.meta :only [BytesClass CharsClass] ])
  (:use [comzotohlabscljc.util.str :only [AddDelim! nsb strim] ])
  (:use [comzotohlabscljc.util.io :only [ReadChars ReadBytes ] ])
  (:use [comzotohlabscljc.util.dates :only [GmtCal] ])
  (:require [comzotohlabscljc.dbio.core
             :as dbcore :only [MergeMeta ese DbioError] ])
  (:import (java.util Calendar GregorianCalendar TimeZone))
  (:import (com.zotohlabs.frwk.dbio MetaCache DBIOError OptLockError))
  (:import (java.math BigDecimal BigInteger))
  (:import (java.io Reader InputStream))
  (:import (com.zotohlabs.frwk.dbio DBAPI))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (java.sql ResultSet Types SQLException
                     DatabaseMetaData ResultSetMetaData
                     Date Timestamp Blob Clob
                     Statement PreparedStatement Connection)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Tablename ""

  ( ^String
    [mdef]
    (:table mdef))

  ( ^String
    [mid cache]
    (:table (cache mid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Colname ""

  ( ^String
    [fdef]
    (:column fdef))

  ( ^String
    [fid zm]
    (:column (get (:fields (meta zm)) fid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtUpdateWhere ""

  ^String
  [lock zm]

  (str (dbcore/ese (Colname :rowid zm))
       "=?"
       (if lock
           (str " AND " (dbcore/ese (Colname :verid zm)) "=?")
           "")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- lockError ""

  [^String opcode cnt ^String table rowID]

  (when (= cnt 0)
    (throw (OptLockError. opcode table rowID))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SqlFilterClause ""

  [zm filters]

  (let [ flds (:fields (meta zm))
         wc (reduce (fn [^StringBuilder sum en]
                      (let [ k (first en)
                             fld (get flds k)
                             c (if (nil? fld)
                                   k
                                   (:column fld)) ]
                        (AddDelim! sum " AND "
                          (str (dbcore/ese c) (if (nil? (last en))
                                     " IS NULL "
                                     " = ? ")))))
                    (StringBuilder.)
                    (seq filters)) ]
    [ (nsb wc) (FlattenNil (vals filters)) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readCol ""

  ^Object
  [sqlType pos ^ResultSet rset]

  (let [ obj (.getObject rset (int pos))
         ^InputStream inp (cond
                            (instance? Blob obj) (.getBinaryStream ^Blob obj)
                            (instance? InputStream obj) obj
                            :else nil)
         ^Reader rdr (cond
                       (instance? Clob obj) (.getCharacterStream ^Clob obj)
                       (instance? Reader obj) obj
                       :else nil) ]
    (cond
      (notnil? rdr) (with-open [r rdr] (ReadChars r))
      (notnil? inp) (with-open [p inp] (ReadBytes p))
      :else obj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readOneCol ""

  [sqlType pos ^ResultSet rset]

  (case sqlType
      Types/TIMESTAMP (.getTimestamp rset (int pos) (GmtCal))
      Types/DATE (.getDate rset (int pos) (GmtCal))
      (readCol sqlType pos rset)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; row is a transient object.
(defn- model-injtor ""

  [cache zm row cn ct cv]

  (let [ cols (:columns (meta zm))
         fdef (get cols cn) ]
    (if (nil? fdef)
      row
      (assoc! row (:id fdef) cv))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; generic resultset, no model defined.
;; row is a transient object.
(defn- std-injtor ""

  [row ^String cn ct cv]

  (assoc! row (keyword (cstr/upper-case cn)) cv))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- row2obj ""

  [finj ^ResultSet rs ^ResultSetMetaData rsmeta]

  (with-local-vars [ row (transient {}) ]
    (doseq [ pos (range 1 (+ (.getColumnCount rsmeta) 1)) ]
      (let [ cn (.getColumnName rsmeta (int pos))
             ct (.getColumnType rsmeta (int pos))
             cv (readOneCol ct (int pos) rs) ]
        (var-set row (finj @row cn ct cv))))
    (persistent! @row)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- insert? ""

  [^String sql]

  (.startsWith (cstr/lower-case (strim sql)) "insert"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setBindVar ""

  [^PreparedStatement ps pos p]

  (cond
    (instance? String p) (.setString ps pos p)
    (instance? Long p) (.setLong ps pos p)
    (instance? Integer p) (.setInt ps pos p)
    (instance? Short p) (.setShort ps pos p)

    (instance? BigDecimal p) (.setBigDecimal ps pos p)
    (instance? BigInteger p) (.setBigDecimal ps
                                             pos
                                             (BigDecimal. ^BigInteger p))

    (instance? InputStream p) (.setBinaryStream ps pos p)
    (instance? Reader p) (.setCharacterStream ps pos p)

    (instance? Blob p) (.setBlob ps ^long pos ^Blob p)
    (instance? Clob p) (.setClob ps ^long pos ^Clob p)

    (instance? (CharsClass) p) (.setString ps pos (String. ^chars p))
    (instance? (BytesClass) p) (.setBytes ps pos ^bytes p)
    (instance? XData p) (.setBinaryStream ps pos (.stream ^XData p))

    (instance? Boolean p) (.setInt ps pos (if p 1 0))
    (instance? Double p) (.setDouble ps pos p)
    (instance? Float p) (.setFloat ps pos p)

    (instance? Timestamp p) (.setTimestamp ps pos p (GmtCal))
    (instance? Date p) (.setDate ps pos p (GmtCal))
    (instance? Calendar p) (.setTimestamp ps pos
                                          (Timestamp. (.getTimeInMillis ^Calendar p))
                                          (GmtCal))

    :else (dbcore/DbioError (str "Unsupported param type: " (type p)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mssql-tweak-sqlstr ""

  [^String sqlstr token ^String cmd]

  (loop [ stop false sql sqlstr ]
    (if stop
      sql
      (let [ lcs (cstr/lower-case sql)
             pos (.indexOf lcs (name token))
             rc (if (< pos 0)
                  []
                  [(.substring sql 0 pos) (.substring sql pos)]) ]
        (if (empty? rc)
          (recur true sql)
          (recur false (str (first rc) " WITH (" cmd ") " (last rc)) ))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jiggleSQL ""

  [^DBAPI db ^String sqlstr]

  (let [ sql (strim sqlstr)
         lcs (cstr/lower-case sql)
         v (.vendor db)   ]
    (if (= :sqlserver (:id v))
      (cond
        (.startsWith lcs "select") (mssql-tweak-sqlstr sql :where "NOLOCK")
        (.startsWith lcs "delete") (mssql-tweak-sqlstr sql :where "ROWLOCK")
        (.startsWith lcs "update") (mssql-tweak-sqlstr sql :set "ROWLOCK")
        :else sql)
      sql)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- build-stmt ""

  ^PreparedStatement
  [db ^Connection conn ^String sqlstr params]

  (let [ sql sqlstr ;; (jiggleSQL db sqlstr)
         ps (if (insert? sql)
              (.prepareStatement conn
                                 sql
                                 Statement/RETURN_GENERATED_KEYS)
              (.prepareStatement conn sql)) ]
    (log/debug "building SQLStmt: {}" sql)
    (doseq [n (seq (range 0 (count params))) ]
      (setBindVar ps (inc n) (nth params n)))
    ps
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleGKeys ""

  [^ResultSet rs cnt options]

  (let [ rc (cond
              (= cnt 1) (.getObject rs 1)
              :else (.getLong rs (nsb (:pkey options)))) ]
    { :1 rc }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private SQueryAPI

  (sql-select [_ sql pms row-provider-func post-func] [_ sql pms] )
  (sql-executeWithOutput [_  sql pms options] )
  (sql-execute [_  sql pms] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-sql ""

  ^comzotohlabscljc.dbio.sql.SQueryAPI
  [^MetaCache metaCache db ^Connection conn]

  (reify SQueryAPI

    (sql-executeWithOutput [this sql pms options]
      (with-open [ stmt (build-stmt db conn sql pms) ]
        (if (> (.executeUpdate stmt) 0)
          (with-open [ rs (.getGeneratedKeys stmt) ]
            (let [ cnt (if (nil? rs)
                          0
                          (-> (.getMetaData rs) (.getColumnCount))) ]
              (if (and (> cnt 0) (.next rs))
                (handleGKeys rs cnt options)
                {}
                ))))))

    (sql-select [this sql pms ]
      (sql-select this sql pms
                  (partial row2obj std-injtor) (fn [a] a)))

    (sql-select [this sql pms func postFunc]
      (with-open [ stmt (build-stmt db conn sql pms) ]
        (with-open [ rs (.executeQuery stmt) ]
          (let [ rsmeta (.getMetaData rs) ]
            (loop [ sum (transient [])
                    ok (.next rs) ]
              (if (not ok)
                (persistent! sum)
                (recur (conj! sum (postFunc (func rs rsmeta))) (.next rs))))))))

    (sql-execute [this sql pms]
      (with-open [ stmt (build-stmt db conn sql pms) ]
        (.executeUpdate stmt)))  ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol SQLProcAPI

  ""

  (doQuery [_ conn sql params model] [_ conn sql params] )
  (doExecuteWithOutput [_ conn sql params options] )
  (doExecute [_ conn sql params] )
  (doCount [_  conn model] )
  (doPurge [_  conn model] )
  (doDelete [_  conn pojo] )
  (doInsert [_  conn pojo] )
  (doUpdate [_  conn pojo] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Format sql string for insert.
;;
(defn- insert-fields ""

  [flds obj s1 s2]

  (with-local-vars [ ps (transient []) ]
    (doseq [ [k v] (seq obj) ]
      (let [ fdef (flds k) ]
        (when (and (notnil? fdef)
                   (not (:auto fdef))
                   (not (:system fdef)))
          (AddDelim! s1 "," (dbcore/ese (Colname fdef)))
          (AddDelim! s2 "," (if (nil? v) "NULL" "?"))
          (when-not (nil? v)
            (var-set ps (conj! @ps v))))))

    (persistent! @ps)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Format sql string for update.
;;
(defn- update-fields ""

  [flds obj ^StringBuilder sb1]

  (with-local-vars [ ps (transient []) ]
    (doseq [ [k v] (seq obj) ]
      (let [ fdef (flds k) ]
        (when (and (notnil? fdef)
                   (:updatable fdef)
                   (not (:auto fdef)) (not (:system fdef)) )
          (doto sb1
            (AddDelim! "," (dbcore/ese (Colname fdef)))
            (.append (if (nil? v) "=NULL" "=?")))
          (when-not (nil? v)
            (var-set ps (conj! @ps v))))))
    (persistent! @ps)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postFmtModelRow ""

  [model obj]

  (let [ mm { :typeid model
               :verid (:verid obj)
               :rowid (:rowid obj)
               :last-modify (:last-modify obj) }
         rc (with-meta (-> obj
                           (dbcore/DbioClrFld :rowid)
                           (dbcore/DbioClrFld :verid)
                           (dbcore/DbioClrFld :last-modify)) mm) ]
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeProc ""

  ^comzotohlabscljc.dbio.sql.SQLProcAPI
  [ ^MetaCache metaCache ^DBAPI db  ]

  (let [ metas (.getMetas metaCache) ]
    (reify SQLProcAPI

      (doQuery [_ conn sql pms model]
        (let [ zm (metas model) ]
          (when (nil? zm)
                (dbcore/DbioError (str "Unknown model " model)))
          (let [ px (partial model-injtor metaCache zm)
                 pf (partial row2obj px)
                 f2 (fn [obj] (postFmtModelRow model obj)) ]
            (-> (make-sql metaCache db conn)
                (.sql-select sql pms pf f2)))))

      (doQuery [_ conn sql pms]
        (let []
          (-> (make-sql metaCache db conn)
              (.sql-select sql pms ))) )

      (doCount [this conn model]
        (let [ rc (doQuery this conn
                    (str "SELECT COUNT(*) FROM "
                         (dbcore/ese (Tablename model metas))) [] ) ]
          (if (empty? rc)
            0
            (last (first (seq (first rc)))))))

      (doPurge [_ conn model]
        (let [ sql (str "DELETE FROM " (dbcore/ese (Tablename model metas))) ]
          (do (-> (make-sql metaCache db conn)
                  (.sql-execute sql []))
              nil)))

      (doDelete [this conn obj]
        (let [ info (meta obj) model (:typeid info)
               zm (metas model) ]
          (when (nil? zm) (dbcore/DbioError (str "Unknown model " model)))
          (let [ lock (.supportsOptimisticLock db)
                 table (Tablename zm)
                 rowid (:rowid info)
                 verid (:verid info)
                 p (if lock [rowid verid] [rowid] )
                 w (fmtUpdateWhere lock zm)
                 cnt (doExecute this conn
                       (str "DELETE FROM " (dbcore/ese table) " WHERE " w)
                       p) ]
            (when lock (lockError "delete" cnt table rowid))
            cnt)))

      (doInsert [this conn obj]
        (let [ info (meta obj) model (:typeid info)
               zm (metas model) ]
          (when (nil? zm) (dbcore/DbioError (str "Unknown model " model)))
          (let [ lock (.supportsOptimisticLock db)
                 flds (:fields (meta zm))
                 s2 (StringBuilder.)
                 s1 (StringBuilder.)
                 table (Tablename zm)
                 now (NowJTstamp)
                 pms (insert-fields flds obj s1 s2) ]
            (if (== (.length s1) 0)
              nil
              (let [ out (doExecuteWithOutput this conn
                            (str "INSERT INTO " (dbcore/ese table) "(" s1 ") VALUES (" s2 ")" )
                            pms { :pkey (Colname :rowid zm) } ) ]
                (if (empty? out)
                  (dbcore/DbioError (str "Insert requires row-id to be returned."))
                  (log/debug "exec-with-out " out))
                (let [ wm { :rowid (:1 out) :verid 0 } ]
                  (when-not (number? (:rowid wm))
                    (dbcore/DbioError (str "RowID data-type must be Long.")))
                  (vary-meta obj dbcore/MergeMeta wm))))
          )))

      (doUpdate [this conn obj]
        (let [ info (meta obj) model (:typeid info)
               zm (metas model) ]
          (when (nil? zm) (dbcore/DbioError (str "Unknown model " model)))
          (let [ lock (.supportsOptimisticLock db)
                 cver (nnz (:verid info))
                 flds (:fields (meta zm))
                 table (Tablename zm)
                 rowid (:rowid info)
                 sb1 (StringBuilder.)
                 now (NowJTstamp)
                 nver (inc cver)
                 pms (update-fields flds obj sb1) ]
            (if (= (.length sb1) 0)
              nil
              (with-local-vars [ ps (transient pms) ]
                (-> (AddDelim! sb1 "," (dbcore/ese (Colname :last-modify zm)))
                    (.append "=?"))
                (var-set  ps (conj! @ps now))
                (when lock ;; up the version
                  (-> (AddDelim! sb1 "," (dbcore/ese (Colname :verid zm)))
                      (.append "=?"))
                  (var-set ps (conj! @ps nver)))
                ;; for the where clause
                (var-set ps (conj! @ps rowid))
                (when lock (var-set  ps (conj! @ps cver)))
                (let [ cnt (doExecute this conn
                              (str "UPDATE " (dbcore/ese table) " SET " sb1 " WHERE "
                                  (fmtUpdateWhere lock zm))
                                      (persistent! @ps)) ]
                  (when lock (lockError "update" cnt table rowid))
                  (vary-meta obj dbcore/MergeMeta
                             { :verid nver :last-modify now }))))
        )))

        (doExecuteWithOutput [this conn sql pms options]
          (-> (make-sql metaCache db conn)
              (.sql-executeWithOutput sql pms options)))

        (doExecute [this conn sql pms]
          (-> (make-sql metaCache db conn) (.sql-execute sql pms)))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private sql-eof nil)

