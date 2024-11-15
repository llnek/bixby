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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns czlab.bixby.web.realm

  (:gen-class
   :extends org.apache.shiro.realm.AuthorizingRealm
   :name czlab.bixby.web.realm.JdbcRealm
   :init my-init
   :constructors {[] []}
   :exposes-methods { }
   :state my-state)

  (:require [czlab.bixby.web.auth :as w]
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

