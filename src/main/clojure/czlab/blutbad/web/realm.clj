;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.web.realm

  (:gen-class
   :extends org.apache.shiro.realm.AuthorizingRealm
   :name czlab.blutbad.web.realm.JdbcRealm
   :init my-init
   :constructors {[] []}
   :exposes-methods { }
   :state my-state)

  (:require [czlab.blutbad.web.auth :as w]
            [czlab.basal.core :as c]
            [czlab.hoard.core :as hc]
            [czlab.hoard.rels :as hr]
            [czlab.hoard.connect :as ht]
            [czlab.twisty.codec :as co :refer [pwd<>]])

  (:import [org.apache.shiro.realm CachingRealm AuthorizingRealm]
           [org.apache.shiro.subject PrincipalCollection]
           [org.apache.shiro.authz
            AuthorizationInfo
            AuthorizationException]
           [org.apache.shiro.authc
            SimpleAccount
            AuthenticationToken
            AuthenticationException]
           [java.util Collection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -my-init [] [ [] (atom nil) ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -doGetAuthenticationInfo

  [^AuthorizingRealm this ^AuthenticationToken token]

  (let [;;pwd (.getCredentials token)
        user (.getPrincipal token)
        sql (ht/simple w/*auth-db*)]
    (when-some [acc (w/find-login-account sql user)]
      (SimpleAccount. acc (:passwd acc) (.getName this)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -doGetAuthorizationInfo

  [^AuthorizingRealm this ^PrincipalCollection principals]

  (let [sql (ht/simple w/*auth-db*)
        acc (.getPrimaryPrincipal principals)]
    (c/do-with
      [rc (SimpleAccount. acc
                          (:passwd acc)
                          (.getName this))]
      (doseq [r (w/list-roles acc sql)] (.addRole rc ^String (:name r))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -init [] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

