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

  comzotohlabscljc.dbio.connect

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [comzotohlabscljc.dbio.core :as dbcore :only [MakeDbPool] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [Try!] ])
  (:use [comzotohlabscljc.util.str :only [nsb] ])
  (:use [comzotohlabscljc.dbio.composite])
  (:use [comzotohlabscljc.dbio.simple])
  (:use [comzotohlabscljc.dbio.sqlserver])
  (:use [comzotohlabscljc.dbio.postgresql])
  (:use [comzotohlabscljc.dbio.mysql])
  (:use [comzotohlabscljc.dbio.oracle])
  (:use [comzotohlabscljc.dbio.h2])
  (:import (java.util Map HashMap))
  (:import (com.zotohlabs.frwk.dbio DBAPI JDBCPool JDBCInfo
                                    DBIOLocal DBIOError OptLockError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hashJdbc ""

  ^long
  [jdbc]

  (.hashCode
    (str (:driver jdbc) (:url jdbc)
         (:user jdbc) (nsb (:pwdObj jdbc)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RegisterJdbcTL ""

  [^JDBCInfo jdbc options]

  (let [ tloc (DBIOLocal/getCache)
         hc (.getId jdbc)
         ^Map c (.get tloc) ]
    (when-not (.containsKey c hc)
      (log/debug "no db pool found in thread-local, creating one...")
      (let [ p (dbcore/MakeDbPool jdbc options) ]
        (.put c hc p)))
    (.get c hc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybe-finz-pool ""

  [ hc]

  (let [ tloc (DBIOLocal/getCache) ;; a thread local
         ^Map c (.get tloc) ;; c == java hashmap
         p (.get c hc) ]
    (when-not (nil? p)
      (Try! (.shutdown ^JDBCPool p))
      (.remove c hc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybe-get-pool ""

  ^JDBCPool
  [ hc jdbc options]

  (let [ tloc (DBIOLocal/getCache) ;; get the thread local
         ^Map c (.get tloc)
         rc (.get c hc) ]
    (if (nil? rc)
      (RegisterJdbcTL jdbc options)
      rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioConnect "Connect to a datasource."

  ^DBAPI
  [^JDBCInfo jdbc metaCache options]

  (let [ hc (.getId jdbc) ]
    ;;(log/debug (.getMetas metaCache))
    (reify DBAPI

      (getMetaCache [_] metaCache)

      (supportsOptimisticLock [_]
        (if (false? (:opt-lock options)) false true))

      (vendor [_]
        (let [ ^JDBCPool
               p (maybe-get-pool hc jdbc options) ]
          (if (nil? p)
            nil
            (.vendor p))))

      (finz [_] nil)

      (open [_]
        (let [ ^JDBCPool
               p (maybe-get-pool hc jdbc options) ]
          (if (nil? p)
            nil
            (.nextFree p))))

      (newCompositeSQLr [this]
        (CompositeSQLr metaCache this))

      (newSimpleSQLr [this]
        (SimpleSQLr metaCache this)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private connect-eof nil)

