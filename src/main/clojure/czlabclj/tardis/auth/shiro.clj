;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.auth.shiro

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.crypto.codec :only [Pwdify]])

  (:import  [org.apache.shiro.authz AuthorizationException AuthorizationInfo]
            [org.apache.shiro.authc.credential CredentialsMatcher]
            [org.apache.shiro.realm AuthorizingRealm]
            [org.apache.shiro.authc AuthenticationException
             AuthenticationToken AuthenticationInfo SimpleAccount]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [com.zotohlab.frwk.dbio DBAPI]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype PwdMatcher [] CredentialsMatcher

  (doCredentialsMatch [_ token info]
    (let [^AuthenticationToken tkn token
          ^AuthenticationInfo inf info
          pwd (.getCredentials tkn)
          uid (.getPrincipal tkn)
          pc (.getCredentials inf)
          tstPwd (Pwdify (if (instance? String pwd)
                           pwd
                           (String. ^chars pwd))
                         "")
          acc (-> (.getPrincipals inf)
                  (.getPrimaryPrincipal))]
      (and (= (:acctid acc) uid)
           (.validateHash tstPwd pc)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ns-unmap *ns* '->PwdMatcher)
(def ^:private shiro-eof nil)

