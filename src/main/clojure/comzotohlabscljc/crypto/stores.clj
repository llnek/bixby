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

  comzotohlabscljc.crypto.stores

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.math.numeric-tower :as math])

  (:import (java.security.cert CertificateFactory X509Certificate Certificate))
  (:import (java.io File FileInputStream IOException InputStream))
  (:import (java.security KeyStore PrivateKey
                          KeyStore$TrustedCertificateEntry
                          KeyStore$ProtectionParameter
                          KeyStore$PasswordProtection
                          KeyStore$PrivateKeyEntry))
  (:import (javax.net.ssl KeyManagerFactory TrustManagerFactory))
  (:import (javax.security.auth.x500 X500Principal))
  (:use [comzotohlabscljc.crypto.core :only [NewAlias CertAliases PKeyAliases GetPkcsStore GetJksStore] ])
  (:use [comzotohlabscljc.util.str :only [hgl?] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onNewKey ""

  [ ^KeyStore keystore ^String nm
    ^KeyStore$PrivateKeyEntry pkey
    ^KeyStore$ProtectionParameter pm]

  (let [ cc (.getCertificateChain pkey) ]
    (doseq [ ^Certificate c (seq cc) ]
      (.setCertificateEntry keystore (NewAlias) c))
    (.setEntry keystore nm pkey pm)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCAs ""

  [^KeyStore keystore tca root]

  (let [ en (.aliases keystore) ]
    (loop [ rc (transient []) ]
      (if (not (.hasMoreElements en))
        (persistent! rc)
        (let [ ^String a (.nextElement en) ]
          (if (.isCertificateEntry keystore a)
            (let [ ^KeyStore$TrustedCertificateEntry ce (.getEntry keystore a nil)
                   ^X509Certificate cert (.getTrustedCertificate ce)
                   issuer (.getIssuerX500Principal cert)
                   subj (.getSubjectX500Principal cert)
                   matched (and (not (nil? issuer)) (= issuer subj)) ]
              (if (or (and root (not matched)) (and tca matched))
                (recur rc)
                (recur (conj! rc cert))))
            (recur rc)))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol CryptoStore

  ""

  (addKeyEntity [_ ^bytes bits ^comzotohlabscljc.crypto.codec.Password pwdObj] )
  (addCertEntity [_ ^bytes bits] )
  (trustManagerFactory [_] )
  (keyManagerFactory [_] )
  (certAliases [_] )
  (keyAliases [_] )
  (keyEntity [_ ^String nm ^comzotohlabscljc.crypto.codec.Password pwdObj] )
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
    (throw (IllegalArgumentException. "wrong keystore type."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeCryptoStore ""

  ^comzotohlabscljc.crypto.stores.CryptoStore
  [^KeyStore keystore ^comzotohlabscljc.crypto.codec.Password passwdObj]

  (reify CryptoStore

    (addKeyEntity [this bits pwdObj]
      ;; we load the p12 content into an empty keystore, then extract the entry
      ;; and insert it into the current one.
      (let [ ch (.toCharArray ^comzotohlabscljc.crypto.codec.Password pwdObj)
             tmp (doto (mkStore keystore) (.load bits ch))
             pp (KeyStore$PasswordProtection. ch)
             ^KeyStore$PrivateKeyEntry pkey (.getEntry tmp (.nextElement (.aliases tmp)) pp) ]
        (onNewKey this (NewAlias) pkey pp)))

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
      (let [ ca (.toCharArray ^comzotohlabscljc.crypto.codec.Password pwdObj) ]
        (.getEntry keystore nm (KeyStore$PasswordProtection. ca))))

    (certEntity [_ nm]
      (if (hgl? nm)
        (.getEntry keystore ^String nm nil) ))

    (removeEntity [_ nm]
      (if (hgl? nm)
        (when (.containsAlias keystore ^String nm) (.deleteEntry keystore ^String nm))))

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


