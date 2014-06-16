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

  cmzlabsclj.tardis.auth.shiro

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.crypto.codec :only [Pwdify] ])
  (:import (org.apache.shiro.authz AuthorizationException AuthorizationInfo))
  (:import (org.apache.shiro.authc.credential CredentialsMatcher))
  (:import (org.apache.shiro.realm AuthorizingRealm))
  (:import (org.apache.shiro.authc AuthenticationException AuthenticationToken
                                   AuthenticationInfo SimpleAccount))
  (:import (com.zotohlab.frwk.dbio DBAPI)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype PwdMatcher [] CredentialsMatcher

  (doCredentialsMatch [_ token info]
    (let [ pwd (.getCredentials ^AuthenticationToken token)
           uid (.getPrincipal ^AuthenticationToken token)
           pc (.getCredentials ^AuthenticationInfo info)
           ^cmzlabsclj.nucleus.crypto.codec.Password
           tstPwd (Pwdify pwd "")
           acc (-> (.getPrincipals ^AuthenticationInfo info)
                   (.getPrimaryPrincipal)) ]
      (and (= (:acctid acc) uid)
           (.validateHash tstPwd pc)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private shiro-eof nil)

