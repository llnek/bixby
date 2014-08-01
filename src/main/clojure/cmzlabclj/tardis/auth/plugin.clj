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

  cmzlabclj.tardis.auth.plugin

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr]
            [clojure.data.json :as json])

  (:use [cmzlabclj.nucleus.util.core :only [notnil? Stringify
                                       MakeMMap juid ternary
                                       test-nonil LoadJavaProps] ]
        [cmzlabclj.nucleus.crypto.codec :only [Pwdify] ]
        [cmzlabclj.nucleus.util.str :only [nsb hgl? strim] ]
        [cmzlabclj.nucleus.net.comms :only [GetFormFields] ]
        [cmzlabclj.tardis.core.constants]
        [cmzlabclj.tardis.core.wfs]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.io.webss]
        [cmzlabclj.tardis.io.basicauth]
        [cmzlabclj.tardis.auth.model]
        [cmzlabclj.nucleus.dbio.connect :only [DbioConnectViaPool] ]
        [cmzlabclj.nucleus.dbio.core])

  (:import  [com.zotohlab.gallifrey.etc PluginFactory Plugin PluginError]
            [com.zotohlab.gallifrey.runtime AuthError UnknownUser DuplicateUser]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]
            [org.apache.commons.codec.binary Base64]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.frwk.util CrappyDataError]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [com.zotohlab.frwk.dbio DBAPI MetaCache SQLr
                                    JDBCPool JDBCInfo]
            [org.apache.commons.io FileUtils]
            [java.io File IOException]
            [java.util Properties]
            [org.apache.shiro.config IniSecurityManagerFactory]
            [org.apache.shiro SecurityUtils]
            [org.apache.shiro.subject Subject]
            [org.apache.shiro.authc UsernamePasswordToken]
            [com.zotohlab.wflow If BoolExpr FlowPoint
                                Activity Pipeline
                                PipelineDelegate PTask Work]
            [com.zotohlab.gallifrey.io HTTPEvent HTTPResult]
            [com.zotohlab.wflow.core Job]))

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
(defn AssertPluginOK ""

  [^JDBCPool pool]

  (let [tbl (:table LoginAccount) ]
    (when-not (TableExist? pool tbl)
      (DbioError (str "Expected to find table "
                      tbl
                      ", but table is not found.")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr ""

  ^SQLr
  [^Container ctr]

  (-> (DbioConnectViaPool (.acquireDbPool ctr "") AUTH-MCACHE {})
      (.newSimpleSQLr)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateAuthRole ""

  [^SQLr sql ^String role ^String desc]

  (.insert sql (-> (DbioCreateObj :czc.tardis.auth/AuthRole)
                   (DbioSetFld :name role)
                   (DbioSetFld :desc desc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RemoveAuthRole ""

  [^SQLr sql role]

  (.execute sql (str "delete from "
                     (ese (:table AuthRole))
                     " where "
                     ;;(ese (:column (:name (:fields (meta AuthRole)))))
                     (->> (meta AuthRole)
                          (:fields)
                          (:name)
                          (:column)
                          (ese))
                     " = ?")
                [(nsb role)]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListAuthRoles ""

  [^SQLr sql]

  (.findAll sql :czc.tardis.auth/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateLoginAccount

  "options : a set of extra properties, such as email address.
  roleObjs : a list of roles to be assigned to the account."

  [^SQLr sql ^String user
   ^PasswordAPI pwdObj options roleObjs]

  (let [[p s] (.hashed pwdObj)
        acc (.insert sql (-> (DbioCreateObj :czc.tardis.auth/LoginAccount)
                             (DbioSetFld :email (strim (:email options)))
                             (DbioSetFld :acctid (strim user))
                            ;;(dbio-set-fld :salt s)
                             (DbioSetFld :passwd  p))) ]
    ;; Currently adding roles to the account is not bound to the
    ;; previous insert. That is, if we fail to set a role, it's
    ;; assumed ok for the account to remain inserted.
    (doseq [r (seq roleObjs) ]
      (DbioSetM2M { :as :roles :with sql } acc r))
    (log/debug "created new account into db: "
               acc
               "\nwith meta "
               (meta acc))
    acc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccountViaEmail  ""

  [^SQLr sql ^String email]

  (.findOne sql
            :czc.tardis.auth/LoginAccount
            { :email (strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccount  ""

  [^SQLr sql ^String user]

  (.findOne sql
            :czc.tardis.auth/LoginAccount
            { :acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginAccount  ""

  [^SQLr sql ^String user ^String pwd]

  (let [acct (.findOne sql
                       :czc.tardis.auth/LoginAccount
                       { :acctid (strim user) }) ]
    (cond
      (nil? acct)
      (throw (UnknownUser. user))

      (.validateHash ^PasswordAPI
                     (Pwdify pwd "")
                     (:passwd acct))
      acct

      :else
      (throw (AuthError. "Incorrect password")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasLoginAccount  ""

  [^SQLr sql ^String user]

  (notnil? (.findOne sql
                     :czc.tardis.auth/LoginAccount
                     { :acctid (strim user) } )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ChangeLoginAccount ""

  [^SQLr sql userObj ^PasswordAPI pwdObj ]

  (let [[p s] (.hashed pwdObj)
        u (-> userObj
              (DbioSetFld :passwd p)
              (DbioSetFld :salt s)) ]
    (.update sql u)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UpdateLoginAccount

  "details : a set of properties such as email address."

  [^SQLr sql userObj details]

  (if (empty? details)
    userObj
    (with-local-vars [u userObj ]
      (doseq [[f v] (seq details) ]
        (var-set u (DbioSetFld @u f v)))
      (.update sql @u))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RemoveLoginAccountRole ""

  [^SQLr sql userObj roleObj]

  (DbioClrM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddLoginAccountRole ""

  [^SQLr sql userObj roleObj]

  (DbioSetM2M {:as :roles :with sql } userObj roleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RemoveLoginAccount ""

  [^SQLr sql userObj]

  (.delete sql userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteLoginAccount ""

  [^SQLr sql user]

  (.execute sql (str "delete from " (ese (:table LoginAccount))
                     " where "
                     ;;(ese (:column (:acctid (:fields (meta LoginAccount)))))
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
(defn ListLoginAccounts ""

  [^SQLr sql]

  (.findAll sql :czc.tardis.auth/LoginAccount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-shiro ""

  [^File appDir ^String appKey]

  (let [ ini (File. appDir "conf/shiro.ini")
         sm (-> (IniSecurityManagerFactory. (-> ini (.toURI)(.toURL)(.toString)))
                (.getInstance)) ]
    (SecurityUtils/setSecurityManager sm)
    (log/info "created shiro security manager: " sm)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Work Flow
;;
(defn MaybeSignupTest ""

  ^BoolExpr
  [^String challengeStr]

  (DefPredicate
    (fn [^Job job]
      (let [^cmzlabclj.tardis.core.sys.Element ctr (.container job)
            ^cmzlabclj.tardis.auth.plugin.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event job)
            ^cmzlabclj.tardis.io.webss.WebSession
            mvs (.getSession evt)
            csrf (.getXref mvs)
            si (try (GetSignupInfo evt)
                    (catch CrappyDataError e# { :e e# }))
            info (ternary si {}) ]
        (log/debug "session csrf = " csrf ", and form token = " (:csrf info))
        (test-nonil "AuthPlugin" pa)
        (cond
          (notnil? (:e info))
          (do
            (.setLastResult job { :error (AuthError. (nsb (:e info))) })
            false)

          (and (hgl? challengeStr)
               (not= challengeStr (nsb (:captcha info))))
          (do
            (.setLastResult job { :error (AuthError. "Broken captcha.") })
            false)

          (not (= csrf (:csrf info)))
          (do
            (.setLastResult job { :error (AuthError. "Broken token.") })
            false)

          (and (hgl? (:credential info))
               (hgl? (:principal info))
               (hgl? (:email info)))
          (if (.hasAccount pa info)
            (do
              (.setLastResult job { :error (DuplicateUser. ^String (:principal info)) })
              false)
            (do
              (.setLastResult job { :account (.addAccount pa info) })
              true))

          :else
          (do
            (.setLastResult job { :error (AuthError. "Bad Request") })
            false))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeLoginTest ""

  ^BoolExpr
  []

  (DefPredicate
    (fn [^Job job]
      (let [^cmzlabclj.tardis.core.sys.Element ctr (.container job)
            ^cmzlabclj.tardis.auth.plugin.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event job)
            ^cmzlabclj.tardis.io.webss.WebSession
            mvs (.getSession evt)
            csrf (.getXref mvs)
            si (try (GetSignupInfo evt)
                    (catch CrappyDataError e#  { :e e# }))
            info (ternary si {} ) ]
        (log/debug "session csrf = " csrf ", and form token = " (:csrf info))
        (test-nonil "AuthPlugin" pa)
        (cond

          (notnil? (:e info))
          (do
            (.setLastResult job { :error (AuthError. (nsb (:e info))) })
            false)

          (not (= csrf (:csrf info)))
          (do
            (.setLastResult job { :error (AuthError. "Broken token.") })
            false)

          (and (hgl? (:credential info))
               (hgl? (:principal info)))
          (do
            (.setLastResult job {:account (.login pa (:principal info)
                                                     (:credential info)) })
            (notnil? (:account (.getLastResult job))))

          :else
          (do
            (.setLastResult job {:error (AuthError. "Bad Request") })
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

      (contextualize [_ c] nil)

      (configure [_ props] nil)

      (initialize [_] (ApplyAuthPluginDDL (.acquireDbPool ctr "")))

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

      (checkAction [_ acctObj action])

      (addAccount [_ options]
        (let [pkey (.getAppKey ctr)
              sql (getSQLr ctr) ]
          (CreateLoginAccount sql
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
            (log/debug "current user session " sss)
            (log/debug "current user object " cur)
            (when-not (.isAuthenticated cur)
              (try
                ;;(.setRememberMe token true)
                (.login cur token)
                (log/debug "user [" user "] logged in successfully.")
                (catch Exception e#
                  (log/error e# ""))))
            (if (.isAuthenticated cur)
              (.getPrincipal cur)
              nil))))

      (hasAccount [_ options]
        (let [pkey (.getAppKey ctr)
              sql (getSQLr ctr) ]
          (HasLoginAccount sql
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
    (require 'cmzlabclj.tardis.auth.plugin)
    (makeAuthPlugin ctr)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain ""

  [& args]

  (let [appDir (File. ^String (nth args 0))
        ^Properties mf (LoadJavaProps (File. appDir "META-INF/MANIFEST.MF"))
        pkey (.toCharArray (.getProperty mf "Implementation-Vendor-Id"))
        ^String cmd (nth args 1)
        ^String db (nth args 2)
        env (json/read-str (ReadConf appDir "env.conf")
                           :key-fn keyword)
        cfg (get (:jdbc (:databases env))
                 (keyword db)) ]
    (when-not (nil? cfg)
      (let [j (MakeJdbc db cfg (Pwdify (:passwd cfg) pkey))
            t (MatchJdbcUrl (nsb (:url cfg))) ]
        (cond
          (= "init-db" cmd)
          (ApplyAuthPluginDDL j)

          (= "gen-sql" cmd)
          (if (> (count args) 3)
            (ExportAuthPluginDDL t
                                 (File. ^String (nth args 3))))

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

(def ^:private plugin-eof nil)


