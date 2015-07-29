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

  czlabclj.tardis.auth.plugin

  (:require [czlabclj.xlib.dbio.connect :refer [DbioConnectViaPool]]
            [czlabclj.xlib.i18n.resources :refer [RStr]]
            [czlabclj.xlib.util.core
             :refer
             [tryc
              notnil?
              Stringify
              MakeMMap
              juid
              test-nonil
              LoadJavaProps]]
            [czlabclj.xlib.crypto.codec :refer [Pwdify]]
            [czlabclj.xlib.util.str :refer [nsb hgl? strim]]
            [czlabclj.xlib.util.format :refer [ReadEdn]]
            [czlabclj.xlib.net.comms :refer [GetFormFields]])

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlabclj.tardis.core.consts]
        [czlabclj.xlib.util.wfs]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.io.webss]
        [czlabclj.tardis.io.basicauth]
        [czlabclj.tardis.auth.model]
        [czlabclj.xlib.dbio.core])

  (:import  [com.zotohlab.skaro.runtime AuthError UnknownUser DuplicateUser]
            [com.zotohlab.skaro.etc PluginFactory Plugin PluginError]
            [org.apache.commons.lang3.tuple ImmutablePair]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]
            [org.apache.commons.codec.binary Base64]
            [com.zotohlab.skaro.core Container]
            [com.zotohlab.frwk.util CrappyDataError]
            [com.zotohlab.frwk.i18n I18N]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [com.zotohlab.frwk.dbio DBAPI MetaCache
             SQLr
             JDBCPool JDBCInfo]
            [java.io File IOException]
            [java.util Properties]
            [org.apache.shiro.config IniSecurityManagerFactory]
            [org.apache.shiro SecurityUtils]
            [org.apache.shiro.subject Subject]
            [org.apache.shiro.authc UsernamePasswordToken]
            [com.zotohlab.wflow If BoolExpr
             Activity Job
             PTask Work]
            [com.zotohlab.skaro.io HTTPEvent HTTPResult]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol AuthPlugin

  "A Plugin that uses Apache Shiro for authentication and authorization of
  users."

  (checkAction [_ acctObj action])
  (addAccount [_ options] )
  (hasAccount [_ options] )
  (login [_ user pwd] )
  (getRoles [_ acctObj ] )
  (getAccount [_ options]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AssertPluginOK "Test if the plugin has been initialized,
                      by looking into the db."

  [^JDBCPool pool]

  (let [tbl (:table LoginAccount)]
    (when-not (TableExist? pool tbl)
      (DbioError (RStr (I18N/getBase)
                       "auth.no.table" [tbl])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr ""

  ^SQLr
  [^Container ctr]

  ;; get the default db pool
  (-> (DbioConnectViaPool (.acquireDbPool ctr "") AUTH-MCACHE {})
      (.newSimpleSQLr)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateAuthRole "Create a new auth-role in db."

  [^SQLr sql ^String role ^String desc]

  (.insert sql (-> (DbioCreateObj :czc.tardis.auth/AuthRole)
                   (DbioSetFlds :name role :desc desc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteAuthRole "Delete this role."

  [^SQLr sql role]

  (.exec sql (str "DELETE FROM "
                  (GTable AuthRole)
                  " WHERE "
                  (->> (meta AuthRole)
                       (:fields)
                       (:name)
                       (:column)
                       (ese))
                  " = ?")
             [(strim role)]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListAuthRoles "List all the roles in db."

  [^SQLr sql]

  (.findAll sql :czc.tardis.auth/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateLoginAccount

  "Create a new account.

   options : a set of extra properties, such as email address.
   roleObjs : a list of roles to be assigned to the account."

  [^SQLr sql ^String user
   ^PasswordAPI pwdObj & [options roleObjs]]

  (let [roleObjs (or roleObjs [])
        options (or options {})
        ps (.hashed pwdObj)
        acc (.insert sql (-> (DbioCreateObj :czc.tardis.auth/LoginAccount)
                             (DbioSetFld* (merge {:acctid (strim user)
                                                  :passwd  (.getLeft ps)}
                                                 options)))) ]
    ;; Currently adding roles to the account is not bound to the
    ;; previous insert. That is, if we fail to set a role, it's
    ;; assumed ok for the account to remain inserted.
    (doseq [r roleObjs]
      (DbioSetM2M { :as :roles :with sql } acc r))
    (log/debug "Created new account into db: "
               acc
               "\nwith meta "
               (meta acc))
    acc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccountViaEmail  "Look for account with this email address."

  [^SQLr sql ^String email]

  (.findOne sql
            :czc.tardis.auth/LoginAccount
            { :email (strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccount  "Look for account with this user id."

  [^SQLr sql ^String user]

  (.findOne sql
            :czc.tardis.auth/LoginAccount
            { :acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginAccount  "Get the user account."

  [^SQLr sql ^String user ^String pwd]

  (if-let [acct (FindLoginAccount sql user)]
    (if (.validateHash (Pwdify pwd "")
                       (:passwd acct))
      acct
      (throw (AuthError. (RStr (I18N/getBase) "auth.bad.pwd"))))
    (throw (UnknownUser. user))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasLoginAccount  "Returns true if this user account exists."

  [^SQLr sql ^String user]

  (notnil? (FindLoginAccount sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ChangeLoginAccount "Change the account password."

  [^SQLr sql userObj ^PasswordAPI pwdObj ]

  (let [ps (.hashed pwdObj)
        u (-> userObj
              (DbioSetFlds :passwd (.getLeft ps)
                           :salt (.getRight ps))) ]
    (.update sql u)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UpdateLoginAccount

  "Update account details.

   details : a set of properties such as email address."

  [^SQLr sql userObj details]

  {:pre [(map? details)]}

  (if (empty? details)
    userObj
    (->> (DbioSetFld* userObj details)
         (.update sql))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteLoginAccountRole "Remove a role from this user."

  [^SQLr sql userObj roleObj]

  (DbioClrM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddLoginAccountRole "Add a role to this user."

  [^SQLr sql userObj roleObj]

  (DbioSetM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteLoginAccount "Delete this account."

  [^SQLr sql userObj]

  (.delete sql userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteUser "Delete the account with this user id."

  [^SQLr sql user]

  (.exec sql (str "DELETE FROM " (GTable LoginAccount)
                  " WHERE "
                  (->> (meta LoginAccount)
                       (:fields)
                       (:acctid)
                       (:column)
                       (ese))
                  " =?")
             [ (strim user) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListLoginAccounts "List all user accounts."

  [^SQLr sql]

  (.findAll sql :czc.tardis.auth/LoginAccount))

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
  (log/info "Created shiro security manager."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeSignupTest "Test component of a standard sign-up workflow."

  ^BoolExpr
  [^String challengeStr]

  (DefBoolExpr
    (fn [^Job job]
      (let [^czlabclj.tardis.core.sys.Elmt ctr (.container job)
            ^czlabclj.tardis.auth.plugin.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event job)
            ^czlabclj.tardis.io.webss.WebSS
            mvs (.getSession evt)
            csrf (.getXref mvs)
            rb (I18N/getBase)
            si (try (GetSignupInfo evt)
                    (catch CrappyDataError e# { :e e# }))
            info (or si {})]
        (log/debug "Session csrf = " csrf
                   ", and form token = " (:csrf info))
        (test-nonil "AuthPlugin" pa)
        (cond
          (notnil? (:e info))
          (do
            (.setLastResult job { :error (AuthError. (nsb (:e info))) })
            false)

          (and (hgl? challengeStr)
               (not= challengeStr (nsb (:captcha info))))
          (do
            (.setLastResult job
                            { :error
                             (AuthError. (RStr rb "auth.bad.cha")) })
            false)

          (not= csrf (nsb (:csrf info)))
          (do
            (.setLastResult job
                            { :error
                             (AuthError. (RStr rb "auth.bad.tkn")) })
            false)

          (and (hgl? (:credential info))
               (hgl? (:principal info))
               (hgl? (:email info)))
          (if (.hasAccount pa info)
            (do
              (.setLastResult job { :error
                                   (DuplicateUser. ^String
                                                   (:principal info)) })
              false)
            (do
              (.setLastResult job { :account (.addAccount pa info) })
              true))

          :else
          (do
            (.setLastResult job
                            { :error
                             (AuthError. (RStr rb "auth.bad.req")) })
            false))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeLoginTest ""

  ^BoolExpr
  []

  (DefBoolExpr
    (fn [^Job job]
      (let [^czlabclj.tardis.core.sys.Elmt ctr (.container job)
            ^czlabclj.tardis.auth.plugin.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event job)
            ^czlabclj.tardis.io.webss.WebSS
            mvs (.getSession evt)
            csrf (.getXref mvs)
            rb (I18N/getBase)
            si (try (GetSignupInfo evt)
                    (catch CrappyDataError e#  { :e e# }))
            info (or si {} ) ]
        (log/debug "Session csrf = " csrf
                   ", and form token = " (:csrf info))
        (test-nonil "AuthPlugin" pa)
        (cond

          (notnil? (:e info))
          (do
            (.setLastResult job { :error (AuthError. (nsb (:e info))) })
            false)

          (not= csrf (nsb (:csrf info)))
          (do
            (.setLastResult job
                            { :error
                             (AuthError. (RStr rb "auth.bad.tkn")) })
            false)

          (and (hgl? (:credential info))
               (hgl? (:principal info)))
          (do
            (.setLastResult job {:account (.login pa (:principal info)
                                                     (:credential info)) })
            (notnil? (:account (.getLastResult job))))

          :else
          (do
            (.setLastResult job
                            {:error
                             (AuthError. (RStr rb "auth.bad.req")) })
            false))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAuthPlugin ""

  ^Plugin
  [^Container ctr]

  (let [impl (MakeMMap) ]
    (reify Plugin

      (configure [_ props] )

      (initialize [_]
        (ApplyAuthPluginDDL (.acquireDbPool ctr "")))

      (start [_]
        (AssertPluginOK (.acquireDbPool ctr ""))
        (init-shiro (.getAppDir ctr)
                    (.getAppKey ctr))
        (log/info "AuthPlugin started."))

      (stop [_]
        (log/info "AuthPlugin stopped."))

      (dispose [_]
        (log/info "AuthPlugin disposed."))

      AuthPlugin

      (checkAction [_ acctObj action] )

      (addAccount [_ options]
        (let [pkey (.getAppKey ctr)]
          (CreateLoginAccount (getSQLr ctr)
                              (:principal options)
                              (Pwdify (:credential options) pkey)
                              options
                              [])))

      (login [_ user pwd]
        (binding [*JDBC-POOL* (.acquireDbPool ctr "")
                  *META-CACHE* AUTH-MCACHE ]
          (let [token (UsernamePasswordToken. ^String user ^String pwd)
                cur (SecurityUtils/getSubject)
                sss (.getSession cur) ]
            (log/debug "Current user session " sss)
            (log/debug "Current user object " cur)
            (when-not (.isAuthenticated cur)
              (tryc
                ;;(.setRememberMe token true)
                (.login cur token)
                (log/debug "User [" user "] logged in successfully.")))
            (if (.isAuthenticated cur)
              (.getPrincipal cur)
              nil))))

      (hasAccount [_ options]
        (let [pkey (.getAppKey ctr)]
          (HasLoginAccount (getSQLr ctr)
                           (:principal options))))

      (getAccount [_ options]
        (let [pkey (.getAppKey ctr)
              sql (getSQLr ctr) ]
          (cond
            (notnil? (:principal options))
            (FindLoginAccount sql
                              (:principal options))
            (notnil? (:email options))
            (FindLoginAccountViaEmail sql
                              (:email options))
            :else nil)))

      (getRoles [_ acct] [])

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype AuthPluginFactory []

  PluginFactory

  (createPlugin [_ ctr]
    (require 'czlabclj.tardis.auth.plugin)
    (makeAuthPlugin ctr)
  ))

(ns-unmap *ns* '->AuthPluginFactory)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain ""

  [& args]

  (let [appDir (io/file (first args))
        ^Properties mf
        (LoadJavaProps (io/file appDir "etc/MANIFEST.MF"))
        pkey (-> (.getProperty mf "Implementation-Vendor-Id")
                 (.toCharArray))
        ^String cmd (nth args 1)
        ^String db (nth args 2)
        env (ReadEdn (io/file appDir CFG_ENV_CF))
        cfg ((keyword db) (:jdbc (:databases env))) ]
    (when-not (nil? cfg)
      (let [j (MakeJdbc db cfg (Pwdify (:passwd cfg) pkey))
            t (MatchJdbcUrl (nsb (:url cfg))) ]
        (cond
          (= "init-db" cmd)
          (ApplyAuthPluginDDL j)

          (= "gen-sql" cmd)
          (if (> (count args) 3)
            (ExportAuthPluginDDL t
                                 (io/file (nth args 3))))

          :else
          nil)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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


