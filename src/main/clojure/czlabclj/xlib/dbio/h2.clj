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

  czlabclj.xlib.dbio.h2

  (:require [czlabclj.xlib.util.core :refer [test-nonil test-nestr]]
            [czlabclj.xlib.util.str :refer [nsb]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlabclj.xlib.dbio.drivers]
        [czlabclj.xlib.dbio.core])

  (:import  [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.frwk.dbio DBIOError]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [java.io File]
            [java.sql DriverManager Connection Statement]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def H2-SERVER-URL "jdbc:h2:tcp://host/path/db" )
(def H2-DRIVER "org.h2.Driver" )

(def H2-MEM-URL "jdbc:h2:mem:{{dbid}};DB_CLOSE_DELAY=-1" )
(def H2-FILE-URL "jdbc:h2:{{path}};MVCC=TRUE" )

(def H2_MVCC ";MVCC=TRUE" )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; H2
(defmethod GetDateKeyword H2 [db] "TIMESTAMP")
(defmethod GetDoubleKeyword H2 [db] "DOUBLE")
(defmethod GetBlobKeyword H2 [db] "BLOB")
(defmethod GetFloatKeyword H2 [db] "FLOAT")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoInteger H2

  [db table fld]

  (str (GetPad db) (GenCol fld)
       " " (GetIntKeyword db)
       (if (:pkey fld) " IDENTITY(1) " " AUTO_INCREMENT(1) ")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoLong H2

  [db table fld]

  (str (GetPad db) (GenCol fld)
       " " (GetLongKeyword db)
       (if (:pkey fld) " IDENTITY(1) " " AUTO_INCREMENT(1) ")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenBegin H2

  [db table]

  (str "CREATE CACHED TABLE " table "\n(\n" ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDrop H2

  [db table]

  (str "DROP TABLE " table " IF EXISTS CASCADE" (GenExec db) "\n\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeH2Db "Create a H2 database."

  [^File dbFileDir
   ^String dbid
   ^String user
   ^PasswordAPI pwdObj]

  (test-nonil "file-dir" dbFileDir)
  (test-nestr "db-id" dbid)
  (test-nestr "user" user)
  (let [url (io/file dbFileDir dbid)
        u (.getCanonicalPath url)
        pwd (nsb pwdObj)
        dbUrl (StringUtils/replace H2-FILE-URL "{{path}}" u) ]
    (log/debug "Creating H2: " dbUrl)
    (.mkdir dbFileDir)
    (with-open [c1 (DriverManager/getConnection dbUrl user pwd) ]
      (.setAutoCommit c1 true)
      (with-open [s (.createStatement c1) ]
        ;;(.execute s (str "CREATE USER " user " PASSWORD \"" pwd "\" ADMIN"))
        (.execute s "SET DEFAULT_TABLE_TYPE CACHED"))
      (with-open [s (.createStatement c1) ]
        (.execute s "SHUTDOWN")))
    dbUrl
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseH2Db "Close an existing H2 database."

  [^File dbFileDir
   ^String dbid
   ^String user
   ^PasswordAPI pwdObj]

  (test-nonil "file-dir" dbFileDir)
  (test-nestr "db-id" dbid)
  (test-nestr "user" user)
  (let [url (io/file dbFileDir dbid)
        u (.getCanonicalPath url)
        pwd (nsb pwdObj)
        dbUrl (StringUtils/replace H2-FILE-URL "{{path}}" u) ]
    (log/debug "Closing H2: " dbUrl)
    (with-open [c1 (DriverManager/getConnection dbUrl user pwd) ]
      (.setAutoCommit c1 true)
      (with-open [s (.createStatement c1) ]
        (.execute s "SHUTDOWN")) )
  ))

;;(println (GetDDL (MakeMetaCache testschema) (H2.) ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

