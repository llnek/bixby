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

  comzotohlabscljc.dbio.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.set :as cset])
  (:require [comzotohlabscljc.crypto.codec :as codec ])
  (:use [comzotohlabscljc.util.str
         :only [strim Embeds? nsb HasNocase? hgl?] ])
  (:use [comzotohlabscljc.util.core
         :only [TryC Try! RootCause StripNSPath Interject
                ternary notnil? nnz nbf juid] ])
  (:use [comzotohlabscljc.util.meta :only [ForName] ])
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (com.zotohlabs.frwk.dbio MetaCache Schema
                                    SQLr JDBCPool JDBCInfo))
  (:import (java.sql SQLException DatabaseMetaData
                     Connection Driver DriverManager))
  (:import (java.util GregorianCalendar TimeZone Properties))
  (:import (java.lang Math))
  (:import (com.zotohlabs.frwk.dbio DBIOError))
  (:import (com.jolbox.bonecp BoneCP BoneCPConfig))
  (:import (org.apache.commons.lang3 StringUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^MetaCache ^:dynamic *META-CACHE* nil)
(def ^:dynamic *USE_DDL_SEP* true)
(def ^:dynamic *DDL_BVS* nil)
(def ^:dynamic *JDBC-INFO* nil)
(def ^String DDL_SEP "-- :")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro uc-ent ^String [ent] `(cstr/upper-case (name ~ent)))
(defmacro lc-ent ^String [ent] `(cstr/lower-case (name ~ent)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro GetTypeId [model] `(:typeid (meta ~model)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ese "Escape string entity for sql."

  (^String [ent] (uc-ent ent))

  (^String [ch ent] (str ch (uc-ent ent) ch))

  (^String [c1 ent c2] (str c1 (uc-ent ent) c2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MergeMeta ""

  [m1 m2]

  (merge m1 m2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro eseOID [] `(ese "DBIO_ROWID"))
(defmacro eseVID [] `(ese "DBIO_VERID"))
(defmacro eseLHS [] `(ese "LHS_ROWID"))
(defmacro eseRHS [] `(ese "RHS_ROWID"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeJdbc "Make a JDBCInfo record."

  ^JDBCInfo
  [^String id
   cfg
   ^comzotohlabscljc.crypto.codec.Password pwdObj]

  ;;(debug "JDBC id= " id ", cfg = " cfg)
  (reify JDBCInfo
    (getUser [_] (:user cfg))
    (getDriver [_] (:d cfg))
    (getId [_] id)
    (getUrl [_] (:url cfg))
    (getPwd [_] (nsb pwdObj))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def Postgresql :postgresql)
(def SQLServer :sqlserver)
(def Oracle :oracle)
(def MySQL :mysql)
(def H2 :h2)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def DBTYPES {
    :sqlserver { :test-string "select count(*) from sysusers" }
    :postgresql { :test-string "select 1" }
    :mysql { :test-string "select version()" }
    :h2  { :test-string "select 1" }
    :oracle { :test-string "select 1 from DUAL" }
  })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioError ""

  [^String msg]

  (throw (DBIOError. msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioScopeType ""

  [t]

  (keyword (str *ns* "/" t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetVendor ""

  [^String product]

  (let [ lp (cstr/lower-case product) ]
    (cond
      (Embeds? lp "microsoft")
      :sqlserver
      (Embeds? lp "postgres")
      :postgresql
      (Embeds? lp "oracle")
      :oracle
      (Embeds? lp "mysql")
      :mysql
      (Embeds? lp "h2")
      :h2
      :else
      (DbioError (str "Unknown db product " product)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Concise ""

  [obj]

  { :rowid (:rowid (meta obj))
    :verid (:verid (meta obj)) } )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtfkey ""

  [p1 p2]

  (keyword (str "fk_" (name p1) "_" (name p2))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MatchDbType ""

  [^String dbtype]

  (let [ kw (keyword (cstr/lower-case dbtype)) ]
    (if (nil? (get DBTYPES kw))
         nil
         kw)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MatchJdbcUrl ""

  [^String url]

  (let [ ss (seq (StringUtils/split url \:)) ]
    (if (> (count ss) 1)
        (MatchDbType (nth ss 1))
        nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; data modelling
;;
(def JOINED-MODEL-MONIKER :czc.dbio.core/dbio-joined-model)
(def BASEMODEL-MONIKER :czc.dbio.core/dbio-basemodel)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioModel

  ([^String nm] (DbioModel *ns* nm))

  ([^String nsp ^String nm]
    {
      :id (keyword (str nsp "/" nm))
      :table (cstr/upper-case nm)
      :parent nil
      :abstract false
      :system false
      :mxm false
      :indexes {}
      :uniques {}
      :fields {}
      :assocs {} }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro DefModel2  ""

  [ nsp modelname & body ]

  `(def ~modelname
     (-> (DbioModel ~nsp ~(name modelname))
                 ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro DefModel "Define a data model."

  [modelname & body]

  `(def ~modelname
     (-> (DbioModel ~(name modelname))
                 ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro DefJoined2 ""

  [nsp modelname lhs rhs]

  `(def ~modelname
      (-> (DbioModel ~nsp ~(name modelname))
                (WithDbParentModel JOINED-MODEL-MONIKER)
                (WithDbJoinedModel ~lhs ~rhs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro DefJoined ""

  [modelname lhs rhs]

  `(def ~modelname
      (-> (DbioModel ~(name modelname))
                (WithDbParentModel JOINED-MODEL-MONIKER)
                (WithDbJoinedModel ~lhs ~rhs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbParentModel ""

  [pojo par]

  (assoc pojo :parent par))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbJoinedModel ""

  [pojo lhs rhs]

  (let [ a1 { :kind :MXM :rhs lhs :fkey :lhs_rowid }
         a2 { :kind :MXM :rhs rhs :fkey :rhs_rowid }
         am (:assocs pojo)
         m2 (-> am
                (assoc :lhs a1)
                (assoc :rhs a2)) ]
    (-> pojo
      (assoc :assocs m2)
      (assoc :mxm true))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbTablename ""

  [pojo tablename]

  (assoc pojo :table tablename))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbIndexes ""

  [pojo indices]

  (Interject pojo :indexes #(merge % indices) ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbUniques ""

  [pojo uniqs]

  (Interject pojo :uniques #(merge %  uniqs) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getDftFldObj ""

  [fid]

  { :column (cstr/upper-case (name fid))
    :size 255
    :id fid
    :domain :String
    :assoc-key false
    :pkey false
    :null true
    :auto false
    :dft nil
    :updatable true
    :system false
    :index "" } )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbField ""

  [pojo fid fdef]

  (let [ fd (-> (merge (getDftFldObj fid) fdef)
                (assoc :id (keyword fid)) ) ]
    (Interject pojo :fields #(assoc % fid fd))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbFields ""

  [pojo flddefs]

  (with-local-vars [rcmap pojo]
    (doseq [ [k v] (seq flddefs) ]
      (var-set rcmap (WithDbField @rcmap k v)))
    @rcmap
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbAssoc ""

  [pojo aid adef]

  (let [ dft { :kind nil :rhs nil :fkey "" :cascade false }
         pid (:id pojo)
         ad (merge dft adef)
         a2 (case (:kind ad)
              (:O2O :O2M) (assoc ad :fkey (fmtfkey pid aid))
              (:M2M :MXM) ad
              (DbioError (str "Invalid assoc def " adef))) ]
    (Interject pojo :assocs #(assoc % aid a2))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbAssocs ""

  [pojo assocs]

  (with-local-vars [ rcmap pojo ]
    (doseq [ [k v] (seq assocs) ]
      (var-set rcmap (WithDbAssoc @rcmap k v)))
    @rcmap
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WithDbAbstract ""

  [pojo]

  (assoc pojo :abstract true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- WithDbSystem ""

  [pojo]

  (assoc pojo :system true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nested-merge ""

  [src des]

  (cond
    (and (set? src)(set? des)) (cset/union src des)
    (and (map? src)(map? des)) (merge src des)
    :else des
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defining the base model here.
;;
(DefModel2 "czc.dbio.core" dbio-basemodel
  (WithDbAbstract)
  (WithDbSystem)
  (WithDbFields {
    :rowid {:column "DBIO_ROWID" :pkey true :domain :Long
            :auto true :system true :updatable false}
    :verid {:column "DBIO_VERSION" :domain :Long :system true
            :dft [ 0 ] }
    :last-modify {:column "DBIO_LASTCHANGED" :domain :Timestamp
               :system true :dft [""] }
    :created-on {:column "DBIO_CREATED_ON" :domain :Timestamp
                  :system true :dft [""] :updatable false}
    :created-by {:column "DBIO_CREATED_BY" :system true :domain :String } }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(DefModel2 "czc.dbio.core" dbio-joined-model
  (WithDbAbstract)
  (WithDbSystem)
  (WithDbFields {
    :lhs-typeid {:column "LHS_TYPEID" }
    :lhs-oid {:column "LHS_ROWID" :domain :Long :null false}
    :rhs-typeid {:column "RHS_TYPEID" }
    :rhs-oid {:column "RHS_ROWID" :domain :Long :null false} }) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSchema ""

  ^Schema
  [theModels]

  (reify Schema
    (getModels [_] theModels)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolve-assocs ""

  [ms]

  (let [ fdef { :domain :Long :assoc-key true } ]
    (with-local-vars [ rc (transient {}) xs (transient {}) ]
      ;; create placeholder maps for each model, to hold new fields from assocs.
      (doseq [ [k m] (seq ms) ] (var-set rc (assoc! @rc k {} )))
      ;; as we find new assoc fields, add them to the placeholder maps.
      (doseq [ [k m] (seq ms) ]
        (let [ socs (:assocs m) ]
          (doseq [ [x s] (seq socs) ]
            (case (:kind s)
              (:O2O :O2M)
              (let [ rhs (get @rc (:rhs s))
                     zm (get @rc k)
                     fid (:fkey s)
                     ft (merge (getDftFldObj fid) fdef) ]
                (var-set rc (assoc! @rc (:rhs s) (assoc rhs fid ft))))
              nil))))
      ;; now walk through all the placeholder maps and merge those new
      ;; fields to the actual models.
      (let [ tm (persistent! @rc) ]
        (doseq [ [k v] (seq tm) ]
          (let [ zm (get ms k)
                 fs (:fields zm) ]
            (var-set xs (assoc! @xs k (assoc zm :fields (merge fs v))))))
        (persistent! @xs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ensure that all user defined models are all derived from the system base
;; model.
;;
(defn- resolve-parent ""

  [ms model]

  (let [ par (:parent model) ]
    (cond
      (keyword? par)
      (if (nil? (get ms par))
          (DbioError (str "Unknown parent model " par))
          model)

      (nil? par)
      (assoc model :parent BASEMODEL-MONIKER)

      :else
      (DbioError (str "Invalid parent " par)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Idea is walk through all models and ultimately
;; link it's root to base-model.
;;
(defn- resolve-parents ""

  [ms]

  (persistent! (reduce (fn [sum en]
                          (let [ rc (resolve-parent ms (last en)) ]
                            (assoc! sum (:id rc) rc)))
                       (transient {})
                       (seq ms))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Turn the list of models into a map of models, keyed by the model id.
;;
(defn- mapize-models ""

  [ms]

  (persistent! (reduce (fn [sum n] (assoc! sum (:id n) n))
                       (transient {})
                       (seq ms))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- collect-db-xxx-filter ""

  [a b]

  (cond
    (keyword? b)
    :keyword

    (map? b)
    :map

    :else
    (DbioError (str "Invalid arg " b))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CollectDbFields collect-db-xxx-filter)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CollectDbFields :keyword

  [cache modelid]

  (let [ mm (get cache modelid) ]
    (when (nil? mm) (log/warn "unknown database model id " modelid))
    (CollectDbFields cache mm)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CollectDbFields :map

  [cache zm]

  (let [ par (:parent zm) ]
    (if (nil? par)
        (merge {} (:fields zm))
        (merge {} (CollectDbFields cache par)(:fields zm)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CollectDbIndexes collect-db-xxx-filter)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CollectDbIndexes :keyword

  [cache modelid]

  (let [ mm (get cache modelid) ]
    (when (nil? mm) (log/warn "unknown model id " modelid))
    (CollectDbIndexes cache mm)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CollectDbIndexes :map

  [cache zm]

  (let [ par (:parent zm) ]
    (if (nil? par)
        (merge {} (:indexes zm))
        (merge {} (CollectDbIndexes cache par) (:indexes zm)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CollectDbUniques collect-db-xxx-filter)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CollectDbUniques :keyword

  [cache modelid]

  (let [ mm (get cache modelid) ]
    (when (nil? mm) (log/warn "unknown model id " modelid))
    (CollectDbUniques cache mm)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CollectDbUniques :map

  [cache zm]

  (let [ par (:parent zm) ]
    (if (nil? par)
        (merge {} (:uniques zm))
        (merge {} (CollectDbUniques cache par) (:uniques zm)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create a map of fields keyed by the column name.
;;
(defn- colmap-fields ""

  [flds]

  (with-local-vars [ sum (transient {}) ]
    (doseq [ [k v] (seq flds) ]
      (let [ cn (cstr/upper-case (nsb (:column v))) ]
        (var-set sum (assoc! @sum cn v))))
    (persistent! @sum)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Inject extra meta-data properties into each model.  Each model will have
;; its (complete) set of fields keyed by column nam or field id.
;;
(defn- meta-models ""

  [cache]

  (with-local-vars [ sum (transient {}) ]
    (doseq [ [k m] (seq cache) ]
      (let [ flds (CollectDbFields cache m)
             cols (colmap-fields flds) ]
        (var-set sum
                 (assoc! @sum k
                         (with-meta m
                                    { :columns cols
                                      :fields flds } ) ))))
    (persistent! @sum)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeMetaCache ""

  ^MetaCache
  [^Schema schema]

  (let [ ms (if (nil? schema)
                {}
                (mapize-models (.getModels schema)))
         m2 (if (empty? ms)
                {}
                (-> (assoc ms JOINED-MODEL-MONIKER dbio-joined-model)
                    (resolve-parents)
                    (resolve-assocs)
                    (assoc BASEMODEL-MONIKER dbio-basemodel)
                    (meta-models))) ]
    (reify MetaCache
      (getMetas [_] m2))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- safeGetConn ""

  ^Connection
  [^JDBCInfo jdbc]

  (let [ user (.getUser jdbc)
         dv (.getDriver jdbc)
         url (.getUrl jdbc)
         d (if (hgl? url) (DriverManager/getDriver url))
         p (if (hgl? user)
               (doto (Properties.)
                     (.put "password" (nsb (.getPwd jdbc)))
                     (.put "user" user)
                     (.put "username" user))
               (Properties.)) ]
    (when (nil? d) (DbioError (str "Can't load Jdbc Url: " url)))
    (when (and (hgl? dv)
               (not= (-> d (.getClass) (.getName)) dv))
      (log/warn "Expected " dv ", loaded with driver: " (.getClass d)))
    (.connect d url p)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeConnection ""

  ^Connection
  [^JDBCInfo jdbc]

  (let [ url (.getUrl jdbc)
         ^Connection
         conn (if (hgl? (.getUser jdbc))
                  (safeGetConn jdbc)
                  (DriverManager/getConnection url)) ]
    (when (nil? conn) (DbioError (str "Failed to create db connection: " url)))
    (doto conn
          (.setTransactionIsolation Connection/TRANSACTION_READ_COMMITTED))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn TestConnection ""

  [jdbc]

  (TryC (.close (MakeConnection jdbc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ResolveVendor class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ResolveVendor JDBCInfo

  [jdbc]

  (with-open [ conn (MakeConnection jdbc) ]
    (ResolveVendor conn)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ResolveVendor Connection

  [^Connection conn]

  (let [ md (.getMetaData conn) ]
    (-> { :id (maybeGetVendor (.getDatabaseProductName md)) }
        (assoc :version (.getDatabaseProductVersion md))
        (assoc :name (.getDatabaseProductName md))
        (assoc :quote-string (.getIdentifierQuoteString md))
        (assoc :url (.getURL md))
        (assoc :user (.getUserName md))
        (assoc :lcis (.storesLowerCaseIdentifiers md))
        (assoc :ucis (.storesUpperCaseIdentifiers md))
        (assoc :mcis (.storesMixedCaseIdentifiers md)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti TableExist? (fn [a b] (class a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod TableExist? JDBCInfo

  [jdbc ^String table]

  (with-open [ conn (MakeConnection jdbc) ]
    (TableExist? conn table)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod TableExist? Connection

  [^Connection conn ^String table]

  (with-local-vars [ rc false ]
    (Try!
      (let [ mt (.getMetaData conn)
             tbl (cond
                    (.storesUpperCaseIdentifiers mt)
                    (cstr/upper-case table)
                    (.storesLowerCaseIdentifiers mt)
                    (cstr/lower-case table)
                    :else table) ]
        (with-open [ res (.getColumns mt nil nil tbl nil) ]
          (when (and (notnil? res) (.next res))
            (var-set rc true)))
      ))
    @rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti RowExist? (fn [a b] (class a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod RowExist? JDBCInfo

  [jdbc ^String table]

  (with-open [ conn (MakeConnection jdbc) ]
    (RowExist? conn table)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod RowExist? Connection

  [^Connection conn ^String table]

  (with-local-vars [ rc false ]
    (Try!
      (let [ sql (str "SELECT COUNT(*) FROM  "
                      (cstr/upper-case table)) ]
        (with-open [ stmt (.createStatement conn) ]
          (with-open [ res (.executeQuery stmt sql) ]
            (when (and (notnil? res) (.next res))
              (var-set rc (> (.getInt res (int 1)) 0)))))
      ))
    @rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- load-columns

  [^DatabaseMetaData mt ^String catalog ^String schema ^String table]

  (with-local-vars [ pkeys #{} cms {} ]
    (with-open [ rs (.getPrimaryKeys mt catalog schema table) ]
      (loop [ sum (transient #{}) more (.next rs) ]
        (if (not more)
          (var-set pkeys (persistent! sum))
          (recur
            (conj! sum (cstr/upper-case (.getString rs (int 4))) )
            (.next rs))
        )))
    (with-open [ rs (.getColumns mt catalog schema table "%") ]
      (loop [ sum (transient {}) more (.next rs) ]
        (if (not more)
          (var-set cms (persistent! sum))
          (let [ opt (not= (.getInt rs (int 11)) DatabaseMetaData/columnNoNulls)
                 cn (cstr/upper-case (.getString rs (int 4)))
                 ctype (.getInt rs (int 5)) ]
            (recur
              (assoc! sum (keyword cn)
                  { :column cn :sql-type ctype :null opt
                    :pkey (clojure.core/contains? @pkeys cn) })
              (.next rs)))
        )))
    (with-meta @cms { :supportsGetGeneratedKeys
                      (.supportsGetGeneratedKeys mt)
                      :supportsTransactions
                      (.supportsTransactions mt) } )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LoadTableMeta ""

  [^Connection conn ^String table]

  (let [ dbv (ResolveVendor conn)
         mt (.getMetaData conn)
         catalog nil
         schema (if (= (:id dbv) :oracle) "%" nil)
         tbl (cond
                (.storesUpperCaseIdentifiers mt)
                (cstr/upper-case table)
                (.storesLowerCaseIdentifiers mt)
                (cstr/lower-case table)
                :else table) ]
    ;; not good, try mixed case... arrrrrrrrrrhhhhhhhhhhhhhh
    ;;rs = m.getTables( catalog, schema, "%", null)
    (load-columns mt catalog schema tbl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makePool ""

  ^JDBCPool
  [jdbc ^BoneCP impl]

  (let [ dbv (ResolveVendor jdbc) ]
    (reify

      Object

      (finalize [this] (Try! (.shutdown this)))

      JDBCPool

      (shutdown [_] (.shutdown impl))
      (vendor [_] dbv)
      (nextFree  [_]
        (try
            (.getConnection impl)
          (catch Throwable e#
            (DbioError (str "No free connection."))))) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeDbPool ""

  ([^JDBCInfo jdbc] (MakeDbPool jdbc {}))

  ([^JDBCInfo jdbc options]
    (let [ ^String dv (.getDriver jdbc)
           bcf (BoneCPConfig.) ]
      ;;(log/debug "Driver : " dv)
      ;;(log/debug "URL : "  (.getUrl jdbc))
      (when (hgl? dv) (ForName dv))
      (doto bcf
            (.setPartitionCount (Math/max 1 (nnz (:partitions options))))
            (.setLogStatementsEnabled (nbf (:debug options)))
            (.setPassword (nsb (.getPwd jdbc)))
            (.setJdbcUrl (.getUrl jdbc))
            (.setUsername (.getUser jdbc))
            (.setIdleMaxAgeInSeconds (* 60 60 24)) ;; 1 day
            (.setMaxConnectionsPerPartition (Math/max 2 (nnz (:max-conns options))))
            (.setMinConnectionsPerPartition (Math/max 1 (nnz (:min-conns options))))
            (.setPoolName (juid))
            (.setAcquireRetryDelayInMs 5000)
            (.setConnectionTimeoutInMs  (Math/max 5000 (nnz (:max-conn-wait options))))
            (.setDefaultAutoCommit false)
            (.setAcquireRetryAttempts 1))
      (makePool jdbc (BoneCP. bcf)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitLines ""

  [^String lines]

  (with-local-vars [ w (.length DDL_SEP)
                     rc []
                     s2 lines ]
    (loop [ sum (transient [])
            ddl lines
            pos (.indexOf ddl DDL_SEP) ]
      (if (< pos 0)
        (do (var-set rc (persistent! sum)) (var-set s2 (strim ddl)))
        (let [ nl (strim (.substring ddl 0 pos))
               d2 (.substring ddl (+ pos @w))
               p2 (.indexOf d2 DDL_SEP) ]
          (recur (conj! sum nl) d2 p2))))
    (if (hgl? @s2)
      (conj @rc @s2)
      @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeOK ""

  [^String dbn ^Throwable e]

  (let [ oracle (Embeds? (nsb dbn) "oracle")
         ee (RootCause e)
         ec (if (instance? SQLException ee)
                (.getErrorCode ^SQLException ee) ) ]
    (if (nil? ec)
        (throw e)

        (cond
          (and oracle (= 942 ec)(= 1418 ec)(= 2289 ec)(= 0 ec))
          true

          :else
          (throw e)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti UploadDdl (fn [a b] (class a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod UploadDdl JDBCInfo

  [jdbc ^String ddl]

  (with-open [ conn (MakeConnection jdbc) ]
     (UploadDdl conn ddl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod UploadDdl Connection

  [^Connection conn ^String ddl]

  (log/debug "\n" ddl)
  (let [ dbn (cstr/lower-case (-> (.getMetaData conn)(.getDatabaseProductName)))
         lines (splitLines ddl) ]
    (.setAutoCommit conn true)
    (doseq [ ^String line (seq lines) ]
      (let [ ln (StringUtils/strip (strim line) ";") ]
        (when (and (hgl? ln) (not= (cstr/lower-case ln) "go"))
          (try
            (with-open [ stmt (.createStatement conn) ]
              (.executeUpdate stmt ln))
            (catch SQLException e#
              (maybeOK dbn e#))))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioCreateObj

  "Creates a blank object of the given type.
  model : keyword, the model type id."

  [model]

  (with-meta {} { :typeid model } ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioSetFld ""

  [pojo fld value]

  (assoc pojo (keyword fld) value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioClrFld ""

  [pojo fld]

  (dissoc pojo (keyword fld)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioGetFld ""

  [pojo fld]

  (get pojo (keyword fld)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioGetAssoc

  "Get the assoc definition.
  mc : meta cache.
  zm : the model.
  id : assoc id."

  [mc zm id]

  (if (nil? zm)
    nil
    (let [ rc ((:assocs zm) id) ]
      (if (nil? rc)
        (DbioGetAssoc mc (mc (:parent zm)) id)
        rc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; handling assocs
(defn- dbio-get-o2x ""

  [ctx lhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         zm (mc (GetTypeId lhsObj))
         ac (DbioGetAssoc mc zm (:as ctx))
         rt (:cast ctx)
         fid (:fkey ac)
         fv (:rowid (meta lhsObj)) ]
    (if (nil? ac)
        (DbioError "Unknown assoc " (:as ctx))
        [ (:with ctx) (if (nil? rt) (:rhs ac) rt) { fid fv} ])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dbio-set-o2x ""

  [ctx lhsObj rhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         zm (mc (GetTypeId lhsObj))
         ac (DbioGetAssoc mc zm (:as ctx))
         fv (:rowid (meta lhsObj))
         fid (:fkey ac) ]
    (when (nil? ac) (DbioError "Unknown assoc " (:as ctx)))
    (let [ x (-> (DbioCreateObj (GetTypeId rhsObj))
                 (DbioSetFld fid fv)
                 (vary-meta MergeMeta (Concise rhsObj)))
         y (.update ^SQLr (:with ctx) x) ]
      [ lhsObj (merge y (dissoc rhsObj fid)) ])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; one to many assocs
;;
(defn DbioGetO2M ""

  [ctx lhsObj]

  (let [ [sql rt pms] (dbio-get-o2x ctx lhsObj) ]
    (if (notnil? rt)
      (.findSome ^SQLr sql rt pms))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioSetO2M ""

  [ctx lhsObj rhsObj]

  (dbio-set-o2x ctx lhsObj rhsObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioAddO2M ""

  [ctx lhsObj rhsObjs ]

  (with-local-vars [ rc (transient []) ]
    (doseq [ r (seq rhsObjs) ]
      (var-set rc
               (conj! @rc
                      (last (dbio-set-o2x ctx lhsObj r)))))
    [ lhsObj (persistent! @rc) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioClrO2M ""

  [ctx lhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         zm (mc (GetTypeId lhsObj))
         ac (DbioGetAssoc mc zm (:as ctx))
         fv (:rowid (meta lhsObj))
         rt (:cast ctx)
         rp (if (nil? rt) (:rhs ac) rt)
         fid (:fkey ac) ]
    (when (nil? ac)(DbioError "Unknown assoc " (:as ctx)))
    (.execute ^SQLr (:with ctx)
              (str "delete from "
                   (ese (:table (mc rp)))
                   " where "
                   (ese fid)
                   " = ?")
              [ fv ])
    lhsObj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; one to one assocs
;;
(defn DbioGetO2O ""

  [ctx lhsObj]

  (let [ [sql rt pms] (dbio-get-o2x ctx lhsObj) ]
    (.findOne ^SQLr sql rt pms)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioSetO2O ""

  [ctx lhsObj rhsObj]

  (dbio-set-o2x ctx lhsObj rhsObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioClrO2O ""

  [ctx lhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         zm (mc (GetTypeId lhsObj))
         ac (DbioGetAssoc mc
                          zm
                          (:as ctx))
         ^SQLr sql (:with ctx)
         fv (:rowid (meta lhsObj))
         rt (:cast ctx)
         fid (:fkey ac) ]
    (when (nil? ac)
          (DbioError "Unknown assoc " (:as ctx)))
    (let [ y (.findOne sql
                       (ternary rt (:rhs ac))
                       { fid fv } ) ]
      (when-not (nil? y)
        (let [ x (vary-meta (-> (DbioCreateObj (GetTypeId y))
                                (DbioSetFld fid nil))
                            MergeMeta (meta y)) ]
          (.update sql x))))
    lhsObj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; many to many assocs
;;
(defn DbioSetM2M ""

  [ctx lhsObj rhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         lid (GetTypeId lhsObj)
         rid (GetTypeId rhsObj)
         lv (:rowid (meta lhsObj))
         rv (:rowid (meta rhsObj))
         zm (mc lid)
         ac (DbioGetAssoc mc
                          zm
                          (:as ctx))
         mm (mc (:joined ac))
         ml (:rhs (:lhs (:assocs mm)))
         rl (:rhs (:rhs (:assocs mm)))
         x (DbioCreateObj (:id mm))
         y  (if (= ml lid)
              (-> x
                (DbioSetFld :lhs-typeid (StripNSPath lid))
                (DbioSetFld :lhs-oid lv)
                (DbioSetFld :rhs-typeid (StripNSPath rid))
                (DbioSetFld :rhs-oid rv))
              (-> x
                (DbioSetFld :lhs-typeid (StripNSPath rid))
                (DbioSetFld :lhs-oid rv)
                (DbioSetFld :rhs-typeid (StripNSPath lid))
                (DbioSetFld :rhs-oid lv))) ]
    (.insert ^SQLr (:with ctx) y)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioClrM2M

  ([ctx lhsObj] (DbioClrM2M ctx lhsObj nil))

  ([ctx lhsObj rhsObj]
    (let [ mc (.getMetas *META-CACHE*)
           lid (GetTypeId lhsObj)
           rid (GetTypeId rhsObj)
           lv (:rowid (meta lhsObj))
           rv (:rowid (meta rhsObj))
           zm (mc lid)
           ac (DbioGetAssoc mc
                            zm
                            (:as ctx))
           mm (mc (:joined ac))
           flds (:fields (meta mm))
           ml (:rhs (:lhs (:assocs mm)))
           rl (:rhs (:rhs (:assocs mm)))
           ^SQLr sql (:with ctx)
           [x y a b]
           (if (= ml lid)
              [ (:column (:lhs-oid flds)) (:column (:lhs-typeid flds))
                (:column (:rhs-oid flds)) (:column (:rhs-typeid flds)) ]
              [ (:column (:rhs-oid flds)) (:column (:rhs-typeid flds))
                (:column (:lhs-oid flds)) (:column (:lhs-typeid flds)) ]) ]
      (when (nil? ac) (DbioError "Unkown assoc " (:as ctx)))
      (if (nil? rhsObj)
          (.execute
            sql
            (str "delete from "
                 (ese (:table mm))
                 " where " (ese x) " =? and " (ese y) " =?")
            [ lv (StripNSPath lid) ] )
          (.execute
            sql
            (str "delete from "
                 (ese (:table mm))
                 " where " (ese x) " =? and " (ese y) " =? and "
                 (ese a) " =? and " (ese b) " =?" )
            [ lv (StripNSPath lid)
              rv (StripNSPath rid) ])) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioClrM2M ""

  [ctx lhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         lid (GetTypeId lhsObj)
         lv (:rowid (meta lhsObj))
         zm (mc lid)
         ac (DbioGetAssoc mc
                          zm
                          (:as ctx))
         mm (mc (:joined ac))
         flds (:fields (meta mm))
         ml (:rhs (:lhs (:assocs mm)))
         rl (:rhs (:rhs (:assocs mm)))
         [x y]
         (if (= ml lid)
             [ (:column (:lhs-oid flds)) (:column (:lhs-typeid flds)) ]
             [ (:column (:rhs-oid flds)) (:column (:rhs-typeid flds)) ]) ]
    (when (nil? ac) (DbioError "Uknown assoc " (:as ctx)))
    (.execute
      ^SQLr (:with ctx)
      (str "delete from "
           (ese (:table mm))
           " where "
           (ese x) " =? and "
           (ese y) " =?")
      [ lv (StripNSPath lid) ] )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioGetM2M ""

  [ctx lhsObj]

  (let [ mc (.getMetas *META-CACHE*)
         lv (:rowid (meta lhsObj))
         lid (GetTypeId lhsObj)
         zm (mc lid)
         ac (DbioGetAssoc mc
                          zm
                          (:as ctx))
         mm (mc (:joined ac))
         flds (:fields (meta mm))
         ml (:rhs (:lhs (:assocs mm)))
         rl (:rhs (:rhs (:assocs mm)))
         eseRES (ese "RES")
         eseMM (ese "MM")
         [x y z k a t]
         (if (= ml lid)
             [:lhs-typeid :rhs-typeid :lhs-oid :rhs-oid ml rl]
             [:rhs-typeid :lhs-typeid :rhs-oid :lhs-oid rl ml] ) ]
    (when (nil? ac) (DbioError "Unknown assoc " (:as ctx)))
    (.select
      ^SQLr (:with ctx)
      t
      (str "select distinct " eseRES ".* from "
           (ese (:table (get mc t)))
           " " eseRES " join " (ese (:table mm)) " " eseMM " on "
           (str eseMM "." (ese (:column (x flds))))
           "=? and "
           (str eseMM "." (ese (:column (y flds))))
           "=? and "
           (str eseMM "." (ese (:column (z flds))))
           "=? and "
           (str eseMM "." (ese (:column (k flds))))
           " = " (str eseRES "." (ese (:column (:rowid flds)))))
      [ (StripNSPath a) (StripNSPath t) lv ])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

