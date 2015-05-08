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
(defn SimpleSQLr "Non transactional SQL object."

  ^SQLr
  [^DBAPI db]

  (let [runc (fn [^Connection c f] (with-open [c c] (f c)))
        getc (fn [d] (openDB d))]
    (ReifySQLr (MakeProc db) db getc runc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private simple-eof nil)

