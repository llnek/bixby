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

  czlabclj.xlib.crypto.stores

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.crypto.core
         :only
         [NewAlias CertAliases PKeyAliases
          GetPkcsStore GetJksStore]]
        [czlabclj.xlib.util.core :only [ThrowBadArg]]
        [czlabclj.xlib.util.str :only [nsb hgl?]])

  (:import  [java.security.cert CertificateFactory X509Certificate Certificate]
            [com.zotohlab.frwk.crypto PasswordAPI CryptoStoreAPI CryptoUtils]
            [java.io File FileInputStream IOException InputStream]
            [javax.net.ssl KeyManagerFactory TrustManagerFactory]
            [java.security KeyStore PrivateKey
             KeyStore$TrustedCertificateEntry
             KeyStore$ProtectionParameter
             KeyStore$PasswordProtection
             KeyStore$PrivateKeyEntry]
            [javax.security.auth.x500 X500Principal]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onNewKey "Insert private key & certs into this keystore."

  [^KeyStore keystore
   ^String nm
   ^KeyStore$PrivateKeyEntry pkey
   ^chars pwd ]

  (let [cc (.getCertificateChain pkey) ]
    (doseq [^Certificate c (seq cc) ]
      (.setCertificateEntry keystore (NewAlias) c))
    (.setEntry keystore nm pkey (KeyStore$PasswordProtection. pwd))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCAs ""

  [^KeyStore keystore tca root]

  (loop [en (.aliases keystore)
         rc (transient []) ]
    (if (not (.hasMoreElements en))
      (persistent! rc)
      (if-let [ce (CryptoUtils/getCert keystore
                                       (nsb (.nextElement en))) ]
        (let [^X509Certificate cert (.getTrustedCertificate ce)
              issuer (.getIssuerX500Principal cert)
              subj (.getSubjectX500Principal cert)
              matched (and (not (nil? issuer)) (= issuer subj)) ]
          (if (or (and root (not matched)) (and tca matched))
            (recur en rc)
            (recur en (conj! rc cert))))
        (recur en rc)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkStore ""

  ^KeyStore
  [^KeyStore keystore]

  (condp = (.getType keystore)
    "PKCS12" (GetPkcsStore)
    "JKS" (GetJksStore)
    (ThrowBadArg "wrong keystore type.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeCryptoStore "Create a crypto store."

  ^CryptoStoreAPI
  [^KeyStore keystore ^PasswordAPI passwdObj]

  (reify CryptoStoreAPI

    (addKeyEntity [this bits pwdObj]
      ;; we load the p12 content into an empty keystore, then extract the entry
      ;; and insert it into the current one.
      (let [ch (.toCharArray ^PasswordAPI pwdObj)
            tmp (doto (mkStore keystore) (.load bits ch))
            pkey (CryptoUtils/getPKey tmp ^String
                                      (-> (.aliases tmp)
                                          (.nextElement)) ch) ]
        (onNewKey this (NewAlias) pkey ch)))

    (addCertEntity [_ bits]
      (let [fac (CertificateFactory/getInstance "X.509")
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
      (let [ca (.toCharArray ^PasswordAPI pwdObj) ]
        (CryptoUtils/getPKey keystore ^String nm ca)))

    (certEntity [_ nm]
      (CryptoUtils/getCert keystore ^String nm))

    (removeEntity [_ nm]
      (when (.containsAlias keystore ^String nm)
        (.deleteEntry keystore ^String nm)))

    (intermediateCAs [_] (getCAs keystore true false))
    (rootCAs [_] (getCAs keystore false true))

    (trustedCerts [me]
      (map #(let [^KeyStore$TrustedCertificateEntry
                  tc (.certEntity me (nsb %1)) ]
              (.getTrustedCertificate tc))
           (.certAliases me)))

    (addPKCS7Entity [_ bits]
      (let [fac (CertificateFactory/getInstance "X.509")
            certs (.generateCertificates fac bits) ]
        (doseq [^X509Certificate c (seq certs) ]
          (.setCertificateEntry keystore (NewAlias) c))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private stores-eof nil)


