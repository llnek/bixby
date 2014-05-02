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

  comzotohlabscljc.tardis.auth.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (com.zotohlabs.gallifrey.etc PluginFactory Plugin PluginError))
  (:import (com.zotohlabs.gallifrey.runtime AuthError UnknownUser))

  (:import (com.zotohlabs.frwk.net ULFormItems ULFileItem))
  (:import (org.apache.commons.codec.binary Base64))
  (:import (com.zotohlabs.gallifrey.core Container))

  (:import (com.zotohlabs.frwk.dbio DBAPI MetaCache SQLr
                                    JDBCPool JDBCInfo))

  (:import (org.apache.commons.io FileUtils))
  (:import (java.io File IOException))
  (:import (java.util Properties))

  (:import (org.apache.shiro.config IniSecurityManagerFactory))
  (:import (org.apache.shiro SecurityUtils))
  (:import (org.apache.shiro.subject Subject))

  (:import ( com.zotohlabs.wflow If BoolExpr FlowPoint
                                 Activity Pipeline PipelineDelegate PTask Work))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent HTTPResult))
  (:import (com.zotohlabs.wflow.core Job))

  (:use [comzotohlabscljc.util.core :only [notnil? Stringify
                                       MakeMMap juid
                                       test-nonil LoadJavaProps] ])
  (:use [comzotohlabscljc.crypto.codec :only [Pwdify] ])
  (:use [comzotohlabscljc.util.str :only [nsb hgl? strim] ])
  (:use [comzotohlabscljc.net.comms :only [GetFormFields] ])

  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.core.wfs])
  (:use [comzotohlabscljc.tardis.io.webss :only [GetSignupInfo GetLoginInfo Realign!] ])
  (:use [comzotohlabscljc.tardis.auth.dms])
  (:use [comzotohlabscljc.dbio.connect :only [DbioConnect] ])
  (:use [comzotohlabscljc.dbio.core])
  (:require [clojure.data.json :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol AuthPlugin

  ""

  (getRoles [_ acctObj ] )
  (addAccount [_ options] )
  (getAccount [_ options]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkjdbc ""

  ^JDBCInfo
  [^comzotohlabscljc.util.core.MubleAPI impl]

  (let [ cfg (get (.getf impl :cfg) (keyword "_"))
         pkey (.getf impl :appKey) ]
    (MakeJdbc "_" cfg (Pwdify (:passwd cfg) pkey))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getSQLr ""

  ^SQLr
  [^comzotohlabscljc.util.core.MubleAPI impl]

  (-> (DbioConnect (mkjdbc impl) AUTH-MCACHE {})
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
                     (ese (:column (:name (:fields (meta AuthRole)))))
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
(defn CreateLoginAccount  ""

  [^SQLr sql ^String user ^comzotohlabscljc.crypto.codec.Password pwdObj options roleObjs]

  (let [ [p s] (.hashed pwdObj)
         acc (.insert sql (-> (DbioCreateObj :czc.tardis.auth/LoginAccount)
                              (DbioSetFld :email (strim (:email options)))
                              (DbioSetFld :acctid (strim user))
                            ;;(dbio-set-fld :salt s)
                              (DbioSetFld :passwd  p))) ]
    (doseq [ r (seq roleObjs) ]
      (DbioSetM2M { :as :roles :with sql } acc r))
    acc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLoginAccount  ""

  [^SQLr sql ^String user ^comzotohlabscljc.crypto.codec.Password pwdObj]

  (let [ acct (.findOne sql :czc.tardis.auth/LoginAccount { :acctid (strim user) } ) ]
    (cond
      (nil? acct)
      (throw (UnknownUser. user))

      (.validateHash pwdObj (:passwd acct))
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

  [^SQLr sql userObj ^comzotohlabscljc.crypto.codec.Password pwdObj ]

  (let [ [p s] (.hashed pwdObj)
         u (-> userObj
               (DbioSetFld :passwd p)
               (DbioSetFld :salt s)) ]
    (.update sql u)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn UpdateLoginAccount ""

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
                     " where " (ese (:column (:acctid (:fields (meta LoginAccount)))))
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
  []

  (DefPredicate
    (evaluate [_ job]
      (let [^comzotohlabscljc.tardis.core.sys.Element ctr (.container ^Job job)
            ^comzotohlabscljc.tardis.auth.core.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event ^Job job)
            info (GetSignupInfo evt) ]
        (test-nonil "AuthPlugin" pa)
        (with-local-vars [ uid (:email info) ]
          (try
            (when (hgl? (:principal info))
              (var-set uid (:principal info)))
            (log/debug "about to add a user account - " @uid)
            (.setLastResult job { :account (.addAccount pa
                                                        (merge info { :principal @uid } )) })
            (Realign! evt (:account (.getLastResult job)) [])
            true
            (catch Throwable t#
              (log/error t# "")
              (.setLastResult job { :error t# } )
              false)))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeLoginTest ""

  ^BoolExpr
  []

  (DefPredicate
    (evaluate [_ job]
      (let [^comzotohlabscljc.tardis.core.sys.Element ctr (.container ^Job job)
            ^comzotohlabscljc.tardis.auth.core.AuthPlugin
            pa (:auth (.getAttr ctr K_PLUGINS))
            ^HTTPEvent evt (.event ^Job job)
            info (GetLoginInfo evt) ]
        (log/debug "about to login user - " (:principal info))
        (test-nonil "AuthPlugin" pa)
        (try
          (let [ acct (.getAccount pa info)
                 rs (.getRoles pa acct) ]
            (Realign! evt acct rs)
            true)
          (catch Throwable t#
            (log/error t# "")
            (.setLastResult job { :error t# })
            false))

      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- LOGIN-ERROR ""

  ^PTask
  []

  (DefWFTask (perform [_ fw job arg])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- LOGIN-OK ""

  ^PTask
  []

  (DefWFTask (perform [_ fw job arg]
    (let [^HTTPEvent evt (.event ^Job job)
          ^comzotohlabscljc.tardis.io.ios.WebSession ss (.getSession evt) ]
    ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAuthPlugin ""

  ^Plugin
  []

  (let [ impl (MakeMMap) ]
    (reify Plugin

      (contextualize [_ ctr]
        (.setf! impl :appDir (.getAppDir ^Container ctr))
        (.setf! impl :appKey (.getAppKey ^Container ctr)))

      (configure [_ props]
        (let [ dbs (:databases (:env props)) ]
          (.setf! impl :cfg (:jdbc dbs)) ))

      (initialize [_]
        (let []
          (ApplyAuthPluginDDL (mkjdbc impl))
          (init-shiro (.getf impl :appDir)
                      (.getf impl :appKey))))

      (start [_]
        (log/info "AuthPlugin started."))

      (stop [_]
        (log/info "AuthPlugin stopped."))

      (dispose [_]
        (log/info "AuthPlugin disposed."))

      AuthPlugin

      (addAccount [_ options]
        (let [ pkey (.getf impl :appKey)
               sql (getSQLr impl) ]
          (CreateLoginAccount sql
                              (:principal options)
                              (Pwdify (:credential options) pkey)
                              options
                              [])))

      (getAccount [_ options]
        (let [ pkey (.getf impl :appKey)
               sql (getSQLr impl) ]
          (GetLoginAccount sql
                           (:principal options)
                           (Pwdify (:credential options) pkey))))
      (getRoles [_ acct] [])

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype AuthPluginFactory []

  PluginFactory

  (createPlugin [_]
    (require 'comzotohlabscljc.tardis.auth.core)
    (makeAuthPlugin)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doMain ""

  [& args]

  (let [ appDir (File. ^String (nth args 0))
         ^Properties mf (LoadJavaProps (File. appDir "META-INF/MANIFEST.MF"))
         pkey (.getProperty mf "Implementation-Vendor-Id")
         ^String cmd (nth args 1)
         ^String db (nth args 2)
         env (json/read-str
               (FileUtils/readFileToString (File. appDir "conf/env.conf") "utf-8")
               :key-fn keyword)
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

(def ^:private core-eof nil)


