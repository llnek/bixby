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

  czlabclj.xlib.dbio.connect

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [Try!]]
        [czlabclj.xlib.util.str :only [nsb]]
        [czlabclj.xlib.dbio.core]
        [czlabclj.xlib.dbio.composite]
        [czlabclj.xlib.dbio.simple])
        ;;[czlabclj.xlib.dbio.sqlserver]
        ;;[czlabclj.xlib.dbio.postgresql]
        ;;[czlabclj.xlib.dbio.mysql]
        ;;[czlabclj.xlib.dbio.oracle]
        ;;[czlabclj.xlib.dbio.h2])

  (:import  [java.util Map HashMap]
            [com.zotohlab.frwk.dbio DBAPI
             JDBCPool JDBCInfo
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

      (supportsLock [_] (not (false? (:opt-lock options))))

      (vendor [_] (ResolveVendor jdbc))

      (getMetaCache [_] metaCache)

      (finz [_] nil)

      (open [_] (MakeConnection jdbc))

      (newCompositeSQLr [this] (CompositeSQLr this))
      (newSimpleSQLr [this] (SimpleSQLr this)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DbioConnectViaPool "Connect to a datasource."

  ^DBAPI
  [^JDBCPool pool metaCache options]

  (reify DBAPI

    (supportsLock [_] (not (false? (:opt-lock options))))
    (getMetaCache [_] metaCache)

    (vendor [_] (.vendor pool))
    (finz [_] nil)
    (open [_] (.nextFree pool))

    (newCompositeSQLr [this] (CompositeSQLr this))
    (newSimpleSQLr [this] (SimpleSQLr this)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private connect-eof nil)

