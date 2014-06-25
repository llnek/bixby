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

  cmzlabsclj.tardis.auth.plugin

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (com.zotohlab.gallifrey.etc PluginFactory Plugin PluginError))
  (:import (com.zotohlab.gallifrey.runtime AuthError UnknownUser DuplicateUser))

  (:import (com.zotohlab.frwk.net ULFormItems ULFileItem))
  (:import (org.apache.commons.codec.binary Base64))
  (:import (com.zotohlab.gallifrey.core Container))

  (:import (com.zotohlab.frwk.dbio DBAPI MetaCache SQLr
                                    JDBCPool JDBCInfo))

  (:import (org.apache.commons.io FileUtils))
  (:import (java.io File IOException))
  (:import (java.util Properties))

  (:import (org.apache.shiro.config IniSecurityManagerFactory))
  (:import (org.apache.shiro SecurityUtils))
  (:import (org.apache.shiro.subject Subject))
  (:import (org.apache.shiro.authc UsernamePasswordToken))
  (:import ( com.zotohlab.wflow If BoolExpr FlowPoint
                                 Activity Pipeline
                                 PipelineDelegate PTask Work))
  (:import (com.zotohlab.gallifrey.io HTTPEvent HTTPResult))
  (:import (com.zotohlab.wflow.core Job))

  (:use [cmzlabsclj.nucleus.util.core :only [notnil? Stringify
                                       MakeMMap juid ternary
                                       test-nonil LoadJavaProps] ])
  (:use [cmzlabsclj.nucleus.crypto.codec :only [Pwdify] ])
  (:use [cmzlabsclj.nucleus.util.str :only [nsb hgl? strim] ])
  (:use [cmzlabsclj.nucleus.net.comms :only [GetFormFields] ])

  (:use [cmzlabsclj.tardis.core.constants])
  (:use [cmzlabsclj.tardis.core.wfs])
  (:use [cmzlabsclj.tardis.core.sys])

  (:use [cmzlabsclj.tardis.io.webss])
  (:use [cmzlabsclj.tardis.io.basicauth])
  (:use [cmzlabsclj.tardis.auth.model])
  (:use [cmzlabsclj.nucleus.dbio.connect :only [DbioConnectViaPool] ])
  (:use [cmzlabsclj.nucleus.dbio.core])
  (:require [clojure.data.json :as json]))

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

  (let [ tbl (:table LoginAccount) ]
    (when-not (TableExist? pool tbl)
      (DbioError (str "Expected to find table " tbl ", but table is not found.")))
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
   ^cmzlabsclj.nucleus.crypto.codec.Password pwdObj options roleObjs]

  (let [ [p s] (.hashed pwdObj)
         acc (.insert sql (-> (DbioCreateObj :czc.tardis.auth/LoginAccount)
                              (DbioSetFld :email (strim (:email options)))
                              (DbioSetFld :acctid (strim user))
                            ;;(dbio-set-fld :salt s)
                              (DbioSetFld :passwd  p))) ]
    ;; Currently adding roles to the account is not bound to the
    ;; previous insert. That is, if we fail to set a role, it's
    ;; assumed ok for the account to remain inserted.
    (doseq [ r (seq roleObjs) ]
      (DbioSetM2M { :as :roles :with sql } acc r))
    (log/debug "created new account into db: "
               acc
               "\nwith meta "
               (meta acc))
    acc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FindLoginAccount  ""

  [^SQLr sql ^String user]

  (.findOne sql :czc.tardis.auth/LoginAccount
                            { :acctid (strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginAccount  ""

  [^SQLr sql ^String user ^String pwd]

  (let [ acct (.findOne sql :czc.tardis.auth/LoginAccount
                            { :acctid (strim user) }) ]
    (cond
      (nil? acct)
      (throw (UnknownUser. user))

      (.validateHash ^cmzlabsclj.nucleus.crypto.codec.Password
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

  (notnil? (.findOne sql :czc.tardis.auth/LoginAccount
                        { :acctid (strim user) } )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ChangeLoginAccount ""

  [^SQLr sql userObj ^cmzlabsclj.nucleus.crypto.codec.Password pwdObj ]

  (let [ [p s] (.hashed pwdObj)
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
    (with-local-vars [ u userObj ]
      (doseq [ [f v] (seq details) ]
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
      (let [^cmzlabsclj.tardis.core.sys.Element ctr (.container job)
            ^cmzlabsclj.tardis.auth.plugin.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event job)
            info (ternary (GetSignupInfo evt) {} ) ]
        (test-nonil "AuthPlugin" pa)
        (cond
          (and (hgl? challengeStr)
               (not= challengeStr (nsb (:captcha info))))
          (do
            (.setLastResult job { :error (AuthError. "Broken captcha.") })
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
      (let [^cmzlabsclj.tardis.core.sys.Element ctr (.container job)
            ^cmzlabsclj.tardis.auth.plugin.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event ^Job job)
            info (ternary (GetLoginInfo evt) {} ) ]
        (test-nonil "AuthPlugin" pa)
        (cond
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

  (let [ impl (MakeMMap) ]
    (reify Plugin

      (contextualize [_ c] nil)

      (configure [_ props] nil)

      (initialize [_]
        (let []
          (ApplyAuthPluginDDL (.acquireDbPool ctr ""))))

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
        (let [ pkey (.getAppKey ctr)
               sql (getSQLr ctr) ]
          (CreateLoginAccount sql
                              (:principal options)
                              (Pwdify (:credential options) pkey)
                              options
                              [])))

      (login [_ user pwd]
        (binding [ *JDBC-POOL* (.acquireDbPool ctr "")
                   *META-CACHE* AUTH-MCACHE ]
          (let [ token (UsernamePasswordToken. ^String user ^String pwd)
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
        (let [ pkey (.getAppKey ctr)
               sql (getSQLr ctr) ]
          (HasLoginAccount sql
                           (:principal options))))

      (getAccount [_ options]
        (let [ pkey (.getAppKey ctr)
               sql (getSQLr ctr) ]
          (FindLoginAccount sql
                           (:principal options))))

      (getRoles [_ acct] [])

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype AuthPluginFactory []

  PluginFactory

  (createPlugin [_ ctr]
    (require 'cmzlabsclj.tardis.auth.plugin)
    (makeAuthPlugin ctr)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain ""

  [& args]

  (let [ appDir (File. ^String (nth args 0))
         ^Properties mf (LoadJavaProps (File. appDir "META-INF/MANIFEST.MF"))
         pkey (.toCharArray (.getProperty mf "Implementation-Vendor-Id"))
         ^String cmd (nth args 1)
         ^String db (nth args 2)
         env (json/read-str (ReadConf appDir "env.conf") :key-fn keyword)
         cfg (get (:jdbc (:databases env)) (keyword db)) ]
    (when-not (nil? cfg)
      (let [ j (MakeJdbc db cfg (Pwdify (:passwd cfg) pkey))
             t (MatchJdbcUrl (nsb (:url cfg))) ]
        (cond
          (= "init-db" cmd)
          (let []
            (ApplyAuthPluginDDL j))

          (= "gen-sql" cmd)
          (if (> (count args) 3)
            (ExportAuthPluginDDL t (File. ^String (nth args 3))))

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


