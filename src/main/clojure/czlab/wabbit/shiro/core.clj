;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.shiro.core

  ;;(:gen-class)

  (:require [czlab.basal.util :as u]
            [czlab.niou.util :as ct]
            [czlab.wabbit.plugs.http :as hp]
            [czlab.niou.webss  :as ss]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.basal.log :as l]
            [czlab.wabbit.shiro.model :as mo]
            [czlab.twisty.codec :as co]
            [czlab.hoard.connect :as ht]
            [czlab.hoard.rels :as hr]
            [czlab.wabbit.xpis :as xp]
            [czlab.wabbit.core :as b]
            [czlab.niou.upload :as cu]
            [czlab.niou.mime :as mi]
            [czlab.niou.core :as cc]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.basal.meta :as m]
            [czlab.basal.str :as s]
            [czlab.hoard.core :as hc]
            [czlab.basal.proto :as po])

  (:import [org.apache.shiro.authc.credential CredentialsMatcher]
           [org.apache.shiro.config IniSecurityManagerFactory]
           [org.apache.shiro.authc UsernamePasswordToken]
           [java.security GeneralSecurityException]
           [org.apache.commons.fileupload FileItem]
           [czlab.basal DataError XData]
           [org.apache.shiro.realm AuthorizingRealm]
           [org.apache.shiro.subject Subject]
           [java.util Base64 Base64$Decoder]
           [org.apache.shiro SecurityUtils]
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
           [java.util Properties]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:private ^String nonce-param "nonce_token")
(def ^:private ^String csrf-param "csrf_token")
(def ^:private ^String pwd-param "credential")
(def ^:private ^String email-param "email")
(def ^:private ^String user-param "principal")
(def ^:private ^String captcha-param "captcha")

;; hard code the shift position, the encrypt code
;; should match this value.
(def ^:private caesar-shift 13)
(def ^:private props-map
  {email-param [ :email #(mi/normalize-email %) ]
   captcha-param [ :captcha #(s/strim %) ]
   user-param [ :principal #(s/strim %) ]
   pwd-param [ :credential #(s/strim %) ]
   csrf-param [ :csrf #(s/strim %) ]
   nonce-param [ :nonce #(some? %) ]})

(def ^:dynamic *meta-cache* nil)
(def ^:dynamic *jdbc-pool* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol AuthPluglet
  ""
  (check-action [_ acctObj action] "")
  (do-login [_ user pwd] "")
  (add-account [_ options] "")
  (has-account? [_ options] "")
  (get-roles [_ acctObj] "")
  (get-account [_ options] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn new-session<>
  ""
  ([evt]
   (new-session<> evt nil))
  ([evt attrs]
   (let [plug (xp/get-pluglet evt)]
     (c/do-with
       [s (ss/wsession<> (-> plug po/parent xp/pkey-bytes)
                         (:session (xp/get-conf plug)))]
       (doseq [[k v] attrs] (ss/set-session-attr s k v))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn csrf-token<>
  "Create or delete a csrf cookie."
  ;; if maxAge=-1, browser doesnt sent it back!
  ^HttpCookie
  [{:keys [domain-path domain]} token]
  (c/do-with
    [c (HttpCookie. ss/*csrf-cookie*
                    (if (s/hgl? token) token "*"))]
    (if (s/hgl? domain-path) (.setPath c domain-path))
    (if (s/hgl? domain) (.setDomain c domain))
    (.setHttpOnly c true)
    (.setMaxAge c (if (s/hgl? token) 3600 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- crack-form-fields
  "Parse a standard login-like form with userid,password,email"
  [evt]
  (if-some
    [itms (c/cast? czlab.niou.upload.ULFormItems
                   (some-> ^XData (:body evt) .content))]
    (c/preduce<map>
      #(let [fm (cu/get-field-name-lc %2)
             fv (.getString ^FileItem %2)]
         (l/debug "form-field=%s, value=%s." fm fv)
         (if-some [[k v] (get props-map fm)]
           (assoc! %1 k (v fv))
           %1))
      (cu/get-all-fields itms))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- crack-body-content
  "Parse a JSON body."
  [evt]
  (let [xs (some-> ^XData (:body evt) .getBytes)
        json (-> (if xs (i/x->str xs) "{}")
                 (i/read-json #(s/lcase %)))]
    (c/preduce<map>
      #(let [[k [a1 a2]] %2]
         (if-some [fv (get json k)]
           (assoc! %1 a1 (a2 fv)) %1)) props-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- crack-params
  "Parse form fields in the Url."
  [evt]
  (c/preduce<map>
    #(let [[k [a1 a2]] props-map]
       (if (cc/gist-param? evt k)
         (assoc! %1 a1 (a2 (cc/gist-param evt k))) %1)) props-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn maybe-get-auth-info
  "Attempt to parse and get authentication info."
  [evt]
  (c/if-some+ [ct (cc/msg-header evt "content-type")]
  (cond (or (s/embeds? ct "form-data")
            (s/embeds? ct "form-urlencoded"))
        (crack-form-fields evt)
        (s/embeds? ct "/json")
        (crack-body-content evt)
        :else (crack-params evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-decode-field
  [info fld shiftCount]
  (if-not (:nonce info)
    info
    (c/try! (let [^String decr (co/cr-decrypt (co/caesar<>)
                                              shiftCount (get info fld))
                  s (i/x->str (.decode (Base64/getMimeDecoder) decr))]
              (l/debug (str "info = %s." "decr = %s." "val = %s.") info decr s)
              (assoc info fld s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-pod-key
  ^bytes [evt] (-> evt xp/get-pluglet po/parent xp/pkey-bytes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro ^:private get-xxx-info
  [evt]
  `(-> (maybe-get-auth-info ~evt)
       (maybe-decode-field :principal caesar-shift)
       (maybe-decode-field :credential caesar-shift)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-signup-info
  "" [evt] (get-xxx-info evt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-login-info
  "" [evt] (get-xxx-info evt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- assert-plugin-ok
  "If the plugin has been initialized,
   by looking into the db."
  [pool]
  {:pre [(some? pool)]}
  (let [tbl (->> ::mo/LoginAccount
                 (get (:models mo/*auth-meta-cache*)) hc/find-table)]
    (if-not
      (hc/table-exist? pool tbl)
      (mo/apply-ddl pool))
    (if (hc/table-exist? pool tbl)
      (l/info "czlab.wabbit.shiro.model* - ok.")
      (hc/dberr! (u/rstr (b/get-rc-base) "auth.no.table" tbl)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-sqlr
  ([ctr]
   (get-sqlr ctr false))
  ([ctr tx?]
   {:pre [(some? ctr)]}
   (let [db (-> (xp/dft-db-pool ctr)
                (ht/dbio<+> mo/*auth-meta-cache*))]
     (if-not tx?
       (ht/db-simple db)
       (ht/db-composite db)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn create-auth-role
  "Create a new auth-role in db."
  [sql role desc]
  {:pre [(some? sql)]}
  (let [m (get (:models sql) ::mo/AuthRole)]
    (hc/sq-add-obj sql
                   (-> (hc/dbpojo<> m)
                       (hc/db-set-flds* :name role :desc desc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-auth-role
  "Delete this role."
  [sql role]
  {:pre [(some? sql)]}
  (let [m (get (:models sql) ::mo/AuthRole)]
    (hc/sq-exec-sql sql
                    (format "delete from %s where %s =?"
                            (hc/sq-fmt-id sql (hc/find-table m))
                            (hc/sq-fmt-id sql
                                          (hc/find-col (hc/find-field m :name))))
                    [(s/strim role)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn list-auth-roles
  "List all the roles in db."
  [sql]
  {:pre [(some? sql)]}
  (hc/sq-find-all sql ::mo/AuthRole))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn create-login-account
  "Create a new account
   props : extra properties, such as email address.
   roleObjs : a list of roles to be assigned to the account."
  {:tag APersistentMap}
  ([sql user pwdObj props]
   (create-login-account sql user pwdObj props nil))

  ([sql user pwdObj]
   (create-login-account sql user pwdObj nil nil))

  ([sql user pwdObj props roleObjs]
   {:pre [(some? sql)(s/hgl? user)]}
   (let [m (get (:models sql) ::mo/LoginAccount)
         r (get (:models sql) ::mo/AccountRoles)
         ps (some-> pwdObj co/pw-hashed)]
     (c/do-with
       [acc
        (->>
          (hc/db-set-flds (hc/dbpojo<> m)
                          (merge props {:acctid (s/strim user) :passwd ps}))
          (hc/sq-add-obj sql))]
       ;; currently adding roles to the account is not bound to the
       ;; previous insert. That is, if we fail to set a role, it's
       ;; assumed ok for the account to remain inserted
       (doseq [ro roleObjs]
         (hr/db-set-m2m (hc/gmxm r) sql acc ro))
       (l/debug "created new account %s%s%s%s"
                "into db: " acc "\nwith meta\n" (meta acc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn find-login-account-via-email
  "Look for account with this email address."
  [sql email]
  {:pre [(some? sql)]}
  (hc/sq-find-one sql
                  ::mo/LoginAccount
                  {:email (s/strim email) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn find-login-account
  "Look for account with this user id."
  [sql user]
  {:pre [(some? sql)]}
  (hc/sq-find-one sql
                  ::mo/LoginAccount
                  {:acctid (s/strim user) }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-login-account
  "Get the user account."
  [sql user pwd]
  (if-some [acct (find-login-account sql user)]
    (if (co/pw-hash-valid? (co/pwd<> pwd)
                           (:passwd acct))
      acct
      (c/trap! GeneralSecurityException
               (u/rstr (b/get-rc-base) "auth.bad.pwd")))
    (c/trap! GeneralSecurityException (str "UnknownUser: " user))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn has-login-account?
  "If this user account exists?"
  [sql user] (some? (find-login-account sql user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn change-login-account
  "Change the account password."
  [sql userObj pwdObj]
  {:pre [(some? sql)
         (map? userObj)(some? pwdObj)]}
  (let [m {:passwd (some-> pwdObj co/pw-hashed)}]
    (->> (hc/db-set-flds*
           (hc/mock-pojo<> userObj) m)
         (hc/sq-mod-obj sql))
    (hc/db-set-flds* userObj m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn update-login-account
  "Update account details
   details: a set of properties such as email address."
  [sql userObj details]
  {:pre [(some? sql)(map? userObj)]}
  (if-not (empty? details)
    (do (->> (hc/db-set-flds*
               (hc/mock-pojo<> userObj) details)
             (hc/sq-mod-obj sql))
        (hc/db-set-flds* userObj details))
    userObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-login-account-role
  "Remove a role from this user."
  ^long
  [sql user role]
  {:pre [(some? sql)]}
  (let [r (get (:models sql) ::mo/AccountRoles)]
    (hr/db-clr-m2m (hc/gmxm r) sql user role)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn add-login-account-role
  "Add a role to this user."
  [sql user role]
  {:pre [(some? sql)]}
  (let [r (get (:models sql) ::mo/AccountRoles)]
  (hr/db-set-m2m (hc/gmxm r) sql user role)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-login-account
  "Delete this account."
  ^long
  [sql acctObj] {:pre [(some? sql)]} (hc/sq-del-obj sql acctObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-user
  "Delete the account with this user id."
  ^long
  [sql user]
  {:pre [(some? sql)]}
  (let [m (get (:models sql)
               ::mo/LoginAccount)]
    (hc/sq-exec-sql sql
                    (format "delete from %s where %s =?"
                            (hc/sq-fmt-id sql (hc/find-table m))
                            (hc/sq-fmt-id sql
                                          (hc/find-col
                                            (hc/find-field m :acctid))))
                    [(s/strim user)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn list-login-accounts
  "List all user accounts."
  [sql]
  {:pre [(some? sql)]}
  (hc/sq-find-all sql ::mo/LoginAccount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init-shiro
  [^File homeDir ^String podKey]
  (let [f (io/file homeDir "etc/shiro.ini")
        f (if-not (i/file-read? f)
            (doto (io/file i/*tempfile-repo* (u/jid<>))
              (spit (i/res->str "czlab/wabbit/auth/shiro.ini"))) f)]
    (-> (io/as-url f)
        str
        IniSecurityManagerFactory.
        .getInstance
        SecurityUtils/setSecurityManager)
    (l/info "created shiro security manager.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn signup-test-expr<>
  "Test component of a standard sign-up workflow"
  [^String challengeStr evt]
  (let [^HttpCookie
        ck (get (:cookies evt) ss/*csrf-cookie*)
        csrf (some-> ck .getValue)
        info (try (get-signup-info evt)
                  (catch DataError _ {:e _}))
        rb (b/get-rc-base)
        pa (-> (xp/get-pluglet evt)
               po/parent
               (xp/get-child :$auth))]
    (l/debug "csrf = %s%s%s"
             csrf ", and form parts = " info)
    (c/test-some "auth-pluglet" pa)
    (cond (some? (:e info))
          (:e info)
          (and (s/hgl? challengeStr)
               (not= challengeStr (:captcha info)))
          (GeneralSecurityException. (u/rstr rb "auth.bad.cha"))
          (not= csrf (:csrf info))
          (GeneralSecurityException. (u/rstr rb "auth.bad.tkn"))
          (and (s/hgl? (:credential info))
               (s/hgl? (:principal info))
               (s/hgl? (:email info)))
          (if (has-account? pa info)
            (GeneralSecurityException. "DuplicateUser")
            (add-account pa info))
          :else
          (GeneralSecurityException. (u/rstr rb "auth.bad.req")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn login-test-expr<>
  "" [evt]
  (let [^HttpCookie
        ck (get (:cookies evt) ss/*csrf-cookie*)
        csrf (some-> ck .getValue)
        info (try (get-signup-info evt)
                  (catch DataError _ {:e _}))
        rb (b/get-rc-base)
        pa (-> (xp/get-pluglet evt)
               po/parent
               (xp/get-child :$auth))]
    (l/debug "csrf = %s%s%s"
             csrf
             ", and form parts = " info)
    (c/test-some "auth-pluglet" pa)
    (cond (some? (:e info))
          (:e info)
          (not= csrf (:csrf info))
          (GeneralSecurityException. (u/rstr rb "auth.bad.tkn"))
          (and (s/hgl? (:credential info))
               (s/hgl? (:principal info)))
          (do-login pa (:principal info) (:credential info))
          :else
          (GeneralSecurityException. (u/rstr rb "auth.bad.req")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet
  [server _id spec]
  (let [impl (atom {:info (:info spec)
                    :conf (:conf spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :handler]))
      (get-conf [_] (:conf @impl))
      (err-handler [_]
        (or (get-in @impl
                    [:conf :error]) (:error spec)))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (-> server xp/dft-db-pool assert-plugin-ok)
        (swap! impl
               (c/fn_1 (update-in ____1
                                  [:conf]
                                  #(b/prevar-cfg (merge % arg)))))
        (init-shiro (xp/get-home-dir server)
                    (xp/pkey-chars server)))
      po/Finzable
      (finz [_]
        (l/info "AuthPluglet disposed"))
      po/Startable
      (start [_] (po/start _ nil))
      (start [me _]
        (l/info "AuthPluglet started"))
      (stop [_]
        (l/info "AuthPluglet stopped"))
      AuthPluglet
      (check-action [_ acctObj action] )
      (add-account [me arg]
        (let [{:keys [principal credential]} arg
              pkey (xp/pkey-chars server)]
          (create-login-account (get-sqlr server)
                                principal
                                (co/pwd<> credential pkey)
                                (dissoc arg :principal :credential) [])))
      (do-login [me u p]
        (binding [*meta-cache* mo/*auth-meta-cache*
                  *jdbc-pool* (xp/dft-db-pool server)]
          (let [cur (SecurityUtils/getSubject)
                sss (.getSession cur)]
            (l/debug "Current user session %s." sss)
            (l/debug "Current user object %s." cur)
            (when-not (.isAuthenticated cur)
              (c/try! ;;(.setRememberMe token true)
                      (.login cur
                              (UsernamePasswordToken. ^String u (i/x->str p)))
                      (l/debug "User [%s] logged in successfully." u)))
            (if (.isAuthenticated cur)
              (.getPrincipal cur)))))
      (has-account? [me arg]
        (let [pkey (xp/pkey-chars server)]
          (has-login-account? (get-sqlr server)
                              (:principal arg))))
      (get-account [me arg]
        (let [{:keys [principal email]} arg
              pkey (xp/pkey-chars server)
              sql (get-sqlr server)]
          (cond (s/hgl? principal)
                (find-login-account sql principal)
                (s/hgl? email)
                (find-login-account-via-email sql email))))
      (get-roles [_ acct] []))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn web-auth<>
  "" [ctr id] (pluglet :parent ctr :emAlias id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftype PwdMatcher [] CredentialsMatcher
  (doCredentialsMatch [_ token info]
    (let [^AuthenticationToken tkn token
          ^AuthenticationInfo inf info
          pwd (.getCredentials tkn)
          uid (.getPrincipal tkn)
          pc (.getCredentials inf)
          tstPwd (co/pwd<> pwd)
          acc (-> (.getPrincipals inf)
                  .getPrimaryPrincipal)]
      (and (= (:acctid acc) uid)
           (co/pw-hash-valid? tstPwd pc)))))

(ns-unmap *ns* '->PwdMatcher)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- do-main
  "" [& args]
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
        (cond (= "init-db" cmd)
              (mo/apply-ddl j)
              (= "gen-sql" cmd)
              (if (> (count args) 3)
                (mo/export-auth-pluglet-ddl t
                                            (io/file (nth args 3)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; home gen-sql alias outfile
;; home init-db alias
(defn- main "
  Main Entry" [& args]
  ;; for security, don't just eval stuff
  ;;(alter-var-root #'*read-eval* (constantly false))
  (if-not (< (count args) 3) (apply do-main args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


