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
    [czlab.xlib.util.core :refer [test-nonil notnil? try!]]
    [czlab.xlib.util.str :refer [hgl?]])

  (:require [czlab.xlib.util.logging :as log])

  (:use [czlab.xlib.dbio.core]
        [czlab.xlib.dbio.sql])

  (:import
    [com.zotohlab.frwk.dbio Transactable SQLr MetaCache DBAPI]
    [java.sql Connection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompositeSQLr

  "A composite supports transactions"

  ^Transactable
  [^DBAPI db ]

  (reify Transactable

    (execWith [this func]
      (with-local-vars [rc nil]
        (with-open [conn (.begin this) ]
          (let [s (ReifySQLr db
                             (fn [_] conn)
                             #(%2 %1)) ]
            (try
              (var-set rc (func s))
              (.commit this conn)
              @rc
              (catch Throwable e#
                (do
                  (.rollback this conn)
                  (log/warn e# "")
                  (throw e#))) )))))

    (rollback [_ conn] (try! (.rollback ^Connection conn)))
    (commit [_ conn] (.commit ^Connection conn))

    (begin [_]
      (let [conn (.open db) ]
        (.setAutoCommit conn false)
        (.setTransactionIsolation conn Connection/TRANSACTION_SERIALIZABLE)
        conn))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

