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

(ns

  testcljc.crypto.cryptostuff

  (:require [cmzlabsclj.nucleus.crypto.stores :as ST])
  (:require [cmzlabsclj.nucleus.crypto.codec :as RT])
  (:require [cmzlabsclj.nucleus.util.core :as CU])
  (:require [cmzlabsclj.nucleus.util.str :as SU])
  (:require [cmzlabsclj.nucleus.util.io :as IO])
  (:require [cmzlabsclj.nucleus.crypto.core :as RU])
  (:use [clojure.test])
  (:import (java.security Policy KeyStore SecureRandom MessageDigest
    KeyStore$PrivateKeyEntry KeyStore$TrustedCertificateEntry))
  (:import (java.util Date GregorianCalendar))
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ROOTPFX (CU/ResBytes "com/zotohlab/frwk/crypto/test.pfx"))
(def ^:private ROOTJKS (CU/ResBytes "com/zotohlab/frwk/crypto/test.jks"))
(def ^:private ENDDT (.getTime (GregorianCalendar. 2050 1 1)))
(def ^:private TESTPWD (RT/Pwdify "secretsecretsecretsecretsecret"))
(def ^:private HELPME (RT/Pwdify "helpme"))
(def ^:private SECRET (RT/Pwdify "secret"))

(def ^:private ROOTCS
  (ST/MakeCryptoStore (RU/InitStore! (RU/GetPkcsStore) ROOTPFX HELPME) HELPME))

(def ^:private ROOTKS
  (ST/MakeCryptoStore (RU/InitStore! (RU/GetJksStore) ROOTJKS HELPME) HELPME))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testcrypto-cryptostuff

(is (not (= "heeloo, how are you?" (RT/CaesarDecrypt (RT/CaesarEncrypt "heeloo, how are you?" 709394) 666))))
(is (= "heeloo, how are you?" (RT/CaesarDecrypt (RT/CaesarEncrypt "heeloo, how are you?" 709394) 709394)))

(is (= "heeloo" (let [ c (RT/JasyptCryptor) ]
                      (.decrypt c (.encrypt c "heeloo")))))

(is (= "heeloo" (let [ c (RT/JasyptCryptor) pkey (SU/nsb SECRET) ]
                      (.decrypt c pkey (.encrypt c pkey "heeloo")))))

(is (= "heeloo" (let [ c (RT/JavaCryptor) ]
                      (.decrypt c (.encrypt c "heeloo")))))

(is (= "heeloo" (let [ c (RT/JavaCryptor) pkey (SU/nsb TESTPWD) ]
                      (.decrypt c pkey (.encrypt c pkey "heeloo")))))

(is (= "heeloo" (let [ c (RT/BouncyCryptor) ]
                      (.decrypt c (.encrypt c "heeloo")))))

(is (= "heeloo" (let [ c (RT/BouncyCryptor) pkey (SU/nsb TESTPWD) ]
                      (.decrypt c pkey (.encrypt c pkey "heeloo")))))

(is (= (.length ^String (.text (RT/CreateStrongPwd 16))) 16))
(is (= (.length (RT/CreateRandomString 64)) 64))

(is (instance? cmzlabsclj.nucleus.crypto.codec.Password (RT/Pwdify "secret-text")))
(is (.startsWith ^String (.encoded ^cmzlabsclj.nucleus.crypto.codec.Password (RT/Pwdify "secret-text")) "CRYPT:"))


(is (= "SHA-512" (.getAlgorithm (RU/MakeMsgDigest RU/SHA_512))))
(is (= "MD5" (.getAlgorithm (RU/MakeMsgDigest RU/MD_5))))

(is (> (RU/NextSerial) 0))

(is (> (.length (RU/NewAlias)) 0))

(is (= "PKCS12" (.getType (RU/GetPkcsStore))))
(is (= "JKS" (.getType (RU/GetJksStore))))

(is (instance? Policy (RU/MakeEasyPolicy)))

(is (> (.length (RU/GenMac (CU/Bytesify "secret") "heeloo world")) 0))
(is (> (.length (RU/GenHash "heeloo world")) 0))

(is (not (nil? (RU/MakeKeypair "RSA" 1024))))

(is (let [ v (RU/MakeCsrReq 1024 "C=AU,ST=NSW,L=Sydney,O=Google,OU=HQ,CN=www.google.com" "PEM") ]
          (and (= (count v) 2) (> (alength ^bytes (first v)) 0) (> (alength ^bytes (nth v 1)) 0))) )

(is (let [ fout (IO/MakeTmpfile "kenl" ".p12")]
      (RU/MakeSSv1PKCS12 "C=AU,ST=NSW,L=Sydney,O=Google" HELPME fout
                          { :start (Date.) :end ENDDT :keylen 1024 })
      (> (.length fout) 0)))

(is (let [ fout (IO/MakeTmpfile "" ".jks") ]
      (RU/MakeSSv1JKS "C=AU,ST=NSW,L=Sydney,O=Google" SECRET fout
                          { :start (Date.) :end ENDDT :keylen 1024 })
            (> (.length fout) 0)))

(is (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS
                           ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS))
                           HELPME)
       fout (IO/MakeTmpfile "" ".p12")
       pk (.getPrivateKey pke)
       cs (.getCertificateChain pke) ]
            (RU/MakeSSv3PKCS12 "C=AU,ST=NSW,L=Sydney,O=Google" SECRET fout
                                { :start (Date.) :end ENDDT :issuerCerts (seq cs) :issuerKey pk })
              (> (.length fout) 0)))

(is (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity  ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTKS
                           ^String (first (.keyAliases  ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTKS))
                           HELPME)
       fout (IO/MakeTmpfile "" ".jks")
       pk (.getPrivateKey pke)
       cs (.getCertificateChain pke) ]
            (RU/MakeSSv3JKS "C=AU,ST=NSW,L=Sydney,O=Google" SECRET fout
                                { :start (Date.) :end ENDDT :issuerCerts (seq cs) :issuerKey pk })
              (> (.length fout) 0)))

(is (let [ ^File fout (IO/MakeTmpfile "" ".p7b") ]
        (RU/ExportPkcs7 (CU/ResUrl "com/zotohlab/frwk/crypto/test.pfx") HELPME fout)
          (> (.length fout) 0)))


)

(def ^:private cryptostuff-eof nil)

;;(clojure.test/run-tests 'testcljc.crypto.cryptostuff)

