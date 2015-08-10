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

  czlab.skaro.auth.plugin

  (:require
    [czlab.xlib.dbio.connect :refer [DbioConnectViaPool]]
    [czlab.xlib.util.core
    :refer [tryc Stringify ce? MakeMMap
    do->false do->true juid test-nonil LoadJavaProps]]
    [czlab.xlib.i18n.resources :refer [RStr]]
    [czlab.xlib.crypto.codec :refer [Pwdify]]
    [czlab.xlib.util.str :refer [hgl? strim]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.xlib.net.comms :refer [GetFormFields]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io])

  (:use [czlab.skaro.core.consts]
        [czlab.xlib.util.wfs]
        [czlab.skaro.core.sys]
        [czlab.skaro.io.webss]
        [czlab.skaro.io.basicauth]
        [czlab.skaro.auth.model]
        [czlab.xlib.dbio.core])

  (:import
    [org.apache.shiro.config IniSecurityManagerFactory]
    [org.apache.commons.lang3.tuple ImmutablePair]
    [com.zotohlab.frwk.net ULFormItems ULFileItem]
    [com.zotohlab.skaro.etc PluginFactory
    Plugin AuthPlugin PluginError]
    [com.zotohlab.skaro.runtime AuthError
    UnknownUser DuplicateUser]
    [org.apache.commons.codec.binary Base64]
    [com.zotohlab.skaro.core Container Muble]
    [com.zotohlab.frwk.util BadDataError]
    [com.zotohlab.frwk.i18n I18N]
    [com.zotohlab.frwk.crypto PasswordAPI]
    [com.zotohlab.frwk.dbio DBAPI MetaCache
    SQLr JDBCPool JDBCInfo]
    [java.io File IOException]
    [java.util Properties]
    [org.apache.shiro SecurityUtils]
    [org.apache.shiro.subject Subject]
    [org.apache.shiro.authc UsernamePasswordToken]
    [com.zotohlab.wflow If BoolExpr
    Activity Job PTask Work]
    [com.zotohlab.skaro.io WebSS HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AssertPluginOK

  "true if the plugin has been initialized,
   by looking into the db"

  [^JDBCPool pool]

  (let [tbl (:table LoginAccount)]
    (when-not (TableExist? pool tbl)
      (DbioError (RStr (I18N/getBase)
                       "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr ""

  ^SQLr
  [^Container ctr]

  ;; get the default db pool
  (-> (DbioConnectViaPool
        (.acquireDbPool ctr "") AUTH-MCACHE {})
      (.newSimpleSQLr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateAuthRole

  "Create a new auth-role in db"

  [^SQLr sql ^String role ^String desc]

  (.insert sql (-> (DbioCreateObj :czc.skaro.auth/AuthRole)
                   (DbioSetFlds :name role :desc desc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteAuthRole

  "Delete this role"

  [^SQLr sql role]

  (.exec sql
         (str "DELETE FROM "
              (GTable AuthRole)
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
(defn ListAuthRoles

  "List all the roles in db"

  [^SQLr sql]

  (.findAll sql :czc.skaro.auth/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateLoginAccount

  "Create a new account
   options : a set of extra properties, such as email address.
   roleObjs : a list of roles to be assigned to the account"

  [^SQLr sql ^String user
   ^PasswordAPI pwdObj & [options roleObjs]]

  (let [roleObjs (or roleObjs [])
        options (or options {})
        ps (.hashed pwdObj)
        acc (->> (-> :czc.skaro.auth/LoginAccount
                     (DbioCreateObj )
                     (DbioSetFld*
                       (merge
                         {:acctid (strim user)
                          :passwd (.getLeft ps)}
                         options)))
                 (.insert sql)) ]
    ;; currently adding roles to the account is not bound to the
    ;; previous insert. That is, if we fail to set a role, it's
    ;; assumed ok for the account to remain inserted
    (doseq [r roleObjs]
      (DbioSetM2M { :as :roles :with sql } acc r))
    (log/debug "created new account %s%s%s%s"
               "into db: "
               acc "\nwith meta\n" (meta acc))
    acc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccountViaEmail

  "Look for account with this email address"

  [^SQLr sql ^String email]

  (.findOne sql
            :czc.skaro.auth/LoginAccount
            {:email (strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccount

  "Look for account with this user id"

  [^SQLr sql ^String user]

  (.findOne sql
            :czc.skaro.auth/LoginAccount
            {:acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginAccount

  "Get the user account"

  [^SQLr sql ^String user ^String pwd]

  (if-some [acct (FindLoginAccount sql user)]
    (if (.validateHash (Pwdify pwd)
                       (:passwd acct))
      acct
      (throw (AuthError. (RStr (I18N/getBase) "auth.bad.pwd"))))
    (throw (UnknownUser. user))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasLoginAccount

  "true if this user account exists"

  [^SQLr sql ^String user]

  (some? (FindLoginAccount sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ChangeLoginAccount

  "Change the account password"

  [^SQLr sql userObj ^PasswordAPI pwdObj ]

  (let [ps (.hashed pwdObj)
        u (-> userObj
              (DbioSetFlds :passwd (.getLeft ps)
                           :salt (.getRight ps))) ]
    (.update sql u)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UpdateLoginAccount

  "Update account details
   details: a set of properties such as email address"

  [^SQLr sql userObj details]

  {:pre [(map? details)]}

  (if (empty? details)
    userObj
    (->> (DbioSetFld* userObj details)
         (.update sql))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteLoginAccountRole

  "Remove a role from this user"

  [^SQLr sql userObj roleObj]

  (DbioClrM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddLoginAccountRole

  "Add a role to this user"

  [^SQLr sql userObj roleObj]

  (DbioSetM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteLoginAccount

  "Delete this account"

  [^SQLr sql userObj]

  (.delete sql userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteUser

  "Delete the account with this user id"

  [^SQLr sql user]

  (.exec sql
         (str "DELETE FROM "
              (GTable LoginAccount)
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
(defn ListLoginAccounts

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
(defn MaybeSignupTest

  "Test component of a standard sign-up workflow"

  ^BoolExpr
  [^String challengeStr]

  (DefBoolExpr
    (fn [^Job job]
      (let
        [^HTTPEvent evt (.event job)
         csrf (-> ^WebSS
                  (.getSession evt)
                  (.getXref))
         si (try
              (GetSignupInfo evt)
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
            (->> {:error (AuthError. (ce? (:e info)))}
                 (.setLastResult job)))

          (and (hgl? challengeStr)
               (not= challengeStr (:captcha info)))
          (do->false
            (->> {:error (AuthError. (RStr rb "auth.bad.cha")) }
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (AuthError. (RStr rb "auth.bad.tkn")) }
                 (.setLastResult job)))

          (and (hgl? (:credential info))
               (hgl? (:principal info))
               (hgl? (:email info)))
          (if (.hasAccount pa info)
            (do->false
              (->> {:error (DuplicateUser. (str (:principal info)))}
                   (.setLastResult job )))
            (do->true
              (->> {:account (.addAccount pa info)}
                   (.setLastResult job))))

          :else
          (do->false
            (->> {:error (AuthError. (RStr rb "auth.bad.req"))}
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeLoginTest ""

  ^BoolExpr
  []

  (DefBoolExpr
    (fn [^Job job]
      (let
        [^HTTPEvent evt (.event job)
         csrf (-> ^WebSS
                  (.getSession evt)
                  (.getXref ))
         si (try
              (GetSignupInfo evt)
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
            (->> {:error (AuthError. (ce? (:e info)))}
                 (.setLastResult job)))

          (not= csrf (:csrf info))
          (do->false
            (->> {:error (AuthError. (RStr rb "auth.bad.tkn"))}
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
            (->> {:error (AuthError. (RStr rb "auth.bad.req")) }
                 (.setLastResult job))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAuthPlugin ""

  ^Plugin
  [^Container ctr]

  (let [impl (MakeMMap) ]
    (reify AuthPlugin

      (configure [_ props] )

      (initialize [_]
        (-> (.acquireDbPool ctr "")
            (applyDDL )))

      (start [_]
        (AssertPluginOK (.acquireDbPool ctr ""))
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
          (CreateLoginAccount
            (getSQLr ctr)
            (:principal options)
            (-> (:credential options)
                (Pwdify pkey))
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
          (HasLoginAccount (getSQLr ctr)
                           (:principal options))))

      (getAccount [_ options]
        (let [pkey (.getAppKey ctr)
              sql (getSQLr ctr) ]
          (cond
            (some? (:principal options))
            (FindLoginAccount sql
                              (:principal options))
            (some? (:email options))
            (FindLoginAccountViaEmail sql
                              (:email options))
            :else nil)))

      (getRoles [_ acct] []))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AuthPluginFactory ""

  ^PluginFactory
  []

  (reify PluginFactory
    (createPlugin [_ ctr]
      (makeAuthPlugin ctr)
    )))

;;(ns-unmap *ns* '->AuthPluginFactory)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain ""

  [& args]

  (let [appDir (io/file (first args))
        cmd (nth args 1)
        db (nth args 2)
        env (ReadEdn (io/file appDir CFG_ENV_CF))
        app (ReadEdn (io/file appDir CFG_APP_CF))
        pkey (-> (str (get-in app [:info :disposition]))
                 (.toCharArray))
        cfg ((keyword db) (get-in env [:databases :jdbc])) ]
    (when (some? cfg)
      (let [j (MakeJdbc db cfg (Pwdify (:passwd cfg) pkey))
            t (MatchJdbcUrl (:url cfg)) ]
        (cond
          (= "init-db" cmd)
          (applyDDL j)

          (= "gen-sql" cmd)
          (if (> (count args) 3)
            (ExportAuthPluginDDL t
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
    (apply doMain args)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

