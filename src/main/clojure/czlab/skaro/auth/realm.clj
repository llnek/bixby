;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.auth.realm

  (:gen-class
   :extends org.apache.shiro.realm.AuthorizingRealm
   :name czlab.skaro.auth.realm.JdbcRealm
   :init myInit
   :constructors {[] []}
   :exposes-methods { }
   :state myState)

  (:require
    [czlab.xlib.crypto.codec :refer [Pwdify]])

  (:require
    [czlab.xlib.util.logging :as log])

  (:use [czlab.skaro.auth.plugin]
        [czlab.xlib.dbio.connect]
        [czlab.xlib.dbio.core])

  (:import
    [org.apache.shiro.authz AuthorizationException AuthorizationInfo]
    [org.apache.shiro.authc AuthenticationException
    AuthenticationToken SimpleAccount]
    [org.apache.shiro.subject PrincipalCollection]
    [org.apache.shiro.realm AuthorizingRealm]
    [com.zotohlab.frwk.dbio DBAPI]
    [org.apache.shiro.realm CachingRealm]
    [java.util Collection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -myInit [] [ [] (atom nil) ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doGetAuthenticationInfo

  [^AuthorizingRealm this ^AuthenticationToken token]

  (let [db (DbioConnectViaPool *JDBC-POOL*
                               *META-CACHE* {})
         ;;pwd (.getCredentials token)
        user (.getPrincipal token)
        sql (.newSimpleSQLr db) ]
    (try
      (when-some [acc (FindLoginAccount sql user) ]
        (SimpleAccount. acc
                        (:passwd acc)
                        (.getName this)))
      (finally
        (.finz db)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doGetAuthorizationInfo ""

  [^AuthorizingRealm  this ^PrincipalCollection principals]

  (let [db (DbioConnectViaPool *JDBC-POOL*
                               *META-CACHE* {})
        acc (.getPrimaryPrincipal principals)
        rc (SimpleAccount. acc
                           (:passwd acc)
                           (.getName this))
        sql (.newSimpleSQLr db) ]
    (try
      (let [rs (DbioGetM2M {:as :roles :with sql } acc) ]
        (doseq [r rs]
          (.addRole rc ^String (:name r)))
        rc)
      (finally
        (.finz db)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -init [] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

