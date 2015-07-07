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

(ns

  testcljc.crypto.cryptostuff

  (:use [czlabclj.xlib.crypto.stores]
        [czlabclj.xlib.crypto.codec]
        [czlabclj.xlib.util.core]
        [czlabclj.xlib.util.str]
        [czlabclj.xlib.util.io]
        [clojure.test]
        [czlabclj.xlib.crypto.core])

  (:import  [java.security KeyPair Policy KeyStore SecureRandom MessageDigest
                           KeyStore$PrivateKeyEntry KeyStore$TrustedCertificateEntry]
            [org.apache.commons.codec.binary Base64]
            [com.zotohlab.frwk.crypto CryptoStoreAPI PasswordAPI]
            [java.util Date GregorianCalendar]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ROOTPFX (ResBytes "com/zotohlab/frwk/crypto/test.pfx"))
(def ^:private ROOTJKS (ResBytes "com/zotohlab/frwk/crypto/test.jks"))
(def ^:private ENDDT (.getTime (GregorianCalendar. 2050 1 1)))
(def ^:private TESTPWD (Pwdify "secretsecretsecretsecretsecret"))
(def ^:private HELPME (Pwdify "helpme"))
(def ^:private SECRET (Pwdify "secret"))

(def ^CryptoStoreAPI ^:private ROOTCS (MakeCryptoStore (InitStore! (GetPkcsStore) ROOTPFX HELPME) HELPME))

(def ^CryptoStoreAPI ^:private ROOTKS (MakeCryptoStore (InitStore! (GetJksStore) ROOTJKS HELPME) HELPME))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testcrypto-cryptostuff

(is (not (= "heeloo, how are you?" (CaesarDecrypt (CaesarEncrypt "heeloo, how are you?" 709394) 666))))
(is (= "heeloo, how are you?" (CaesarDecrypt (CaesarEncrypt "heeloo, how are you?" 709394) 709394)))
(is (= "heeloo, how are you?" (CaesarDecrypt (CaesarEncrypt "heeloo, how are you?" 13) 13)))

(is (= "heeloo" (let [ c (JasyptCryptor) ]
                      (.decrypt c (.encrypt c "heeloo")))))

(is (= "heeloo" (let [ c (JasyptCryptor) pkey (.toCharArray (nsb SECRET)) ]
                      (.decrypt c pkey (.encrypt c pkey "heeloo")))))

(is (= "heeloo" (let [ c (JavaCryptor) ]
                      (.decrypt c (.encrypt c "heeloo")))))

(is (= "heeloo" (let [ c (JavaCryptor) pkey (Bytesify (nsb TESTPWD)) ]
                      (.decrypt c pkey (.encrypt c pkey "heeloo")))))

(is (= "heeloo" (let [ c (BouncyCryptor) ]
                      (.decrypt c (.encrypt c "heeloo")))))

(is (= "heeloo" (let [ c (BouncyCryptor) pkey (Bytesify (nsb TESTPWD)) ]
                      (.decrypt c pkey (.encrypt c pkey "heeloo")))))

(is (= "heeloo" (let [ pkey (Bytesify (nsb TESTPWD)) ]
                      (BcDecr pkey (BcEncr pkey "heeloo" "AES") "AES"))))

(is (= "heeloo" (let [ kp (MakeKeypair "RSA" 1024)
                       pu (.getEncoded (.getPublic kp))
                       pv (.getEncoded (.getPrivate kp)) ]
                      (Stringify (AsymDecr pv (AsymEncr pu (Bytesify "heeloo")))))))

(is (= (.length ^String (.text (CreateStrongPwd 16))) 16))
(is (= (.length (CreateRandomString 64)) 64))

(is (instance? PasswordAPI (Pwdify "secret-text")))
(is (.startsWith ^String (.encoded ^PasswordAPI (Pwdify "secret-text")) "CRYPT:"))


(is (= "SHA-512" (.getAlgorithm (MakeMsgDigest SHA_512))))
(is (= "MD5" (.getAlgorithm (MakeMsgDigest MD_5))))

(is (> (NextSerial) 0))

(is (> (.length (NewAlias)) 0))

(is (= "PKCS12" (.getType (GetPkcsStore))))
(is (= "JKS" (.getType (GetJksStore))))

(is (instance? Policy (MakeEasyPolicy)))

(is (> (.length (GenMac (Bytesify "secret") "heeloo world")) 0))
(is (> (.length (GenHash "heeloo world")) 0))

(is (not (nil? (MakeKeypair "RSA" 1024))))

(is (let [ v (MakeCsrReq 1024 "C=AU,ST=NSW,L=Sydney,O=Google,OU=HQ,CN=www.google.com" "PEM") ]
          (and (= (count v) 2) (> (alength ^bytes (first v)) 0) (> (alength ^bytes (nth v 1)) 0))) )

(is (let [ fout (TempFile "kenl" ".p12")]
      (MakeSSv1PKCS12 "C=AU,ST=NSW,L=Sydney,O=Google" HELPME fout
                          { :start (Date.) :end ENDDT :keylen 1024 })
      (> (.length fout) 0)))

(is (let [ fout (TempFile "" ".jks") ]
      (MakeSSv1JKS "C=AU,ST=NSW,L=Sydney,O=Google" SECRET fout
                          { :start (Date.) :end ENDDT :keylen 1024 })
            (> (.length fout) 0)))

(is (let [ ^KeyStore$PrivateKeyEntry pke
          (.keyEntity ROOTCS ^String (first (.keyAliases ROOTCS))
                           HELPME)
       fout (TempFile "" ".p12")
       pk (.getPrivateKey pke)
       cs (.getCertificateChain pke) ]
            (MakeSSv3PKCS12 "C=AU,ST=NSW,L=Sydney,O=Google" SECRET fout
                                { :start (Date.) :end ENDDT :issuerCerts (seq cs) :issuerKey pk })
              (> (.length fout) 0)))

(is (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ROOTKS
                           ^String (first (.keyAliases ROOTKS))
                           HELPME)
       fout (TempFile "" ".jks")
       pk (.getPrivateKey pke)
       cs (.getCertificateChain pke) ]
            (MakeSSv3JKS "C=AU,ST=NSW,L=Sydney,O=Google" SECRET fout
                                { :start (Date.) :end ENDDT :issuerCerts (seq cs) :issuerKey pk })
              (> (.length fout) 0)))

(is (let [ ^File fout (TempFile "" ".p7b") ]
        (ExportPkcs7 (ResUrl "com/zotohlab/frwk/crypto/test.pfx") HELPME fout)
          (> (.length fout) 0)))


)

(def ^:private cryptostuff-eof nil)

;;(clojure.test/run-tests 'testcljc.crypto.cryptostuff)

