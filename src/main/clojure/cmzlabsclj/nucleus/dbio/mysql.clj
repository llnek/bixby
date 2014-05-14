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
  cmzlabsclj.nucleus.dbio.mysql

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.dbio.drivers])
  (:use [cmzlabsclj.nucleus.dbio.core :only [MySQL] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def MYSQL-DRIVER "com.mysql.jdbc.Driver")

;; MySQL
(defmethod GetBlobKeyword MySQL [db] "LONGBLOB")
(defmethod GetTSKeyword MySQL [db] "TIMESTAMP")
(defmethod GetDoubleKeyword MySQL [db] "DOUBLE")
(defmethod GetFloatKeyword MySQL [db]  "DOUBLE")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenEnd MySQL

  [db table]

  (str "\n) Type=InnoDB" (GenExec db) "\n\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoInteger MySQL

  [db table fld]

  (str (GetPad db) (GenCol fld)
       " " (GetIntKeyword db) " NOT NULL AUTO_INCREMENT"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoLong MySQL

  [db table fld]

  (str (GetPad db) (GenCol fld)
       " " (GetLongKeyword db) " NOT NULL AUTO_INCREMENT"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDrop MySQL

  [db table]

  (str "DROP TABLE IF EXISTS " table (GenExec db) "\n\n"))

;;(println (GetDDL (MakeMetaCache testschema) (MySQL.) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private mysql-eof nil)

