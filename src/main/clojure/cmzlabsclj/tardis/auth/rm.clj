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

  cmzlabsclj.tardis.auth.rm

  (:gen-class
    :extends org.apache.shiro.realm.AuthorizingRealm
    :name cmzlabsclj.tardis.auth.rm.JdbcRealm
    :init myInit
    :constructors {[] []}
    :exposes-methods { }
    :state myState
  )

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.crypto.codec :only [Pwdify] ])
  (:use [cmzlabsclj.tardis.auth.core])
  (:use [cmzlabsclj.nucleus.dbio.connect])
  (:use [cmzlabsclj.nucleus.dbio.core])

  (:import (org.apache.shiro.authc AuthenticationException AuthenticationToken SimpleAccount))
  (:import (org.apache.shiro.authz AuthorizationException AuthorizationInfo))
  (:import (org.apache.shiro.subject PrincipalCollection))
  (:import (org.apache.shiro.realm AuthorizingRealm))
  (:import (com.zotohlab.frwk.dbio DBAPI))
  (:import (org.apache.shiro.realm CachingRealm))
  (:import (java.util Collection)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -myInit []
  [ []
    (atom nil) ] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doGetAuthenticationInfo

  [^AuthorizingRealm this ^AuthenticationToken token]

  (let [ ^DBAPI db (DbioConnect *JDBC-INFO* *META-CACHE*)
         pwd (.getCredentials token)
         user (.getPrincipal token)
         sql (.newSimpleSQLr db) ]
    (try
      (let[ acc (GetLoginAccount sql user (Pwdify pwd))
            rc (SimpleAccount.  acc (:passwd acc) (.getName this)) ]
        rc)
      (catch Throwable e#
        (throw (AuthenticationException. e#)))
      (finally
        (.finz db)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAvailablePrinc ""
  
  [^PrincipalCollection princs ^String rname]

  (if (or (nil? princs)
          (.isEmpty princs))
    nil
    (let [ ^Collection ps (.fromRealm princs rname) ]
      (if (or (nil? ps) (.isEmpty ps))
        (.getPrimaryPrincipal princs)
        (-> ps (.iterator)(.next))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doGetAuthorizationInfo ""

  [^AuthorizingRealm  this ^PrincipalCollection principals]

  (let [ ^DBAPI db (DbioConnect *JDBC-INFO* *META-CACHE*)
         rname (.getName this)
         sql (.newSimpleSQLr db) ]
    (try
      (let [ acc (getAvailablePrinc principals rname)
             rc (SimpleAccount.  ^String acc (:passwd acc) rname)
             rs (DbioGetM2M {:as :roles :with sql } acc) ]
          (doseq [ r (seq rs) ]
            (.addRole rc ^String (:name r)))
          rc)
        (catch Throwable e#
          (throw (AuthorizationException. e#)))
        (finally
          (.finz db)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -init []
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private rm-eof nil)


