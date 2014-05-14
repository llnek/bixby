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

  cmzlabsclj.nucleus.crypto.stores

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.math.numeric-tower :as math])
  (:use [cmzlabsclj.nucleus.crypto.core
         :only [NewAlias CertAliases PKeyAliases GetPkcsStore GetJksStore] ])
  (:use [cmzlabsclj.nucleus.util.core :only [ThrowBadArg] ])
  (:use [cmzlabsclj.nucleus.util.str :only [hgl?] ])

  (:import (java.security.cert CertificateFactory X509Certificate Certificate))
  (:import (com.zotohlabs.frwk.crypto CryptoUtils))
  (:import (java.io File FileInputStream IOException InputStream))
  (:import (java.security KeyStore PrivateKey
                          KeyStore$TrustedCertificateEntry
                          KeyStore$ProtectionParameter
                          KeyStore$PasswordProtection
                          KeyStore$PrivateKeyEntry))
  (:import (javax.net.ssl KeyManagerFactory TrustManagerFactory))
  (:import (javax.security.auth.x500 X500Principal)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onNewKey ""

  [ ^KeyStore keystore
    ^String nm
    ^KeyStore$PrivateKeyEntry pkey
    ^chars pwd ]

  (let [ cc (.getCertificateChain pkey) ]
    (doseq [ ^Certificate c (seq cc) ]
      (.setCertificateEntry keystore (NewAlias) c))
    (.setEntry keystore nm pkey (KeyStore$PasswordProtection. pwd))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCAs ""

  [^KeyStore keystore tca root]

  (loop [ en (.aliases keystore)
          rc (transient []) ]
    (if (not (.hasMoreElements en))
      (persistent! rc)
      (if-let [ ce (CryptoUtils/getCert keystore
                                        ^String (.nextElement en)) ]
        (let [ cert (.getTrustedCertificate ce)
               issuer (.getIssuerX500Principal ^X509Certificate cert)
               subj (.getSubjectX500Principal ^X509Certificate cert)
               matched (and (not (nil? issuer)) (= issuer subj)) ]
          (if (or (and root (not matched)) (and tca matched))
            (recur en rc)
            (recur en (conj! rc cert))))
        (recur en rc)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol CryptoStore

  ""

  (addKeyEntity [_ ^bytes bits ^cmzlabsclj.nucleus.crypto.codec.Password pwdObj] )
  (addCertEntity [_ ^bytes bits] )
  (trustManagerFactory [_] )
  (keyManagerFactory [_] )
  (certAliases [_] )
  (keyAliases [_] )
  (keyEntity [_ ^String nm ^cmzlabsclj.nucleus.crypto.codec.Password pwdObj] )
  (certEntity [_ ^String nm] )
  (removeEntity [_ ^String nm] )
  (intermediateCAs [_] )
  (rootCAs [_] )
  (trustedCerts [_] )
  (addPKCS7Entity [_ ^bytes bits] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkStore ""

  ^KeyStore
  [^KeyStore keystore]

  (case (.getType keystore)
    "PKCS12" (GetPkcsStore)
    "JKS" (GetJksStore)
    (ThrowBadArg "wrong keystore type.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeCryptoStore ""

  ^cmzlabsclj.nucleus.crypto.stores.CryptoStore
  [^KeyStore keystore ^cmzlabsclj.nucleus.crypto.codec.Password passwdObj]

  (reify CryptoStore

    (addKeyEntity [this bits pwdObj]
      ;; we load the p12 content into an empty keystore, then extract the entry
      ;; and insert it into the current one.
      (let [ ch (.toCharArray ^cmzlabsclj.nucleus.crypto.codec.Password pwdObj)
             tmp (doto (mkStore keystore) (.load bits ch))
             pkey (CryptoUtils/getPKey tmp ^String (-> (.aliases tmp)
                                                       (.nextElement)) ch) ]
        (onNewKey this (NewAlias) pkey ch)))

    (addCertEntity [_ bits]
      (let [ fac (CertificateFactory/getInstance "X.509")
             ^X509Certificate c (.generateCertificate fac bits) ]
        (.setCertificateEntry keystore (NewAlias) c)))

    (trustManagerFactory [_]
      (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
            (.init keystore)))

    (keyManagerFactory [_]
      (doto (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
            (.init keystore  (.toCharArray passwdObj))))

    (certAliases [_] (CertAliases keystore))
    (keyAliases [_] (PKeyAliases keystore))

    (keyEntity [_ nm pwdObj]
      (let [ ca (.toCharArray ^cmzlabsclj.nucleus.crypto.codec.Password pwdObj) ]
        (CryptoUtils/getPKey keystore ^String nm ca)))

    (certEntity [_ nm]
      (CryptoUtils/getCert keystore ^String nm))

    (removeEntity [_ nm]
      (when (.containsAlias keystore ^String nm)
            (.deleteEntry keystore ^String nm)))

    (intermediateCAs [_] (getCAs keystore true false))
    (rootCAs [_] (getCAs keystore false true))

    (trustedCerts [me]
      (map (fn [^String nm]
             (let [ ^KeyStore$TrustedCertificateEntry tc (.certEntity me nm) ]
                (.getTrustedCertificate tc)))
           (.certAliases me)))

    (addPKCS7Entity [_ bits]
      (let [ fac (CertificateFactory/getInstance "X.509")
             certs (.generateCertificates fac bits) ]
        (doseq [ ^X509Certificate c (seq certs) ]
          (.setCertificateEntry keystore (NewAlias) c))))

  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private stores-eof nil)


