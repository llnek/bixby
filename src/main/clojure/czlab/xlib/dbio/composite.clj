;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.dbio.composite

  (:require
    [czlab.xlib.util.core :refer [test-nonil try!]]
    [czlab.xlib.util.str :refer [hgl?]]
    [czlab.xlib.util.logging :as log])

  (:use [czlab.xlib.dbio.core]
        [czlab.xlib.dbio.sql])

  (:import
    [com.zotohlab.frwk.dbio Transactable SQLr MetaCache DBAPI]
    [java.sql Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompositeSQLr*

  "A composite supports transactions"

  ^Transactable
  [^DBAPI db ]

  (reify

    Transactable

    (execWith [me func]
      (with-local-vars [rc nil]
      (with-open [conn
                  (.begin me) ]
        (try
          (->> (ReifySQLr
                 db
                 (fn [_] conn) #(%2 %1))
               (func )
               (var-set rc ))
          (.commit me conn)
          @rc
          (catch Throwable e#
            (.rollback me conn)
            (log/warn e# "")
            (throw e#))) )))

    (rollback [_ conn]
      (try! (-> ^Connection
                conn
                (.rollback))))

    (commit [_ conn]
      (-> ^Connection
          conn
          (.commit)))

    (begin [_]
      (let [conn (.open db) ]
        (.setAutoCommit conn false)
        (->> Connection/TRANSACTION_SERIALIZABLE
             (.setTransactionIsolation conn ))
        conn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

