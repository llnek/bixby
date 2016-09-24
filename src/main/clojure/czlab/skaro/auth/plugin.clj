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
      :author "Kenneth Leung" }

  czlab.skaro.auth.plugin

  (:require
    [czlab.dbio.connect :refer [dbopen<+>]]
    [czlab.xlib.core
     :refer [doto->>
             trap!
             exp!
             try!
             stringify
             cexp?
             muble<>
             do->false
             do->true
             juid
             test-some
             loadJavaProps]]
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.netty.util :refer [filterFormFields]])

  (:use [czlab.skaro.auth.core]
        [czlab.skaro.sys.core]
        [czlab.skaro.io.webss]
        [czlab.skaro.auth.model]
        [czlab.dbio.core])

  (:import
    [czlab.skaro.etc AuthError UnknownUser DuplicateUser]
    [org.apache.shiro.config IniSecurityManagerFactory]
    [org.apache.shiro.authc UsernamePasswordToken]
    [czlab.net ULFormItems ULFileItem]
    [clojure.lang APersistentMap]
    [czlab.skaro.etc
     Plugin
     AuthPlugin
     PluginError
     PluginFactory]
    [czlab.skaro.server Container ]
    [czlab.xlib Muble I18N BadDataError]
    [czlab.crypto PasswordAPI]
    [czlab.dbio
     DBAPI
     Schema
     SQLr
     JDBCPool
     JDBCInfo]
    [java.io File IOException]
    [java.util Properties]
    [org.apache.shiro SecurityUtils]
    [org.apache.shiro.subject Subject]
    [czlab.wflow
     BoolExpr
     TaskDef
     If
     Job
     Script]
    [czlab.skaro.io WebSS HttpEvent HttpResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:dynamic *META-CACHE* nil)
(def ^:dynamic *JDBC-POOL* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assertPluginOK

  "true if the plugin has been initialized,
   by looking into the db"
  [^JDBCPool pool]

  (let [tbl (->> :czlab.skaro.auth.model/LoginAccount
                 (.get ^Schema *auth-mcache*)
                 (dbtable))]
    (when-not (tableExist? pool tbl)
      (dberr! (rstr (I18N/base)
                    "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr

  ""
  ^SQLr
  [^Container ctr & [tx?]]
  ;; get the default db pool
  (let [db (-> (.acquireDbPool ctr "")
               (dbopen<+> *auth-mcache* ))]
    (if (boolean tx?)
      (.compositeSQLr db)
      (.simpleSQLr db))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createAuthRole

  "Create a new auth-role in db"
  ^APersistentMap
  [^SQLr sql ^String role ^String desc]

  (let [m (->> :czlab.skaro.auth.model/AuthRole
               (.get (.metas sql)))
        rc
        (-> (dbpojo<> m)
            (dbSetFlds* {:name role
                         :desc desc}))]
    (.insert sql rc)
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteAuthRole

  "Delete this role"
  [^SQLr sql ^String role]

  (let [m (->> :czlab.skaro.auth.model/AuthRole
               (.get (.metas sql)))]
    (.exec sql
           (format
             "delete from %s where %s =?"
             (->> (dbtable m)
                  (.fmtId sql))
             (->> (dbcol :name m)
                  (.fmtId sql)))
           [(strim role)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listAuthRoles

  "List all the roles in db"
  [^SQLr sql]

  (.findAll sql :czlab.skaro.auth.model/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createLoginAccount

  "Create a new account
   props : extra properties, such as email address.
   roleObjs : a list of roles to be assigned to the account"
  ^APersistentMap
  [^SQLr sql ^String user
   ^PasswordAPI pwdObj props roleObjs]
  {:pre [(map? props) (coll? roleObjs)]}

  (let [m (->> :czlab.skaro.auth.model/LoginAccount
               (.get (.metas sql)))
        ps (:hash (.hashed pwdObj))
        acc
        (->>
          (dbSetFlds* (dbpojo<> m)
                      (merge {:acctid (strim user)
                              :passwd ps} props))
          (.insert sql))]
    ;; currently adding roles to the account is not bound to the
    ;; previous insert. That is, if we fail to set a role, it's
    ;; assumed ok for the account to remain inserted
    (doseq [r roleObjs]
      (dbSetM2M {:joined :czlab.skaro.auth.model/AccountRoles
                 :with sql} acc r))
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
            :czlab.skaro.auth.model/LoginAccount
            {:email (strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn findLoginAccount

  "Look for account with this user id"
  [^SQLr sql ^String user]

  (.findOne sql
            :czlab.skaro.auth.model/LoginAccount
            {:acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLoginAccount

  "Get the user account"
  [^SQLr sql ^String user ^String pwd]

  (if-some [acct (findLoginAccount sql user)]
    (if (.validateHash (passwd<> pwd)
                       (:passwd acct))
      acct
      (trap! AuthError (rstr (I18N/base) "auth.bad.pwd")))
    (trap! UnknownUser user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasLoginAccount?

  "true if this user account exists"
  [^SQLr sql ^String user]

  (some? (findLoginAccount sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn changeLoginAccount

  "Change the account password"
  ^APersistentMap
  [^SQLr sql userObj ^PasswordAPI pwdObj]

  (let [ps (.hashed pwdObj)
        m {:passwd (:hash ps)
           :salt (:salt ps)}]
    (->> (dbSetFlds*
           (mockPojo<> userObj) m)
         (.update sql ))
    (dbSetFlds* userObj m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn updateLoginAccount

  "Update account details
   details: a set of properties such as email address"
  ^long
  [^SQLr sql userObj details]
  {:pre [(or (nil? details)
             (map? details))]}

  (if (empty? details)
    0
    (doto->>
      (dbSetFlds* userObj details)
      (.update sql))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteLoginAccountRole

  "Remove a role from this user"
  ^long
  [^SQLr sql userObj roleObj]

  (dbClrM2M {:joined :czlab.skaro.auth.model/AccountRoles
             :with sql}
            userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addLoginAccountRole

  "Add a role to this user"
  ^APersistentMap
  [^SQLr sql userObj roleObj]

  (dbSetM2M {:joined :czlab.skaro.auth.model/AccountRoles
             :with sql}
            userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteLoginAccount

  "Delete this account"
  ^long
  [^SQLr sql userObj]

  (.delete sql userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteUser

  "Delete the account with this user id"
  ^long
  [^SQLr sql user]

  (let [m (->> :czlab.skaro.auth.model/LoginAccount
               (.get (.metas sql)))]
    (.exec sql
           (format "delete from %s where %s =?"
                   (->> (dbtable m)
                        (.fmtId sql))
                   (->> (dbcol :acctid m)
                        (.fmtId sql))
                   " =?")
           [(strim user)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listLoginAccounts

  "List all user accounts"
  [^SQLr sql]

  (.findAll sql :czlab.skaro.auth.model/LoginAccount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-shiro

  ""
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
(defn signupTestExpr<>

  "Test component of a standard sign-up workflow"
  ^BoolExpr
  [^String challengeStr]

  (reify BoolExpr
    (ptest [_ arg]
      (let
        [^HttpEvent evt (.event ^Job arg)
         ^Job job arg
         csrf (-> ^WebSS
                  (.session evt) (.xref))
         si (try
              (getSignupInfo evt)
              (catch BadDataError e# {:e e#}))
         rb (I18N/base)
         info (or si {})
         ^AuthPlugin
         pa (-> ^Muble (.server job)
                (.getv K_PLUGINS)
                (:auth ))]
        (log/debug "session csrf = %s%s%s"
                   csrf ", and form token = " (:csrf info))
        (cond
          (some? (:e info))
          (do->false
            (->> {:error (exp! AuthError
                               (cexp? (:e info)))}
                 (.setLastResult job)))

          (and (hgl? challengeStr)
               (not= challengeStr (:captcha info)))
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.cha")) }
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.tkn")) }
                 (.setLastResult job)))

          (and (hgl? (:credential info))
               (hgl? (:principal info))
               (hgl? (:email info)))
          (if (.hasAccount pa info)
            (do->false
              (->> {:error (exp! DuplicateUser
                                 (str (:principal info)))}
                   (.setLastResult job )))
            (do->true
              (->> {:account (.addAccount pa info)}
                   (.setLastResult job))))

          :else
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.req"))}
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn loginTestExpr<>

  ""
  ^BoolExpr
  []

  (reify BoolExpr
    (ptest [_ arg]
      (let
        [^HttpEvent evt (.event ^Job arg)
         ^Job job arg
         csrf (-> ^WebSS
                  (.session evt)
                  (.xref ))
         si (try
              (getSignupInfo evt)
              (catch
                BadDataError e# {:e e#}))
         rb (I18N/base)
         info (or si {})
         ^AuthPlugin
         pa (-> ^Muble
                (.server job)
                (.getv K_PLUGINS)
                (:auth )) ]
        (log/debug "session csrf = %s%s%s"
                   csrf
                   ", and form token = " (:csrf info))
        (cond
          (some? (:e info))
          (do->false
            (->> {:error (exp! AuthError
                               (cexp? (:e info)))}
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.tkn"))}
                 (.setLastResult job)))

          (and (hgl? (:credential info))
               (hgl? (:principal info)))
          (do
            (->> {:account (.login pa (:principal info)
                                      (:credential info)) }
                 (.setLastResult job))
            (some? (:account (.lastResult job))))

          :else
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.req")) }
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- authPlugin<>

  ""
  ^Plugin
  [^Container ctr]

  (reify AuthPlugin

    (init [_ arg]
      (-> (.acquireDbPool ctr "")
          (applyDDL )))

    (start [_]
      (assertPluginOK (.acquireDbPool ctr ""))
      (init-shiro (.appDir ctr)
                  (.appKey ctr))
      (log/info "AuthPlugin started"))

    (stop [_]
      (log/info "AuthPlugin stopped"))

    (dispose [_]
      (log/info "AuthPlugin disposed"))

    (checkAction [_ acctObj action] )

    (addAccount [_ arg]
      (let [pkey (.appKey ctr)]
        (createLoginAccount
          (getSQLr ctr)
          (:principal arg)
          (-> (:credential arg)
              (passwd<> pkey))
          (dissoc arg
                  :principal :credential)
          [])))

    (login [_ u p]
      (binding
        [*JDBC-POOL* (.acquireDbPool ctr "")
         *META-CACHE* *auth-mcache*]
        (let
          [cur (SecurityUtils/getSubject)
           sss (.getSession cur) ]
          (log/debug "Current user session %s" sss)
          (log/debug "Current user object %s" cur)
          (when-not (.isAuthenticated cur)
            (try!
              ;;(.setRememberMe token true)
              (->>
                (UsernamePasswordToken.
                  ^String u ^String p)
                (.login cur ))
              (log/debug "User [%s] logged in successfully" u)))
          (if (.isAuthenticated cur)
            (.getPrincipal cur)))))

    (hasAccount [_ arg]
      (let [pkey (.appKey ctr)]
        (hasLoginAccount? (getSQLr ctr)
                          (:principal arg))))

    (account [_ arg]
      (let [pkey (.appKey ctr)
            sql (getSQLr ctr) ]
        (cond
          (some? (:principal arg))
          (findLoginAccount sql
                            (:principal arg))
          (some? (:email arg))
          (findLoginAccountViaEmail sql
                            (:email arg))
          :else nil)))

    ;;TODO: get roles please
    (roles [_ acct] [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn pluginFactory<>

  ""
  ^PluginFactory
  []

  (reify PluginFactory
    (createPlugin [_ ctr]
      (authPlugin<> ctr))))

;;(ns-unmap *ns* '->AuthPluginFactory)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain

  ""
  [& args]

  (let [appDir (io/file (first args))
        cmd (nth args 1)
        db (nth args 2)
        env (readEdn (io/file appDir CFG_ENV_CF))
        app (readEdn (io/file appDir CFG_APP_CF))
        pkey (-> (str (get-in app [:info :digest]))
                 (.toCharArray))
        cfg ((keyword db) (get-in env [:databases :jdbc]))
        pwd (-> (passwd<> (:passwd cfg) pkey)
                (.text))]
    (when (some? cfg)
      (let [j (dbspec<> (assoc cfg :passwd pwd))
            t (matchUrl (:url cfg))]
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
(defn -main

  "Main Entry"
  [& args]

  ;; for security, don't just eval stuff
  ;;(alter-var-root #'*read-eval* (constantly false))
  (if (< (count args) 3)
    nil
    (apply doMain args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


