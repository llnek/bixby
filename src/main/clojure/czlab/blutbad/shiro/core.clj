;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.shiro.core

  ;;(:gen-class)

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.niou.util :as ct]
            [czlab.niou.webss  :as ss]
            [czlab.niou.mime :as mi]
            [czlab.niou.core :as cc]
            [czlab.niou.upload :as cu]
            [czlab.twisty.codec :as co]
            [czlab.hoard.core :as hc]
            [czlab.hoard.rels :as hr]
            [czlab.hoard.connect :as ht]
            [czlab.blutbad.core :as b]
            [czlab.blutbad.plugs.http :as hp]
            [czlab.blutbad.shiro.model :as mo])

  (:import [org.apache.shiro.authc.credential CredentialsMatcher]
           [org.apache.shiro.config IniSecurityManagerFactory]
           [org.apache.shiro.authc UsernamePasswordToken]
           [java.security GeneralSecurityException]
           [org.apache.commons.fileupload FileItem]
           [czlab.niou.upload ULFormItems]
           [czlab.basal DataError XData]
           [clojure.lang APersistentMap]
           [java.io File IOException]
           [org.apache.shiro.authz
            AuthorizationException
            AuthorizationInfo]
           [org.apache.shiro.authc
            SimpleAccount
            AuthenticationException
            AuthenticationToken
            AuthenticationInfo]
           [java.net HttpCookie]
           [java.util Properties]
           [java.util Base64 Base64$Decoder]
           [org.apache.shiro SecurityUtils]
           [org.apache.shiro.subject Subject]
           [org.apache.shiro.realm AuthorizingRealm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- ^String nonce-param "nonce_token")
(c/def- ^String csrf-param "csrf_token")
(c/def- ^String pwd-param "credential")
(c/def- ^String email-param "email")
(c/def- ^String user-param "principal")
(c/def- ^String captcha-param "captcha")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;hard code the shift position, the encrypt code
;;should match this value.
(c/def- caesar-shift 13)
(c/def- props-map
  {captcha-param [:captcha #(c/strim %)]
   user-param [:principal #(c/strim %)]
   pwd-param [:credential #(c/strim %)]
   csrf-param [:csrf #(c/strim %)]
   nonce-param [:nonce #(some? %)]
   email-param [:email #(mi/normalize-email %)]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:dynamic *meta-cache* nil)
(def ^:dynamic *jdbc-pool* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol WebAuthPluginAPI
  (do-login [_ user pwd] "")
  (add-account [_ options] "")
  (has-account? [_ options] "")
  (get-roles [_ acctObj] "")
  (get-account [_ options] "")
  (check-action [_ acctObj action] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn new-session<>

  ([evt]
   (new-session<> evt nil))

  ([evt attrs]
   (let [plug (c/parent evt)]
     (c/do-with
       [s (ss/wsession<> (-> plug c/parent b/pkey-bytes)
                         (:session (:conf plug)))]
       (doseq [[k v] attrs] (ss/set-session-attr s k v))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn csrf-token<>

  "Create or delete a csrf cookie,
  if maxAge=-1, browser doesnt sent it back!"
  ^HttpCookie
  [{:keys [domain domain-path]} token]

  (c/do-with
    [c (HttpCookie. ss/csrf-cookie
                    (if (c/hgl? token) token "*"))]
    (if (c/hgl? domain-path) (.setPath c domain-path))
    (if (c/hgl? domain) (.setDomain c domain))
    (.setHttpOnly c true)
    (.setMaxAge c (if (c/hgl? token) 3600 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- crack-form-fields

  "Parse a standard login-like form,
  with userid,password,email."
  [{:keys [^XData body] :as evt}]

  (when-some
    [itms (c/cast? ULFormItems
                   (some-> body .content))]
    (c/preduce<map>
      #(let [fm (cu/get-field-name-lc %2)
             fv (.getString ^FileItem %2)
             [k v] (get props-map fm)]
         (c/debug "fld=%s, val=%s." fm fv)
         (c/if-nil k %1 (assoc! %1 k (v fv)))) (cu/get-all-fields itms))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- crack-body-content

  "Parse a JSON body."
  [{:keys [^XData body] :as evt}]

  (let [xs (some-> body .getBytes)
        json (->> #(c/lcase %)
                  (i/read-json (c/if-nil xs
                                 "{}"
                                 (->> (.encoding body)
                                      (i/x->str xs)))))]
    (c/preduce<map>
      #(let [[k [a1 a2]] %2
             v (get json k)]
         (c/if-nil v %1 (assoc! %1 a1 (a2 v)))) props-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- crack-params

  "Parse form fields in the Url."
  [{:keys [body] :as evt}]

  (c/preduce<map>
    #(let [[k [a1 a2]] %2
           v (cc/gist-param? evt k)]
       (c/if-nil v %1 (assoc! %1 a1 (a2 v)))) props-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-auth-info??

  "Attempt to parse and get authentication info."
  [evt]

  (c/if-some+ [ct (cc/msg-header evt "content-type")]
    (cond (or (c/embeds? ct "form-data")
              (c/embeds? ct "form-urlencoded"))
          (crack-form-fields evt)
          (c/embeds? ct "/json")
          (crack-body-content evt)
          :else (crack-params evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- decode-field??

  [info fld shiftCount]

  (c/if-nil (:nonce info)
    info
    (c/try!
      (let [decr (co/decrypt (co/caesar<>)
                             shiftCount
                             (get info fld))
            s (-> (Base64/getMimeDecoder)
                  (.decode ^String decr) i/x->str)]
        (c/debug "val = %s, decr = %s." s decr)
        (assoc info fld s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/defmacro- get-xxx-info

  [evt] `(-> (get-auth-info?? ~evt)
             (decode-field?? :principal caesar-shift)
             (decode-field?? :credential caesar-shift)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-signup-info

  [evt] (get-xxx-info evt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-login-info

  [evt] (get-xxx-info evt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- assert-plugin-ok

  "If the plugin has been initialized,
  by looking into the db."
  [pool]
  {:pre [(some? pool)]}

  (let [tbl (->> ::mo/LoginAccount
                 (hc/find-model mo/auth-meta-cache) hc/find-table)]
    (if-not
      (hc/table-exist? pool tbl)
      (mo/apply-ddl pool))
    (if (hc/table-exist? pool tbl)
      (c/info "czlab.blutbad.shiro.model* - ok.")
      (hc/dberr! (u/rstr (b/get-rc-base) "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-sqlr

  ([ctr]
   (get-sqlr ctr false))

  ([ctr tx?]
   {:pre [(some? ctr)]}
   (let [db (-> (b/dbpool?? ctr)
                (ht/dbio<+> mo/auth-meta-cache))]
     (if-not tx?
       (ht/simple db)
       (ht/composite db)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn create-auth-role

  "Create a new auth-role in db."
  [sql role desc]
  {:pre [(some? sql)]}

  (let [m (hc/find-model (:schema sql) ::mo/AuthRole)]
    (hc/add-obj sql
                (-> (hc/dbpojo<> m)
                    (hc/db-set-flds* :name role :desc desc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-auth-role

  "Delete this role."
  [sql role]
  {:pre [(some? sql)]}

  (let [m (hc/find-model (:schema sql) ::mo/AuthRole)]
    (hc/exec-sql sql
                 (format "delete from %s where %s =?"
                         (->> (hc/find-table m)
                              (hc/fmt-id sql))
                         (->> (hc/find-field m :name)
                              hc/find-col
                              (hc/fmt-id sql))) [(c/strim role)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn list-auth-roles

  "List all the roles in db."
  [sql]
  {:pre [(some? sql)]}

  (hc/find-all sql ::mo/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn create-login-account

  "Create a new account
  props : extra properties, such as email address.
  roleObjs : a list of roles to be assigned to the account."

  ([sql user pwdObj props]
   (create-login-account sql user pwdObj props nil))

  ([sql user pwdObj]
   (create-login-account sql user pwdObj nil nil))

  ([sql user pwdObj props roleObjs]
   {:pre [(some? sql)(c/hgl? user)]}
   (let [m (hc/find-model (:schema sql) ::mo/LoginAccount)
         r (hc/find-model (:schema sql) ::mo/AccountRoles)
         ps (some-> pwdObj co/hashed)]
     (c/do-with
       [acc
        (->> (hc/db-set-flds (hc/dbpojo<> m)
                             (merge props
                                    {:passwd ps
                                     :acctid (c/strim user)}))
             (hc/add-obj sql))]
       ;;currently adding roles to the account is not bound to the
       ;;previous insert. That is, if we fail to set a role, it's
       ;;assumed ok for the account to remain inserted
       (doseq [ro roleObjs]
         (hr/set-m2m (hc/gmxm r) sql acc ro))
       (c/debug "created new account %s%s%s%s"
                "into db: " acc "\nwith meta\n" (meta acc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn find-login-account-via-email

  "Look for account with this email address."
  [sql email]
  {:pre [(some? sql)]}

  (hc/find-one sql ::mo/LoginAccount {:email (c/strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn find-login-account

  "Look for account with this user id."
  [sql user]
  {:pre [(some? sql)]}

  (hc/find-one sql ::mo/LoginAccount {:acctid (c/strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-login-account

  "Get the user account."
  [sql user pwd]

  (if-some [acct (find-login-account sql user)]
    (if (co/is-hash-valid? (co/pwd<> pwd)
                           (:passwd acct))
      acct
      (c/trap! GeneralSecurityException
               (u/rstr (b/get-rc-base) "auth.bad.pwd")))
    (c/trap! GeneralSecurityException (str "Unknown User: " user))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn has-login-account?

  "If this user account exists?"
  [sql user] (some? (find-login-account sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn change-login-account

  "Change the account password."
  [sql userObj pwdObj]
  {:pre [(some? sql)]}

  (let [m {:passwd (some-> pwdObj
                           co/hashed)}]
    (->> (hc/db-set-flds*
           (hc/mock-pojo<> userObj) m)
         (hc/mod-obj sql))
    (hc/db-set-flds* userObj m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn update-login-account

  "Update account details
   details: a set of properties such as email address."
  [sql userObj details]
  {:pre [(some? sql)(or (nil? details)
                        (map? details))]}

  (if (empty? details)
    userObj
    (do (->> (hc/db-set-flds*
               (hc/mock-pojo<> userObj) details)
             (hc/mod-obj sql))
        (hc/db-set-flds* userObj details))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-login-account-role

  "Remove a role from this user."
  ^long
  [sql user role]
  {:pre [(some? sql)]}

  (let [r (hc/find-model (:schema sql)
                         ::mo/AccountRoles)]
    (hr/clr-m2m (hc/gmxm r) sql user role)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn add-login-account-role

  "Add a role to this user."
  [sql user role]
  {:pre [(some? sql)]}

  (let [r (hc/find-model (:schema sql)
                         ::mo/AccountRoles)]
    (hr/set-m2m (hc/gmxm r) sql user role)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-login-account

  "Delete this account."
  ^long
  [sql acctObj] {:pre [(some? sql)]} (hc/del-obj sql acctObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-user

  "Delete the account with this user id."
  ^long
  [sql user]
  {:pre [(some? sql)]}

  (let [m (hc/find-model (:schema sql)
                         ::mo/LoginAccount)]
    (hc/exec-sql sql
                 (format "delete from %s where %s =?"
                         (hc/fmt-id sql
                                    (hc/find-table m))
                         (->> (hc/find-field m :acctid)
                              hc/find-col
                              (hc/fmt-id sql))) [(c/strim user)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn list-login-accounts

  "List all user accounts."
  [sql]
  {:pre [(some? sql)]}

  (hc/find-all sql ::mo/LoginAccount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init-shiro

  [conf homeDir]

  (let [{:keys [shiro]} conf
        f (io/file homeDir b/dn-etc "shiro.ini")]
    (if-not (i/file-read? f)
      (i/spit-utf8 f
                   (str "[main]\n"
                        (c/sreduce<>
                          #(let [[k v] %2]
                             (c/sbf+ %1
                                     (c/kw->str k)
                                     "=" (str v) "\n"))
                          (partition 2 shiro)))))
    (try
      (-> (io/as-url f)
          str
          IniSecurityManagerFactory.
          .getInstance
          SecurityUtils/setSecurityManager)
      (finally
        (c/info "created shiro security manager - ok.")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn signup-test-expr<>

  "Test component of a standard sign-up workflow."
  [^String challengeStr evt]

  (let [ck ((:cookies evt) ss/csrf-cookie)
        csrf (some-> ^HttpCookie
                     ck .getValue)
        info (try (get-signup-info evt)
                  (catch DataError _ {:e _}))
        rb (b/get-rc-base)
        pa (-> (c/parent evt)
               c/parent (b/get-plugin :$auth))]
    (c/test-some "auth-plugin" pa)
    (c/debug "csrf = %s%s%s"
             csrf ", and form parts = " info)
    (cond (some? (:e info))
          (:e info)
          (and (c/hgl? challengeStr)
               (c/!eq? challengeStr (:captcha info)))
          (GeneralSecurityException. (u/rstr rb "auth.bad.cha"))
          (c/!eq? csrf (:csrf info))
          (GeneralSecurityException. (u/rstr rb "auth.bad.tkn"))
          (and (c/hgl? (:credential info))
               (c/hgl? (:principal info))
               (c/hgl? (:email info)))
          (if-not (has-account? pa info)
            (add-account pa info)
            (GeneralSecurityException. "DuplicateUser"))
          :else
          (GeneralSecurityException. (u/rstr rb "auth.bad.req")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn login-test-expr<>

  [evt]

  (let [ck ((:cookies evt) ss/csrf-cookie)
        csrf (some-> ^HttpCookie
                     ck .getValue)
        info (try (get-signup-info evt)
                  (catch DataError _ {:e _}))
        rb (b/get-rc-base)
        pa (-> (c/parent evt)
               c/parent (b/get-plugin :$auth))]
    (c/test-some "auth-plugin" pa)
    (c/debug "csrf = %s%s%s"
             csrf
             ", and form parts = " info)
    (cond (some? (:e info))
          (:e info)
          (c/!eq? csrf (:csrf info))
          (GeneralSecurityException. (u/rstr rb "auth.bad.tkn"))
          (and (c/hgl? (:credential info))
               (c/hgl? (:principal info)))
          (do-login pa
                    (:principal info)
                    (:credential info))
          :else
          (GeneralSecurityException. (u/rstr rb "auth.bad.req")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord WebAuthPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (let [me2 (update-in me
                         [:conf]
                         #(b/expand-vars* (c/merge+ % arg)))]
      (-> server b/dbpool?? assert-plugin-ok)
      (init-shiro (:conf me2) (b/home-dir server))
      me2))
  c/Finzable
  (finz [me]
    (c/info "AuthPlugin disposed") me)
  c/Startable
  (start [_]
    (c/start _ nil))
  (start [me _]
    (c/info "AuthPlugin started") me)
  (stop [me]
    (c/info "AuthPlugin stopped") me)
  WebAuthPluginAPI
  (check-action [me acctObj action] me)
  (add-account [me arg]
    (let [{:keys [principal credential]} arg
          pkey (b/pkey-chars server)]
      (create-login-account (get-sqlr server)
                            principal
                            (co/pwd<> credential pkey)
                            (dissoc arg :principal :credential) [])))
  (do-login [me u p]
    (binding [*meta-cache* mo/auth-meta-cache
              *jdbc-pool* (b/dbpool?? server)]
      (let [cur (SecurityUtils/getSubject)
            sss (.getSession cur)]
        (c/debug "Current user session %s, object %s." sss cur)
        (when-not (.isAuthenticated cur)
          (c/try! ;;(.setRememberMe token true)
                  (.login cur
                          (UsernamePasswordToken. ^String u (i/x->str p)))
                  (c/debug "User [%s] logged in successfully." u)))
        (if (.isAuthenticated cur)
          (.getPrincipal cur)))))
  (get-roles [_ acct] [])
  (has-account? [me arg]
    (let [pkey (b/pkey-chars server)]
      (has-login-account? (get-sqlr server)
                          (:principal arg))))
  (get-account [me arg]
    (let [{:keys [principal email]} arg
          pkey (b/pkey-chars server)
          sql (get-sqlr server)]
      (cond (c/hgl? principal)
            (find-login-account sql principal)
            (c/hgl? email)
            (find-login-account-via-email sql email)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def WebAuthSpec
  {:info {:name "Web Auth"
          :version "1.0.0"}
   :conf {:$pluggable ::web-ath<>
          :shiro [:kkkCredentialsMatcher "czlab.blutbad.shiro.core.PwdMatcher"
                  :kkk "czlab.blutbad.shiro.realm.JdbcRealm"
                  :kkk.credentialsMatcher "$kkkCredentialsMatcher"]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn web-auth<>

  ([_ id]
   (web-auth<> _ id WebAuthSpec))

  ([co id {:keys [info conf]}]
   (WebAuthPlugin. co id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftype PwdMatcher []
  CredentialsMatcher
  (doCredentialsMatch [_ token info]
    (let [^AuthenticationToken tkn token
          ^AuthenticationInfo inf info
          pwd (.getCredentials tkn)
          uid (.getPrincipal tkn)
          pc (.getCredentials inf)
          tstPwd (co/pwd<> pwd)
          acc (-> (.getPrincipals inf)
                  .getPrimaryPrincipal)]
      (and (c/eq? uid (:acctid acc))
           (co/is-hash-valid? tstPwd pc)))))

(ns-unmap *ns* '->PwdMatcher)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- do-main

  [& args]

  (let [homeDir (io/file (first args))
        cmd (nth args 1)
        db (nth args 2)
        pod (b/slurp-conf homeDir b/cfg-pod-cf true)
        pkey (get-in pod [:info :digest])
        cfg (get-in pod [:rdbms (keyword db)])]
    (when cfg
      (let [pwd (co/pw-text (co/pwd<> (:passwd cfg) pkey))
            j (hc/dbspec<> (assoc cfg :passwd pwd))
            t (hc/match-url?? (:url cfg))]
        (cond (.equals "init-db" cmd)
              (mo/apply-ddl j)
              (.equals "gen-sql" cmd)
              (if (> (count args) 3)
                (mo/export-auth-pluglet-ddl t
                                            (io/file (nth args 3)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; home gen-sql alias outfile
;; home init-db alias
(defn- main

  "Main Entry" [& args]
  ;; for security, don't just eval stuff
  ;;(alter-var-root #'*read-eval* (constantly false))
  (if-not (< (count args) 3) (apply do-main args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

