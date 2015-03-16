;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.nucleus.dbio.connect

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.dbio.core]
        [cmzlabclj.nucleus.util.core :only [Try!] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.nucleus.dbio.composite]
        [cmzlabclj.nucleus.dbio.simple]
        [cmzlabclj.nucleus.dbio.sqlserver]
        [cmzlabclj.nucleus.dbio.postgresql]
        [cmzlabclj.nucleus.dbio.mysql]
        [cmzlabclj.nucleus.dbio.oracle]
        [cmzlabclj.nucleus.dbio.h2])

  (:import  [java.util Map HashMap]
            [com.zotohlab.frwk.dbio DBAPI JDBCPool JDBCInfo
                                    DBIOLocal DBIOError OptLockError]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RegisterJdbcTL "Add a thread-local db pool."

  [^JDBCInfo jdbc options]

  (let [tloc (DBIOLocal/getCache)
        hc (.getId jdbc)
        ^Map c (.get tloc) ]
    (when-not (.containsKey c hc)
      (log/debug "No db pool found in DBIO-thread-local, creating one...")
      (let [o {:partitions 1
               :max-conns 1 :min-conns 1 }
            p (MakeDbPool jdbc (merge options o)) ]
        (.put c hc p)))
    (.get c hc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioConnect "Connect to a datasource."

  ^DBAPI
  [^JDBCInfo jdbc metaCache options]

  (let [hc (.getId jdbc) ]
    ;;(log/debug (.getMetas metaCache))
    (reify DBAPI

      (supportsOptimisticLock [_] (not (false? (:opt-lock options))))

      (vendor [_] (ResolveVendor jdbc))

      (getMetaCache [_] metaCache)

      (finz [_] nil)

      (open [_] (MakeConnection jdbc))

      (newCompositeSQLr [this] (CompositeSQLr metaCache this))
      (newSimpleSQLr [this] (SimpleSQLr metaCache this)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioConnectViaPool "Connect to a datasource."

  ^DBAPI
  [^JDBCPool pool metaCache options]

  (reify DBAPI

    (supportsOptimisticLock [_] (not (false? (:opt-lock options))))
    (getMetaCache [_] metaCache)

    (vendor [_] (.vendor pool))
    (finz [_] nil)
    (open [_] (.nextFree pool))

    (newCompositeSQLr [this] (CompositeSQLr metaCache this))
    (newSimpleSQLr [this] (SimpleSQLr metaCache this)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private connect-eof nil)

