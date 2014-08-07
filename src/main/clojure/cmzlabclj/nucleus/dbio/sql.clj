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


(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.nucleus.dbio.sql

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [FlattenNil notnil? NowJTstamp nnz] ]
        [cmzlabclj.nucleus.dbio.core]
        [cmzlabclj.nucleus.util.meta :only [BytesClass CharsClass] ]
        [cmzlabclj.nucleus.util.str :only [AddDelim! nsb strim] ]
        [cmzlabclj.nucleus.util.io :only [ReadChars ReadBytes ] ]
        [cmzlabclj.nucleus.util.dates :only [GmtCal] ])

  (:import  [java.util Calendar GregorianCalendar TimeZone]
            [com.zotohlab.frwk.dbio MetaCache DBIOError OptLockError]
            [java.math BigDecimal BigInteger]
            [java.io Reader InputStream]
            [com.zotohlab.frwk.dbio DBAPI]
            [com.zotohlab.frwk.io XData]
            [java.sql ResultSet Types SQLException
                      DatabaseMetaData ResultSetMetaData
                      Date Timestamp Blob Clob
                      Statement PreparedStatement Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Tablename ""

  (^String
    [mdef]
    (:table mdef))

  (^String
    [mid cache]
    (:table (cache mid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Colname ""

  (^String
    [fdef]
    (:column fdef))

  (^String
    [fid zm]
    (:column (get (:fields (meta zm)) fid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtUpdateWhere ""

  ^String
  [lock zm]

  (str (ese (Colname :rowid zm))
       "=?"
       (if lock
           (str " AND " (ese (Colname :verid zm)) "=?")
           "")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- lockError ""

  [^String opcode cnt ^String table rowID]

  (when (== cnt 0)
    (throw (OptLockError. opcode table rowID))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SqlFilterClause ""

  [zm filters]

  (let [flds (:fields (meta zm))
        wc (reduce #(let [k (first %2)
                          fld (get flds k)
                          c (if (nil? fld)
                              k
                              (:column fld)) ]
                      (AddDelim! %1 " AND "
                                 (str (ese c)
                                      (if (nil? (last %2))
                                        " IS NULL "
                                        " = ? "))))
                   (StringBuilder.)
                   (seq filters)) ]
    [ (nsb wc) (FlattenNil (vals filters)) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readCol ""

  ^Object
  [sqlType pos ^ResultSet rset]

  (let [obj (.getObject rset (int pos))
        ^InputStream
        inp (condp instance? obj
              Blob (.getBinaryStream ^Blob obj)
              InputStream obj
              nil)
        ^Reader
        rdr (condp instance? obj
              Clob (.getCharacterStream ^Clob obj)
              Reader obj
              nil) ]
    (cond
      (notnil? rdr) (with-open [r rdr] (ReadChars r))
      (notnil? inp) (with-open [p inp] (ReadBytes p))
      :else obj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readOneCol ""

  [sqlType pos ^ResultSet rset]

  (condp == (int sqlType)
    Types/TIMESTAMP (.getTimestamp rset (int pos) (GmtCal))
    Types/DATE (.getDate rset (int pos) (GmtCal))
    (readCol sqlType pos rset)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- model-injtor "Row is a transient object."

  [cache zm row cn ct cv]

  (let [cols (:columns (meta zm))
        fdef (get cols cn) ]
    (if (nil? fdef)
      row
      (assoc! row (:id fdef) cv))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- std-injtor "Generic resultset, no model defined.
                  Row is a transient object."

  [row ^String cn ct cv]

  (assoc! row (keyword (cstr/upper-case cn)) cv))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- row2obj ""

  [finj ^ResultSet rs ^ResultSetMetaData rsmeta]

  (with-local-vars [row (transient {}) ]
    (doseq [pos (range 1 (+ (.getColumnCount rsmeta) 1)) ]
      (let [cn (.getColumnName rsmeta (int pos))
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

  (condp instance? p
    String (.setString ps pos p)
    Long (.setLong ps pos p)
    Integer (.setInt ps pos p)
    Short (.setShort ps pos p)
    BigDecimal (.setBigDecimal ps pos p)
    BigInteger (.setBigDecimal ps
                               pos (BigDecimal. ^BigInteger p))
    InputStream (.setBinaryStream ps pos p)
    Reader (.setCharacterStream ps pos p)
    Blob (.setBlob ps ^long pos ^Blob p)
    Clob (.setClob ps ^long pos ^Clob p)
    (CharsClass) (.setString ps pos (String. ^chars p))
    (BytesClass) (.setBytes ps pos ^bytes p)
    XData (.setBinaryStream ps pos (.stream ^XData p))
    Boolean (.setInt ps pos (if p 1 0))
    Double (.setDouble ps pos p)
    Float (.setFloat ps pos p)
    Timestamp (.setTimestamp ps pos p (GmtCal))
    Date (.setDate ps pos p (GmtCal))
    Calendar (.setTimestamp ps pos
                            (Timestamp. (.getTimeInMillis ^Calendar p))
                            (GmtCal))
    (DbioError (str "Unsupported param type: " (type p)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mssql-tweak-sqlstr ""

  [^String sqlstr token ^String cmd]

  (loop [stop false sql sqlstr]
    (if stop
      sql
      (let [lcs (cstr/lower-case sql)
            pos (.indexOf lcs (name token))
            rc (if (< pos 0)
                 []
                 [(.substring sql 0 pos)
                  (.substring sql pos)]) ]
        (if (empty? rc)
          (recur true sql)
          (recur false (str (first rc) " WITH (" cmd ") " (last rc)) ))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jiggleSQL ""

  [^DBAPI db ^String sqlstr]

  (let [sql (strim sqlstr)
        lcs (cstr/lower-case sql)
        v (.vendor db)   ]
    (if (= :sqlserver (:id v))
      (cond
        (.startsWith lcs "select")
        (mssql-tweak-sqlstr sql :where "NOLOCK")
        (.startsWith lcs "delete")
        (mssql-tweak-sqlstr sql :where "ROWLOCK")
        (.startsWith lcs "update")
        (mssql-tweak-sqlstr sql :set "ROWLOCK")
        :else sql)
      sql)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- build-stmt ""

  ^PreparedStatement
  [db ^Connection conn ^String sqlstr params]

  (let [sql sqlstr ;; (jiggleSQL db sqlstr)
        ps (if (insert? sql)
             (.prepareStatement conn
                                sql
                                Statement/RETURN_GENERATED_KEYS)
             (.prepareStatement conn sql)) ]
    (log/debug "Building SQLStmt: {}" sql)
    (doseq [n (seq (range 0 (count params))) ]
      (setBindVar ps (inc n) (nth params n)))
    ps
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleGKeys ""

  [^ResultSet rs cnt options]

  (let [rc (if (== cnt 1)
             (.getObject rs 1)
             (.getLong rs (nsb (:pkey options)))) ]
    {:1 rc}
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

  ^cmzlabclj.nucleus.dbio.sql.SQueryAPI
  [^MetaCache metaCache db ^Connection conn]

  (reify SQueryAPI

    (sql-executeWithOutput [this sql pms options]
      (with-open [stmt (build-stmt db conn sql pms) ]
        (if (> (.executeUpdate stmt) 0)
          (with-open [rs (.getGeneratedKeys stmt) ]
            (let [cnt (if (nil? rs)
                        0
                        (-> (.getMetaData rs)
                            (.getColumnCount))) ]
              (if (and (> cnt 0)
                       (.next rs))
                (handleGKeys rs cnt options)
                {}
                ))))))

    (sql-select [this sql pms ]
      (sql-select this sql pms
                  (partial row2obj std-injtor)
                  identity))

    (sql-select [this sql pms func postFunc]
      (with-open [stmt (build-stmt db conn sql pms)
                  rs (.executeQuery stmt) ]
        (let [rsmeta (.getMetaData rs) ]
          (loop [sum (transient [])
                 ok (.next rs) ]
            (if (not ok)
              (persistent! sum)
              (recur (conj! sum (postFunc (func rs rsmeta)))
                     (.next rs)))))))

    (sql-execute [this sql pms]
      (with-open [stmt (build-stmt db conn sql pms) ]
        (.executeUpdate stmt)))  ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol SQLProcAPI

  "Methods supported by a SQL Processor."

  (doExecuteWithOutput [_ conn sql params options] )
  (doQuery [_ conn sql params model]
           [_ conn sql params] )
  (doExecute [_ conn sql params] )
  (doCount [_  conn model] )
  (doPurge [_  conn model] )
  (doDelete [_  conn pojo] )
  (doInsert [_  conn pojo] )
  (doUpdate [_  conn pojo] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- insert-fields "Format sql string for insert."

  [flds obj s1 s2]

  (with-local-vars [ps (transient []) ]
    (doseq [[k v] (seq obj) ]
      (let [fdef (flds k) ]
        (when (and (notnil? fdef)
                   (not (:auto fdef))
                   (not (:system fdef)))
          (AddDelim! s1 "," (ese (Colname fdef)))
          (AddDelim! s2 "," (if (nil? v) "NULL" "?"))
          (when-not (nil? v)
            (var-set ps (conj! @ps v))))))
    (persistent! @ps)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- update-fields "Format sql string for update."

  [flds obj ^StringBuilder sb1]

  (with-local-vars [ps (transient []) ]
    (doseq [[k v] (seq obj) ]
      (let [fdef (flds k) ]
        (when (and (notnil? fdef)
                   (:updatable fdef)
                   (not (:auto fdef))
                   (not (:system fdef)) )
          (doto sb1
            (AddDelim! "," (ese (Colname fdef)))
            (.append (if (nil? v) "=NULL" "=?")))
          (when-not (nil? v)
            (var-set ps (conj! @ps v))))))
    (persistent! @ps)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postFmtModelRow ""

  [model obj]

  (let [mm {:typeid model
            :verid (:verid obj)
            :rowid (:rowid obj)
            :last-modify (:last-modify obj) }
        rc (with-meta (-> obj
                          (DbioClrFld :rowid)
                          (DbioClrFld :verid)
                          (DbioClrFld :last-modify)) mm) ]
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeProc ""

  ^cmzlabclj.nucleus.dbio.sql.SQLProcAPI
  [^MetaCache metaCache ^DBAPI db]

  (let [metas (.getMetas metaCache) ]
    (reify SQLProcAPI

      (doQuery [_ conn sql pms model]
        (let [zm (metas model) ]
          (when (nil? zm)
                (DbioError (str "Unknown model " model)))
          (let [px (partial model-injtor metaCache zm)
                pf (partial row2obj px)
                f2 #(postFmtModelRow model %) ]
            (-> (make-sql metaCache db conn)
                (.sql-select sql pms pf f2)))))

      (doQuery [_ conn sql pms]
        (-> (make-sql metaCache db conn)
            (.sql-select sql pms )))

      (doCount [this conn model]
        (let [rc (doQuery this conn
                          (str "SELECT COUNT(*) FROM "
                               (ese (Tablename model metas))) [] ) ]
          (if (empty? rc)
            0
            (last (first (seq (first rc)))))))

      (doPurge [_ conn model]
        (let [sql (str "DELETE FROM "
                       (ese (Tablename model metas))) ]
          (do
            (-> (make-sql metaCache db conn)
                (.sql-execute sql []))
            nil)))

      (doDelete [this conn obj]
        (let [info (meta obj) model (:typeid info)
              zm (metas model) ]
          (when (nil? zm)
            (DbioError (str "Unknown model " model)))
          (let [lock (.supportsOptimisticLock db)
                table (Tablename zm)
                rowid (:rowid info)
                verid (:verid info)
                p (if lock [rowid verid] [rowid] )
                w (fmtUpdateWhere lock zm)
                cnt (doExecute this conn
                               (str "DELETE FROM "
                                    (ese table)
                                    " WHERE "
                                    w)
                               p) ]
            (when lock (lockError "delete" cnt table rowid))
            cnt)))

      (doInsert [this conn obj]
        (let [info (meta obj) model (:typeid info)
              zm (metas model) ]
          (when (nil? zm)
            (DbioError (str "Unknown model " model)))
          (let [lock (.supportsOptimisticLock db)
                flds (:fields (meta zm))
                s2 (StringBuilder.)
                s1 (StringBuilder.)
                table (Tablename zm)
                now (NowJTstamp)
                pms (insert-fields flds obj s1 s2) ]
            (if (== (.length s1) 0)
              nil
              (let [out (doExecuteWithOutput this conn
                                             (str "INSERT INTO "
                                                  (ese table)
                                                  "(" s1
                                                  ") VALUES (" s2
                                                  ")" )
                                             pms
                                             {:pkey (Colname :rowid zm)} ) ]
                (if (empty? out)
                  (DbioError (str "Insert requires row-id to be returned."))
                  (log/debug "Exec-with-out " out))
                (let [wm {:rowid (:1 out) :verid 0} ]
                  (when-not (number? (:rowid wm))
                    (DbioError (str "RowID data-type must be Long.")))
                  (vary-meta obj MergeMeta wm))))
          )))

      (doUpdate [this conn obj]
        (let [info (meta obj) model (:typeid info)
              zm (metas model) ]
          (when (nil? zm)
            (DbioError (str "Unknown model " model)))
          (let [lock (.supportsOptimisticLock db)
                cver (nnz (:verid info))
                flds (:fields (meta zm))
                table (Tablename zm)
                rowid (:rowid info)
                sb1 (StringBuilder.)
                now (NowJTstamp)
                nver (inc cver)
                pms (update-fields flds obj sb1) ]
            (if (== (.length sb1) 0)
              nil
              (with-local-vars [ ps (transient pms) ]
                (-> (AddDelim! sb1 "," (ese (Colname :last-modify zm)))
                    (.append "=?"))
                (var-set  ps (conj! @ps now))
                (when lock ;; up the version
                  (-> (AddDelim! sb1 "," (ese (Colname :verid zm)))
                      (.append "=?"))
                  (var-set ps (conj! @ps nver)))
                ;; for the where clause
                (var-set ps (conj! @ps rowid))
                (when lock (var-set  ps (conj! @ps cver)))
                (let [cnt (doExecute this conn
                                     (str "UPDATE "
                                          (ese table)
                                          " SET "
                                          sb1
                                          " WHERE "
                                          (fmtUpdateWhere lock zm))
                                     (persistent! @ps)) ]
                  (when lock (lockError "update" cnt table rowid))
                  (vary-meta obj MergeMeta
                             { :verid nver :last-modify now }))))
        )))

        (doExecuteWithOutput [this conn sql pms options]
          (-> (make-sql metaCache db conn)
              (.sql-executeWithOutput sql pms options)))

        (doExecute [this conn sql pms]
          (-> (make-sql metaCache db conn)
              (.sql-execute sql pms)))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private sql-eof nil)

