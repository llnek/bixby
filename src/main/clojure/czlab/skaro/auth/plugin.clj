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

  czlab.skaro.auth.plugin

  (:require
    [czlab.dbio.connect :refer [dbioConnectViaPool]]
    [czlab.xlib.core
     :refer [trap!
             exp!
             tryc
             stringify
             ce?
             mubleObj!
             do->false
             do->true
             juid
             test-nonil
             loadJavaProps]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.crypto.codec :refer [pwdify]]
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.net.comms :refer [getFormFields]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.wfs]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.webss]
        [czlab.skaro.io.basicauth]
        [czlab.skaro.auth.model]
        [czlab.dbio.core])

  (:import
    [org.apache.shiro.config IniSecurityManagerFactory]
    [org.apache.commons.lang3.tuple ImmutablePair]
    [org.apache.shiro.authc UsernamePasswordToken]
    [czlab.net ULFormItems ULFileItem]
    [czlab.skaro.etc PluginFactory
     Plugin
     AuthPlugin
     PluginError]
    [czlab.skaro.runtime AuthError
     UnknownUser
     DuplicateUser]
    [org.apache.commons.codec.binary Base64]
    [czlab.skaro.server Cocoon]
    [czlab.xlib Muble I18N BadDataError]
    [czlab.crypto PasswordAPI]
    [czlab.dbio DBAPI
     MetaCache
     SQLr
     JDBCPool
     JDBCInfo]
    [java.io File IOException]
    [java.util Properties]
    [org.apache.shiro SecurityUtils]
    [org.apache.shiro.subject Subject]
    [czlab.wflow If
     BoolExpr
     Activity
     Job
     PTask
     Work]
    [czlab.skaro.io WebSS HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assertPluginOK

  "true if the plugin has been initialized,
   by looking into the db"

  [^JDBCPool pool]

  (let [tbl (:table LoginAccount)]
    (when-not (tableExist? pool tbl)
      (mkDbioError (rstr (I18N/getBase)
                       "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr ""

  ^SQLr
  [^Cocoon ctr]

  ;; get the default db pool
  (-> (dbioConnectViaPool
        (.acquireDbPool ctr "") AUTH-MCACHE {})
      (.newSimpleSQLr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createAuthRole

  "Create a new auth-role in db"

  [^SQLr sql ^String role ^String desc]

  (.insert sql (-> (dbioCreateObj :czc.skaro.auth/AuthRole)
                   (dbioSetFlds :name role :desc desc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteAuthRole

  "Delete this role"

  [^SQLr sql role]

  (.exec sql
         (str "DELETE FROM "
              (gtable AuthRole)
              " WHERE "
              (->> (meta AuthRole)
                   (:fields)
                   (:name)
                   (:column)
                   (ese))
              " = ?")
         [(strim role)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listAuthRoles

  "List all the roles in db"

  [^SQLr sql]

  (.findAll sql :czc.skaro.auth/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createLoginAccount

  "Create a new account
   options : a set of extra properties, such as email address.
   roleObjs : a list of roles to be assigned to the account"

  [^SQLr sql ^String user
   ^PasswordAPI pwdObj & [options roleObjs]]

  (let [roleObjs (or roleObjs [])
        options (or options {})
        ps (.hashed pwdObj)
        acc (->> (-> :czc.skaro.auth/LoginAccount
                     (dbioCreateObj )
                     (dbioSetFld*
                       (merge
                         {:acctid (strim user)
                          :passwd (.getLeft ps)}
                         options)))
                 (.insert sql)) ]
    ;; currently adding roles to the account is not bound to the
    ;; previous insert. That is, if we fail to set a role, it's
    ;; assumed ok for the account to remain inserted
    (doseq [r roleObjs]
      (dbioSetM2M { :as :roles :with sql } acc r))
    (log/debug "created new account %s%s%s%s"
               "into db: "
               acc "\nwith meta\n" (meta acc))
    acc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn findLoginAccountViaEmail

  "Look for account with this email address"

  [^SQLr sql ^String email]

  (.findOne sql
            :czc.skaro.auth/LoginAccount
            {:email (strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn findLoginAccount

  "Look for account with this user id"

  [^SQLr sql ^String user]

  (.findOne sql
            :czc.skaro.auth/LoginAccount
            {:acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLoginAccount

  "Get the user account"

  [^SQLr sql ^String user ^String pwd]

  (if-some [acct (findLoginAccount sql user)]
    (if (.validateHash (pwdify pwd)
                       (:passwd acct))
      acct
      (trap! AuthError (rstr (I18N/getBase) "auth.bad.pwd")))
    (trap! UnknownUser user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasLoginAccount

  "true if this user account exists"

  [^SQLr sql ^String user]

  (some? (findLoginAccount sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn changeLoginAccount

  "Change the account password"

  [^SQLr sql userObj ^PasswordAPI pwdObj ]

  (let [ps (.hashed pwdObj)
        u (-> userObj
              (dbioSetFlds :passwd (.getLeft ps)
                           :salt (.getRight ps))) ]
    (.update sql u)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn updateLoginAccount

  "Update account details
   details: a set of properties such as email address"

  [^SQLr sql userObj details]

  {:pre [(map? details)]}

  (if (empty? details)
    userObj
    (->> (dbioSetFld* userObj details)
         (.update sql))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteLoginAccountRole

  "Remove a role from this user"

  [^SQLr sql userObj roleObj]

  (dbioClrM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addLoginAccountRole

  "Add a role to this user"

  [^SQLr sql userObj roleObj]

  (dbioSetM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteLoginAccount

  "Delete this account"

  [^SQLr sql userObj]

  (.delete sql userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteUser

  "Delete the account with this user id"

  [^SQLr sql user]

  (.exec sql
         (str "DELETE FROM "
              (gtable LoginAccount)
              " WHERE "
              (->> (meta LoginAccount)
                   (:fields)
                   (:acctid)
                   (:column)
                   (ese))
              " =?")
         [ (strim user) ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listLoginAccounts

  "List all user accounts"

  [^SQLr sql]

  (.findAll sql :czc.skaro.auth/LoginAccount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-shiro ""

  [^File appDir ^String appKey]

  (-> (io/file appDir "conf/shiro.ini")
      (io/as-url )
      (.toString)
      (IniSecurityManagerFactory. )
      (.getInstance)
      (SecurityUtils/setSecurityManager ))
  (log/info "created shiro security manager"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn maybeSignupTest

  "Test component of a standard sign-up workflow"

  ^BoolExpr
  [^String challengeStr]

  (defBoolExpr
    (fn [^Job job]
      (let
        [^HTTPEvent evt (.event job)
         csrf (-> ^WebSS
                  (.getSession evt)
                  (.getXref))
         si (try
              (getSignupInfo evt)
              (catch
                BadDataError e# {:e e#}))
         rb (I18N/getBase)
         info (or si {})
         ^AuthPlugin
         pa (-> ^Muble (.container job)
                       (.getv K_PLUGINS)
                       (:auth )) ]
        (log/debug "session csrf = %s%s%s"
                   csrf
                   ", and form token = " (:csrf info))
        (cond
          (some? (:e info))
          (do->false
            (->> {:error (exp! AuthError (ce? (:e info)))}
                 (.setLastResult job)))

          (and (hgl? challengeStr)
               (not= challengeStr (:captcha info)))
          (do->false
            (->> {:error (exp! AuthError (rstr rb "auth.bad.cha")) }
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (exp! AuthError (rstr rb "auth.bad.tkn")) }
                 (.setLastResult job)))

          (and (hgl? (:credential info))
               (hgl? (:principal info))
               (hgl? (:email info)))
          (if (.hasAccount pa info)
            (do->false
              (->> {:error (exp! DuplicateUser (str (:principal info)))}
                   (.setLastResult job )))
            (do->true
              (->> {:account (.addAccount pa info)}
                   (.setLastResult job))))

          :else
          (do->false
            (->> {:error (exp! AuthError (rstr rb "auth.bad.req"))}
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn maybeLoginTest ""

  ^BoolExpr
  []

  (defBoolExpr
    (fn [^Job job]
      (let
        [^HTTPEvent evt (.event job)
         csrf (-> ^WebSS
                  (.getSession evt)
                  (.getXref ))
         si (try
              (getSignupInfo evt)
              (catch
                BadDataError e# {:e e#}))
         rb (I18N/getBase)
         info (or si {})
         ^AuthPlugin
         pa (-> ^Muble (.container job)
                (.getv K_PLUGINS)
                (:auth )) ]
        (log/debug "session csrf = %s%s%s"
                   csrf
                   ", and form token = " (:csrf info))
        (cond
          (some? (:e info))
          (do->false
            (->> {:error (exp! AuthError (ce? (:e info)))}
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (exp! AuthError (rstr rb "auth.bad.tkn"))}
                 (.setLastResult job)))

          (and (hgl? (:credential info))
               (hgl? (:principal info)))
          (do
            (->> {:account (.login pa (:principal info)
                                      (:credential info)) }
                 (.setLastResult job))
            (some? (:account (.getLastResult job))))

          :else
          (do->false
            (->> {:error (exp! AuthError (rstr rb "auth.bad.req")) }
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAuthPlugin ""

  ^Plugin
  [^Cocoon ctr]

  (let [impl (mubleObj!) ]
    (reify AuthPlugin

      (configure [_ props] )

      (initialize [_]
        (-> (.acquireDbPool ctr "")
            (applyDDL )))

      (start [_]
        (assertPluginOK (.acquireDbPool ctr ""))
        (init-shiro (.getAppDir ctr)
                    (.getAppKey ctr))
        (log/info "AuthPlugin started"))

      (stop [_]
        (log/info "AuthPlugin stopped"))

      (dispose [_]
        (log/info "AuthPlugin disposed"))

      (checkAction [_ acctObj action] )

      (addAccount [_ options]
        (let [pkey (.getAppKey ctr)]
          (createLoginAccount
            (getSQLr ctr)
            (:principal options)
            (-> (:credential options)
                (pwdify pkey))
            options
            [])))

      (login [_ user pwd]
        (binding
          [*JDBC-POOL* (.acquireDbPool ctr "")
           *META-CACHE* AUTH-MCACHE ]
          (let
            [token (UsernamePasswordToken.
                     ^String user ^String pwd)
             cur (SecurityUtils/getSubject)
             sss (.getSession cur) ]
            (log/debug "Current user session %s" sss)
            (log/debug "Current user object %s" cur)
            (when-not (.isAuthenticated cur)
              (tryc
                ;;(.setRememberMe token true)
                (.login cur token)
                (log/debug "User [%s] logged in successfully" user)))
            (if (.isAuthenticated cur)
              (.getPrincipal cur)))))

      (hasAccount [_ options]
        (let [pkey (.getAppKey ctr)]
          (hasLoginAccount (getSQLr ctr)
                           (:principal options))))

      (getAccount [_ options]
        (let [pkey (.getAppKey ctr)
              sql (getSQLr ctr) ]
          (cond
            (some? (:principal options))
            (findLoginAccount sql
                              (:principal options))
            (some? (:email options))
            (findLoginAccountViaEmail sql
                              (:email options))
            :else nil)))

      (getRoles [_ acct] []))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn authPluginFactory ""

  ^PluginFactory
  []

  (reify PluginFactory
    (createPlugin [_ ctr]
      (makeAuthPlugin ctr))))

;;(ns-unmap *ns* '->AuthPluginFactory)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain ""

  [& args]

  (let [appDir (io/file (first args))
        cmd (nth args 1)
        db (nth args 2)
        env (readEdn (io/file appDir CFG_ENV_CF))
        app (readEdn (io/file appDir CFG_APP_CF))
        pkey (-> (str (get-in app [:info :disposition]))
                 (.toCharArray))
        cfg ((keyword db) (get-in env [:databases :jdbc])) ]
    (when (some? cfg)
      (let [j (mkJdbc db cfg (pwdify (:passwd cfg) pkey))
            t (matchJdbcUrl (:url cfg)) ]
        (cond
          (= "init-db" cmd)
          (applyDDL j)

          (= "gen-sql" cmd)
          (if (> (count args) 3)
            (exportAuthPluginDDL t
                                 (io/file (nth args 3))))

          :else
          nil)) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; home gen-sql alias outfile
;; home init-db alias
(defn -main "Main Entry"

  [& args]

  ;; for security, don't just eval stuff
  ;;(alter-var-root #'*read-eval* (constantly false))
  (if (< (count args) 3)
    nil
    (apply doMain args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


