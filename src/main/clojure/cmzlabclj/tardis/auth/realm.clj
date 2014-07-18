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

  cmzlabclj.tardis.auth.realm

  (:gen-class
    :extends org.apache.shiro.realm.AuthorizingRealm
    :name cmzlabclj.tardis.auth.realm.JdbcRealm
    :init myInit
    :constructors {[] []}
    :exposes-methods { }
    :state myState
  )

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.crypto.codec :only [Pwdify] ]
        [cmzlabclj.tardis.auth.plugin]
        [cmzlabclj.nucleus.dbio.connect]
        [cmzlabclj.nucleus.dbio.core])

  (:import  [org.apache.shiro.authc AuthenticationException AuthenticationToken SimpleAccount]
            [org.apache.shiro.authz AuthorizationException AuthorizationInfo]
            [org.apache.shiro.subject PrincipalCollection]
            [org.apache.shiro.realm AuthorizingRealm]
            [com.zotohlab.frwk.dbio DBAPI]
            [org.apache.shiro.realm CachingRealm]
            [java.util Collection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -myInit [] [ [] (atom nil) ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doGetAuthenticationInfo

  [^AuthorizingRealm this ^AuthenticationToken token]

  (let [^DBAPI db (DbioConnectViaPool *JDBC-POOL* *META-CACHE* {})
         ;;pwd (.getCredentials token)
        user (.getPrincipal token)
        sql (.newSimpleSQLr db) ]
    (try
      (let [acc (FindLoginAccount sql user) ]
        (if (nil? acc)
          nil
          (SimpleAccount.  acc (:passwd acc) (.getName this))
        ))
      (finally
        (.finz db)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doGetAuthorizationInfo ""

  [^AuthorizingRealm  this ^PrincipalCollection principals]

  (let [^DBAPI db (DbioConnectViaPool *JDBC-POOL* *META-CACHE* {})
        acc (.getPrimaryPrincipal principals)
        rc (SimpleAccount. acc (:passwd acc) (.getName this))
        sql (.newSimpleSQLr db) ]
    (try
      (let [rs (DbioGetM2M {:as :roles :with sql } acc) ]
        (doseq [r (seq rs) ]
          (.addRole rc ^String (:name r)))
        rc)
      (finally
        (.finz db)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -init []
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private realm-eof nil)

