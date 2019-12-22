;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.web.auth

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
            [czlab.hoard.drivers :as hd]
            [czlab.blutbad.core :as b]
            [czlab.blutbad.plugs.http :as hp])

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
           [java.sql Connection]
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
(def ^:dynamic *auth-db* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol WebAuthPluginAPI
  (do-login [_ user pwd] "")
  (add-account [_ options] "")
  (has-account? [_ options] "")
  (get-roles [_ acctObj] "")
  (get-account [_ options] "")
  (check-action [_ acctObj action] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(hc/defschema
  auth-meta-cache
  (hc/dbmodel<> ::StdAddress
                (hc/dbfields {:addr1 {:size 255 :null false}
                              :addr2 {}
                              :state {:null false}
                              :city {:null false}
                              :zip {:null false}
                              :country {:null false}})
                (hc/dbindexes {:i1 #{:city :state :country}
                               :i2 #{:zip :country}
                               :state #{:state}
                               :zip #{:zip}}))
  (hc/dbmodel<> ::AuthRole
                (hc/dbfields {:name {:column "ROLE_NAME" :null false}
                              :desc {:column "DESCRIPTION" :null false}})
                (hc/dbuniques {:u1 #{:name}}))
  (hc/dbmodel<> ::LoginAccount
                (hc/dbfields {:acctid {:null false}
                              :email {:size 128}
                              ;;:salt { :size 128}
                              :passwd {:null false :domain :Password}})
                (hc/dbo2o :addr :cascade true :other ::StdAddress)
                (hc/dbuniques {:u2 #{:acctid}}))
  (hc/dbjoined<> ::AccountRoles ::LoginAccount ::AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn gen-auth-plugin-ddl

  "Generate db ddl for the auth-plugin."
  ^String
  [spec]
  {:pre [(keyword? spec)]}

  (if (hc/match-spec?? spec)
    (hd/get-ddl auth-meta-cache spec)
    (hc/dberr! (u/rstr (b/get-rc-base) "db.unknown" (name spec)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn apply-ddl

  [arg] (cond
          (satisfies? hc/JdbcPool arg)
          (apply-ddl (:jdbc arg))

          (c/is? czlab.hoard.core.JdbcSpec arg)
          (when-some [t (hc/match-url?? (:url arg))]
            (c/wo* [^Connection c (hc/conn<> arg)]
              (hc/upload-ddl c (gen-auth-plugin-ddl t))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn export-auth-pluglet-ddl

  "Output the auth-plugin ddl to file."
  [spec file]

  (i/spit-utf8 file (gen-auth-plugin-ddl spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn new-session<>

  ([evt]
   (new-session<> evt nil))

  ([evt attrs]
   (let [plug (c/parent evt)]
     (c/do-with
       [s (ss/wsession<> (-> plug
                             c/parent
                             b/pkey-bytes)
                         (get-in plug [:conf :session]))]
       (doseq [[k v] attrs] (ss/set-session-attr s k v))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn csrf-token<>

  "Create or delete a csrf cookie,
  if maxAge=-1, browser doesnt sent it back!"
  {:tag HttpCookie}

  ([cfg]
   (csrf-token<> cfg nil))

  ([{:keys [domain domain-path]} token]
   (c/do-with
     [c (HttpCookie. ss/csrf-cookie
                     (if (c/hgl? token) token "*"))]
     (if (c/hgl? domain)
       (.setDomain c domain))
     (if (c/hgl? domain-path)
       (.setPath c domain-path))
     (.setHttpOnly c true)
     (.setMaxAge c (if (c/hgl? token) 3600 0)))))

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
(comment
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
        (assoc info fld s))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- assert-plugin-ok

  "If the plugin has been initialized,
  by looking into the db."
  [pool]
  {:pre [(some? pool)]}

  (let [tbl (->> ::LoginAccount
                 (hc/find-model auth-meta-cache) hc/find-table)]
    (if-not
      (hc/table-exist? pool tbl)
      (apply-ddl pool))
    (if (hc/table-exist? pool tbl)
      (c/info "czlab.blutbad.web.auth - db = ok.")
      (hc/dberr! (u/rstr (b/get-rc-base) "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn create-auth-role

  "Create a new auth-role in db."
  [sql role desc]
  {:pre [(some? sql)]}

  (let [m (hc/find-model (:schema sql) ::AuthRole)]
    (hc/add-obj sql
                (-> (hc/dbpojo<> m)
                    (hc/db-set-flds* :name role :desc desc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-auth-role

  "Delete this role."
  [sql role]
  {:pre [(some? sql)]}

  (let [m (hc/find-model (:schema sql) ::AuthRole)]
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

  (hc/find-all sql ::AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn list-roles

  "List all roles assigned to this account."
  [acct sql]

  (hr/get-m2m (hc/gmxm (hc/find-model (:schema sql) ::AccountRoles)) sql acct))

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
   (let [m (hc/find-model (:schema sql) ::LoginAccount)
         r (hc/find-model (:schema sql) ::AccountRoles)
         ps (some-> pwdObj co/hashed)]
     (c/do-with
       [acc
        (->> (hc/db-set-flds (hc/dbpojo<> m)
                             (assoc props
                                    :passwd ps
                                    :acctid (c/strim user)))
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

  (hc/find-one sql ::LoginAccount {:email (c/strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn find-login-account

  "Look for account with this user id."
  [sql user]
  {:pre [(some? sql)]}

  (hc/find-one sql ::LoginAccount {:acctid (c/strim user) }))

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
                         ::AccountRoles)]
    (hr/clr-m2m (hc/gmxm r) sql user role)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn add-login-account-role

  "Add a role to this user."
  [sql user role]
  {:pre [(some? sql)]}

  (let [r (hc/find-model (:schema sql)
                         ::AccountRoles)]
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
                         ::LoginAccount)]
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

  (hc/find-all sql ::LoginAccount))

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
(defrecord WebAuthPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (let [me2 (update-in me
                         [:conf]
                         #(-> (c/merge+ % arg)
                              b/expand-vars* b/prevar-cfg))
          po (b/dbpool?? server)
          db (ht/dbio<+> po auth-meta-cache)]
      (assert-plugin-ok po)
      (init-shiro (:conf me2) (b/home-dir server))
      (assoc me2 :db db)))
  c/Finzable
  (finz [me]
    (some-> (:db me) c/finz)
    (try (assoc me :db nil)
         (finally (c/info "AuthPlugin disposed"))))
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
      (create-login-account (ht/simple (:db me))
                            principal
                            (co/pwd<> credential pkey)
                            (dissoc arg :principal :credential) [])))
  (do-login [me u p]
    (binding [*auth-db* (:db me)]
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
      (has-login-account? (ht/simple (:db me))
                          (:principal arg))))
  (get-account [me arg]
    (let [{:keys [principal email]} arg
          pkey (b/pkey-chars server)
          sql (ht/simple (:db me))]
      (cond (c/hgl? principal)
            (find-login-account sql principal)
            (c/hgl? email)
            (find-login-account-via-email sql email)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- signup??

  "Test component of a standard sign-up workflow."
  [^String challengeStr evt]

  (let [ck ((:cookies evt) ss/csrf-cookie)
        csrf (some-> ^HttpCookie
                     ck .getValue)
        info (try (get-auth-info?? evt)
                  (catch DataError _ {:e _}))
        rb (b/get-rc-base)
        pa (-> evt c/parent c/parent
               (b/find-plugin WebAuthPlugin))]
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
(defn- login??

  [evt]

  (let [ck ((:cookies evt) ss/csrf-cookie)
        csrf (some-> ^HttpCookie
                     ck .getValue)
        info (try (get-auth-info?? evt)
                  (catch DataError _ {:e _}))
        rb (b/get-rc-base)
        pa (-> evt c/parent c/parent
               (b/find-plugin WebAuthPlugin))]
    (c/test-some "auth-plugin" pa)
    (c/debug "csrf = %s%s%s"
             csrf ", and form parts = " info)
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
(def WebAuthSpec
  {:info {:name "Web Auth"
          :version "1.0.0"}
   :conf {:$pluggable ::web-ath<>
          :data-source :default
          :shiro [:kkkCredentialsMatcher "czlab.blutbad.web.auth.PwdMatcher"
                  :kkk "czlab.blutbad.web.realm.JdbcRealm"
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
              (apply-ddl j)
              (.equals "gen-sql" cmd)
              (if (> (count args) 3)
                (export-auth-pluglet-ddl t
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
(defn login

  [evt]

  (letfn
    [(success [evt acct]
       (let [res (cc/http-result evt)
             plug (c/parent evt)
             svr (c/parent plug)
             pk (b/pkey-bytes svr)
             cfg (get-in plug
                         [:conf :session])
             ck (csrf-token<> cfg)
             mvs (-> (ss/wsession<> pk cfg)
                     (ss/set-principal (:acctid acct)))]
         (-> (cc/res-cookie-add res ck)
             (cc/reply-result mvs))))
     (fail [evt]
       (-> (cc/http-result evt 403) cc/reply-result))]
    (c/if-throw
      [rc (login?? evt)] (fail evt) (success evt rc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn do-signup

  [evt]

  (letfn
    [(success [evt acct]
       (cc/reply-result (cc/http-result evt)))
     (fail [evt ^Throwable err]
       (let [rcb (-> evt
                     c/parent
                     c/parent c/id b/get-rc-bundle)
             e (if (c/eq? "DuplicateUser"
                          (.getMessage err))
                 (u/rstr rcb "acct.dup.user"))
             res (cc/http-result evt (if (c/hgl? e) 409 400))]
         (cc/reply-result
           (if (c/nichts? e)
             res
             (->> (if-not (cc/is-ajax? evt)
                    e
                    (i/fmt->json {:error {:msg e}}))
                  (cc/res-body-set res))))))]
    (c/if-throw
      [rc (signup?? "32" evt)] (fail evt rc) (success evt rc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


