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
  cmzlabsclj.dbio.sqlserver

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:use [cmzlabsclj.dbio.drivers])
  (:use [cmzlabsclj.dbio.core :as dbcore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; SQLServer
(defmethod GetBlobKeyword SQLServer [db] "IMAGE")
(defmethod GetTSKeyword SQLServer [db] "DATETIME")
(defmethod GetDoubleKeyword SQLServer [db] "FLOAT(53)")
(defmethod GetFloatKeyword SQLServer [db] "FLOAT(53)")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoInteger SQLServer

  [db table fld]

  (str (GetPad db) (GenCol fld)
       " " (GetIntKeyword db)
       (if (:pkey fld) " IDENTITY (1,1) " " AUTOINCREMENT ")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoLong SQLServer

  [db table fld]

  (str (GetPad db) (GenCol fld)
       " " (GetLongKeyword db)
       (if (:pkey fld) " IDENTITY (1,1) " " AUTOINCREMENT ")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDrop SQLServer

  [db table]

  (str "IF EXISTS (SELECT * FROM dbo.sysobjects WHERE id=object_id('"
       table "')) DROP TABLE "
       table (GenExec db) "\n\n"))


;;(println (GetDDL (MakeMetaCache testschema) (SQLServer.) ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private sqlserver-eof nil)

