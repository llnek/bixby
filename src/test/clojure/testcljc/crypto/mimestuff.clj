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

  testcljc.crypto.mimestuff

  (:require [cmzlabsclj.nucleus.crypto.codec :as RT])
  (:require [cmzlabsclj.nucleus.crypto.stores :as ST])
  (:require [cmzlabsclj.nucleus.util.core :as CU])
  (:require [cmzlabsclj.nucleus.util.io :as IO])
  (:require [cmzlabsclj.nucleus.util.meta :as MU])
  (:require [cmzlabsclj.nucleus.crypto.core :as RU])
  (:use [clojure.test])
  (:import (org.apache.commons.io FileUtils IOUtils))
  (:import (java.security Policy KeyStore KeyStore$PrivateKeyEntry
    KeyStore$TrustedCertificateEntry SecureRandom))
  (:import (java.util Date GregorianCalendar))
  (:import (java.io File InputStream ByteArrayOutputStream))
  (:import (javax.mail Multipart BodyPart))
  (:import (javax.mail.internet MimeBodyPart MimeMessage MimeMultipart))
  (:import (javax.activation DataHandler DataSource))
  (:import (com.zotohlab.frwk.crypto SDataSource))
  (:import (com.zotohlab.frwk.io XData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ROOTPFX (CU/ResBytes "com/zotohlab/frwk/crypto/test.pfx"))
(def ^:private HELPME (RT/Pwdify "helpme"))
(def ^:private ROOTCS
  (ST/MakeCryptoStore (RU/InitStore! (RU/GetPkcsStore) ROOTPFX HELPME) HELPME))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testcrypto-mimestuff

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
        (let [ msg (RU/NewMimeMsg "" "" inp) ^Multipart mp (.getContent msg) ]
               (and (>= (.indexOf (.getContentType msg) "multipart/mixed") 0)
                    (== (.getCount mp) 2)
                    (not (RU/IsSigned? mp))
                    (not (RU/IsCompressed? mp))
                    (not (RU/IsEncrypted? mp)) ))))

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
      (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS
                             ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS))
                             HELPME)
               msg (RU/NewMimeMsg "" "" inp)
               cs (.getCertificateChain pke)
               pk (.getPrivateKey pke)
               rc (RU/SmimeDigSig  pk cs RU/SHA512 msg) ]
        (RU/IsSigned? rc))))

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
      (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
               msg (RU/NewMimeMsg "" "" inp)
               mp (.getContent msg)
               cs (.getCertificateChain pke)
               pk (.getPrivateKey pke)
               rc (RU/SmimeDigSig  pk cs RU/SHA512 mp) ]
        (RU/IsSigned? rc))))

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
      (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
               msg (RU/NewMimeMsg "" "" inp)
               ^Multipart mp (.getContent msg)
               bp (.getBodyPart mp 1)
               cs (.getCertificateChain pke)
               pk (.getPrivateKey pke)
               rc (RU/SmimeDigSig  pk cs RU/SHA512 bp) ]
        (RU/IsSigned? rc))))

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
      (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
               msg (RU/NewMimeMsg "" "" inp)
               cs (.getCertificateChain pke)
               pk (.getPrivateKey pke)
               mp (RU/SmimeDigSig  pk cs RU/SHA512 msg)
               baos (IO/MakeBitOS)
               msg2 (doto (RU/NewMimeMsg "" "")
                      (.setContent (cast Multipart mp))
                      (.saveChanges)
                      (.writeTo baos))
               msg3 (RU/NewMimeMsg "" "" (IO/Streamify (.toByteArray baos)))
               mp3 (.getContent msg3)
               rc (RU/PeekSmimeSignedContent mp3) ]
        (instance? Multipart rc))))

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
      (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
               msg (RU/NewMimeMsg "" "" inp)
               cs (.getCertificateChain pke)
               pk (.getPrivateKey pke)
               mp (RU/SmimeDigSig  pk cs RU/SHA512 msg)
               baos (IO/MakeBitOS)
               msg2 (doto (RU/NewMimeMsg "" "")
                      (.setContent (cast Multipart mp))
                      (.saveChanges)
                      (.writeTo baos))
               msg3 (RU/NewMimeMsg "" "" (IO/Streamify (.toByteArray baos)))
               mp3 (.getContent msg3)
               rc (RU/TestSmimeDigSig mp3 cs) ]
        (if (and (not (nil? rc)) (== (count rc) 2))
          (and (instance? Multipart (nth rc 0)) (instance? (MU/BytesClass) (nth rc 1)))
          false))))

(is (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
                s (SDataSource. (CU/Bytesify "hello world") "text/plain")
                cs (.getCertificateChain pke)
                pk (.getPrivateKey pke)
                bp (doto (MimeBodyPart.)
                    (.setDataHandler (DataHandler. s)))
                ^BodyPart bp2 (RU/SmimeEncrypt (nth cs 0) RU/DES_EDE3_CBC bp)
                baos (IO/MakeBitOS)
                msg (doto (RU/NewMimeMsg)
                        (.setContent (.getContent bp2) (.getContentType bp2))
                        (.saveChanges)
                        (.writeTo baos))
                msg2 (RU/NewMimeMsg (IO/Streamify (.toByteArray baos)))
                enc (RU/IsEncrypted? (.getContentType msg2))
                rc (RU/SmimeDecrypt [pk] msg2) ]
      ;; rc is a bodypart
           (and (not (nil? rc))
              (> (.indexOf (CU/Stringify rc) "hello world") 0))))

(is (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
                s2 (SDataSource. (CU/Bytesify "what's up dawg") "text/plain")
                s1 (SDataSource. (CU/Bytesify "hello world") "text/plain")
                cs (.getCertificateChain pke)
                pk (.getPrivateKey pke)
                bp2 (doto (MimeBodyPart.)
                      (.setDataHandler (DataHandler. s2)))
                bp1 (doto (MimeBodyPart.)
                      (.setDataHandler (DataHandler. s1)))
                mp (doto (MimeMultipart.)
                     (.addBodyPart bp1)
                     (.addBodyPart bp2))
                msg (doto (RU/NewMimeMsg) (.setContent  mp))
                ^BodyPart bp3 (RU/SmimeEncrypt (nth cs 0) RU/DES_EDE3_CBC msg)
                baos (IO/MakeBitOS)
                msg2 (doto (RU/NewMimeMsg)
                        (.setContent (.getContent bp3) (.getContentType bp3))
                        (.saveChanges)
                        (.writeTo baos))
                msg3 (RU/NewMimeMsg (IO/Streamify (.toByteArray baos)))
                enc (RU/IsEncrypted? (.getContentType msg3))
                rc (RU/SmimeDecrypt [pk] msg3) ]
      ;; rc is a multipart
           (and (not (nil? rc))
              (> (.indexOf (CU/Stringify rc) "what's up dawg") 0)
              (> (.indexOf (CU/Stringify rc) "hello world") 0))))

(is (let [ ^KeyStore$PrivateKeyEntry pke (.keyEntity ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS ^String (first (.keyAliases ^cmzlabsclj.nucleus.crypto.stores.CryptoStore ROOTCS)) HELPME)
             cs (.getCertificateChain pke)
             pk (.getPrivateKey pke)
             data (XData. "heeloo world")
             sig (RU/PkcsDigSig pk cs RU/SHA512 data)
             dg (RU/TestPkcsDigSig (nth cs 0) data sig) ]
        (if (and (not (nil? dg)) (instance? (MU/BytesClass) dg))
          true
          false)))

(is (with-open [ inp (CU/ResStream "com/zotohlab/frwk/mime/mime.eml") ]
        (let [ msg (RU/NewMimeMsg "" "" inp)
               bp (RU/SmimeCompress msg)
               ^XData x (RU/SmimeDecompress bp) ]
          (if (and (not (nil? x))
                    (> (alength ^bytes (.javaBytes x)) 0) )
            true
            false))))

(is (let [ bp (RU/SmimeCompress "text/plain" (XData. "heeloo world"))
           baos (IO/MakeBitOS)
           ^XData x (RU/SmimeDecompress bp) ]
          (if (and (not (nil? x))
                    (> (alength ^bytes (.javaBytes x)) 0) )
            true
            false)))

(is (let [ bp (RU/SmimeCompress "text/plain" "blah-blah" "some-id" (XData. "heeloo world"))
           baos (IO/MakeBitOS)
           ^XData x (RU/SmimeDecompress bp) ]
          (if (and (not (nil? x))
                    (> (alength ^bytes (.javaBytes x)) 0) )
            true
            false)))

(is (let [ f (RU/FingerprintSHA1 (CU/Bytesify "heeloo world")) ]
  (if (and (not (nil? f)) (> (.length f) 0))
    true
    false)) )

(is (let [ f (RU/FingerprintMD5 (CU/Bytesify "heeloo world")) ]
  (if (and (not (nil? f)) (> (.length f) 0))
    true
    false)) )

(is (let [f (RU/FingerprintSHA1 (CU/Bytesify "heeloo world"))
          g (RU/FingerprintMD5 (CU/Bytesify "heeloo world")) ]
  (if (= f g) false true)))

)

(def ^:private mimestuff-eof nil)

;;(clojure.test/run-tests 'testcljc.crypto.mimestuff)

