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
      :author "Kenneth Leung"}

  czlab.wabbit.auth.plugin

  (:require [czlab.convoy.net.util :refer [filterFormFields]]
            [czlab.horde.dbio.connect :refer [dbopen<+>]]
            [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.resources :refer [rstr]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.auth.model]
        [czlab.wabbit.auth.core]
        [czlab.wabbit.sys.core]
        [czlab.wabbit.mvc.web]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.horde.dbio.core])

  (:import [czlab.wabbit.etc AuthError UnknownUser DuplicateUser]
           [czlab.convoy.net HttpResult ULFormItems ULFileItem]
           [org.apache.shiro.config IniSecurityManagerFactory]
           [org.apache.shiro.authc UsernamePasswordToken]
           [czlab.xlib Muble I18N BadDataError]
           [org.apache.shiro.subject Subject]
           [org.apache.shiro SecurityUtils]
           [czlab.wabbit.server Container]
           [clojure.lang APersistentMap]
           [java.io File IOException]
           [czlab.twisty IPassword]
           [java.util Properties]
           [czlab.flux.wflow
            BoolExpr
            TaskDef
            If
            Job
            Script]
           [czlab.wabbit.etc
            Plugin
            AuthPlugin
            PluginError
            PluginFactory]
           [czlab.horde
            DBAPI
            Schema
            SQLr
            JDBCPool
            JDBCInfo]
           [czlab.wabbit.io HttpEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:dynamic *META-CACHE* nil)
(def ^:dynamic *JDBC-POOL* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assertPluginOK
  "If the plugin has been initialized,
   by looking into the db"
  [^JDBCPool pool]
  {:pre [(some? pool)]}
  (let [tbl (->> :czlab.wabbit.auth.model/LoginAccount
                 (.get ^Schema *auth-meta-cache*)
                 (dbtable))]
    (when-not (tableExist? pool tbl)
      (dberr! (rstr (I18N/base)
                    "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr
  ""
  {:tag SQLr}
  ([ctr] (getSQLr ctr false))
  ([^Container ctr tx?]
   {:pre [(some? ctr)]}
   (let [db (-> (.acquireDbPool ctr)
                (dbopen<+> *auth-meta-cache*))]
     (if (boolean tx?)
       (.compositeSQLr db)
       (.simpleSQLr db)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createAuthRole
  "Create a new auth-role in db"
  ^APersistentMap
  [^SQLr sql ^String role ^String desc]
  {:pre [(some? sql)]}
  (let [m (.get (.metas sql)
                :czlab.wabbit.auth.model/AuthRole)
        rc (-> (dbpojo<> m)
               (dbSetFlds* {:name role
                            :desc desc}))]
    (.insert sql rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteAuthRole
  "Delete this role"
  [^SQLr sql ^String role]
  {:pre [(some? sql)]}
  (let [m (.get (.metas sql)
                :czlab.wabbit.auth.model/AuthRole)]
    (.exec sql
           (format
             "delete from %s where %s =?"
             (.fmtId sql (dbtable m))
             (.fmtId sql (dbcol :name m))) [(strim role)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listAuthRoles
  "List all the roles in db"
  ^Iterable
  [^SQLr sql]
  {:pre [(some? sql)]}
  (.findAll sql :czlab.wabbit.auth.model/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createLoginAccount
  "Create a new account
   props : extra properties, such as email address.
   roleObjs : a list of roles to be assigned to the account"
  {:tag APersistentMap}

  ([sql user pwdObj props] (createLoginAccount sql user pwdObj props nil))
  ([sql user pwdObj] (createLoginAccount sql user pwdObj nil nil))
  ([^SQLr sql ^String user ^IPassword pwdObj props roleObjs]
   {:pre [(some? sql)(hgl? user)]}
   (let [m (.get (.metas sql)
                 :czlab.wabbit.auth.model/LoginAccount)
         ps (if (some? pwdObj)
              (:hash (.hashed pwdObj)))
         acc
         (->>
           (dbSetFlds* (dbpojo<> m)
                       (merge props {:acctid (strim user)
                                     :passwd ps}))
           (.insert sql))]
     ;; currently adding roles to the account is not bound to the
     ;; previous insert. That is, if we fail to set a role, it's
     ;; assumed ok for the account to remain inserted
     (doseq [r roleObjs]
       (dbSetM2M {:joined :czlab.wabbit.auth.model/AccountRoles
                  :with sql} acc r))
     (log/debug "created new account %s%s%s%s"
                "into db: " acc "\nwith meta\n" (meta acc))
     acc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn findLoginAccountViaEmail
  "Look for account with this email address"
  ^APersistentMap
  [^SQLr sql ^String email]
  {:pre [(some? sql)]}
  (.findOne sql
            :czlab.wabbit.auth.model/LoginAccount
            {:email (strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn findLoginAccount
  "Look for account with this user id"
  ^APersistentMap
  [^SQLr sql ^String user]
  {:pre [(some? sql)]}
  (.findOne sql
            :czlab.wabbit.auth.model/LoginAccount
            {:acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLoginAccount
  "Get the user account"
  [^SQLr sql ^String user ^String pwd]
  (if-some
    [acct (findLoginAccount sql user)]
    (if (.validateHash (passwd<> pwd)
                       (:passwd acct))
      acct
      (trap! AuthError (rstr (I18N/base) "auth.bad.pwd")))
    (trap! UnknownUser user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hasLoginAccount?
  "If this user account exists"
  [^SQLr sql ^String user] (some? (findLoginAccount sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn changeLoginAccount
  "Change the account password"
  ^APersistentMap
  [^SQLr sql userObj ^IPassword pwdObj]
  {:pre [(some? sql)
         (map? userObj)(some? pwdObj)]}
  (let [ps (.hashed pwdObj)
        m {:passwd (:hash ps)
           :salt (:salt ps)}]
    (->> (dbSetFlds*
           (mockPojo<> userObj) m)
         (.update sql))
    (dbSetFlds* userObj m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn updateLoginAccount
  "Update account details
   details: a set of properties such as email address"
  ^APersistentMap
  [^SQLr sql userObj details]
  {:pre [(some? sql)(map? userObj)]}
  (if-not (empty? details)
    (do
      (->> (dbSetFlds*
             (mockPojo<> userObj) details)
           (.update sql))
      (dbSetFlds* userObj details))
    userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteLoginAccountRole
  "Remove a role from this user"
  ^long
  [^SQLr sql user role]
  {:pre [(some? sql)]}
  (dbClrM2M
    {:joined :czlab.wabbit.auth.model/AccountRoles :with sql} user role))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addLoginAccountRole
  "Add a role to this user"
  ^APersistentMap
  [^SQLr sql user role]
  {:pre [(some? sql)]}
  (dbSetM2M
    {:joined :czlab.wabbit.auth.model/AccountRoles :with sql} user role))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteLoginAccount
  "Delete this account"
  ^long
  [^SQLr sql user] {:pre [(some? sql)]} (.delete sql user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deleteUser
  "Delete the account with this user id"
  ^long
  [^SQLr sql ^String user]
  {:pre [(some? sql)]}
  (let [m (.get (.metas sql)
                :czlab.wabbit.auth.model/LoginAccount)]
    (.exec sql
           (format
             "delete from %s where %s =?"
             (.fmtId sql (dbtable m))
             (.fmtId sql (dbcol :acctid m))) [(strim user)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listLoginAccounts
  "List all user accounts"
  ^Iterable
  [^SQLr sql]
  {:pre [(some? sql)]}
  (.findAll sql :czlab.wabbit.auth.model/LoginAccount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-shiro
  ""
  [^File podDir ^String podKey]
  (-> (io/file podDir "conf/shiro.ini")
      (io/as-url )
      str
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
    (ptest [_ job]
      (let
        [^HttpEvent evt (.event job)
         csrf (.. evt session xref)
         info (try
                (getSignupInfo evt)
                (catch BadDataError _ {:e _}))
         info (or info {})
         rb (I18N/base)
         ^AuthPlugin
         pa (-> ^Muble (.server job)
                (.getv :plugins)
                (:auth ))]
        (log/debug "session csrf = %s%s%s"
                   csrf ", and form token = " (:csrf info))
        (cond
          (some? (:e info))
          (do->false
            (->> {:error (exp! AuthError
                               ""
                               (cexp? (:e info)))}
                 (.setLastResult job)))

          (and (hgl? challengeStr)
               (not= challengeStr (:captcha info)))
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.cha"))}
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.tkn"))}
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
    (ptest [_ job]
      (let
        [^HttpEvent evt (.event job)
         csrf (.. evt session xref)
         info (try
                (getSignupInfo evt)
                (catch BadDataError _ {:e _}))
         info (or info {})
         rb (I18N/base)
         ^AuthPlugin
         pa (-> ^Muble
                (.server job)
                (.getv :plugins)
                (:auth ))]
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
                                      (:credential info))}
                 (.setLastResult job))
            (some? (:account (.lastResult job))))

          :else
          (do->false
            (->> {:error (exp! AuthError
                               (rstr rb "auth.bad.req"))}
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- authPlugin<>
  ""
  ^Plugin
  [^Container ctr]
  (reify AuthPlugin

    (init [_ arg]
      (applyDDL (.acquireDbPool ctr)))

    (start [_]
      (assertPluginOK (.acquireDbPool ctr))
      (init-shiro (.podDir ctr)
                  (.podKey ctr))
      (log/info "AuthPlugin started"))

    (stop [_]
      (log/info "AuthPlugin stopped"))

    (dispose [_]
      (log/info "AuthPlugin disposed"))

    (checkAction [_ acctObj action] )

    (addAccount [_ arg]
      (let [pkey (.podKey ctr)]
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
        [*JDBC-POOL* (.acquireDbPool ctr)
         *META-CACHE* *auth-meta-cache*]
        (let
          [cur (SecurityUtils/getSubject)
           sss (.getSession cur)]
          (log/debug "Current user session %s" sss)
          (log/debug "Current user object %s" cur)
          (when-not (.isAuthenticated cur)
            (try!
              ;;(.setRememberMe token true)
              (.login cur
                      (UsernamePasswordToken. ^String u ^String p))
              (log/debug "User [%s] logged in successfully" u)))
          (if (.isAuthenticated cur)
            (.getPrincipal cur)))))

    (hasAccount [_ arg]
      (let [pkey (.podKey ctr)]
        (hasLoginAccount? (getSQLr ctr)
                          (:principal arg))))

    (account [_ arg]
      (let [pkey (.podKey ctr)
            sql (getSQLr ctr)]
        (cond
          (hgl? (:principal arg))
          (findLoginAccount sql
                            (:principal arg))
          (hgl? (:email arg))
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
  (let [podDir (io/file (first args))
        cmd (nth args 1)
        db (nth args 2)
        pod (slurpXXXConf podDir CFG_POD_CF true)
        pkey (-> (get-in pod [:info :digest])
                 str
                 (.toCharArray))
        cfg (get-in pod [:rdbms (keyword db)])]
    (when (some? cfg)
      (let [pwd (.text (passwd<> (:passwd cfg) pkey))
            j (dbspec<> (assoc cfg :passwd pwd))
            t (matchUrl (:url cfg))]
        (cond
          (= "init-db" cmd)
          (applyDDL j)

          (= "gen-sql" cmd)
          (if (> (count args) 3)
            (exportAuthPluginDDL t
                                 (io/file (nth args 3)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; home gen-sql alias outfile
;; home init-db alias
(defn -main
  "Main Entry"
  [& args]

  ;; for security, don't just eval stuff
  ;;(alter-var-root #'*read-eval* (constantly false))
  (if-not (< (count args) 3) (apply doMain args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


