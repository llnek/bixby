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

  comzotohlabscljc.crypto.codec

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.math.numeric-tower :as math])
  (:use [comzotohlabscljc.util.core
         :only [NewRandom Bytesify Stringify ThrowBadArg ternary] ])
  (:use [comzotohlabscljc.util.io :only [MakeBitOS] ])
  (:use [comzotohlabscljc.util.str :only [nsb] ])
  (:import (org.apache.commons.codec.binary Base64))
  (:import (javax.crypto.spec SecretKeySpec))
  (:import (org.jasypt.encryption.pbe StandardPBEStringEncryptor))
  (:import (org.jasypt.util.text StrongTextEncryptor))
  (:import (java.io ByteArrayOutputStream))
  (:import (java.security SecureRandom))
  (:import (javax.crypto Cipher))
  (:import (org.mindrot.jbcrypt BCrypt))
  (:import (org.bouncycastle.crypto.params DESedeParameters KeyParameter))
  (:import (org.bouncycastle.crypto.paddings PaddedBufferedBlockCipher))
  (:import (org.bouncycastle.crypto KeyGenerationParameters))
  (:import (org.bouncycastle.crypto.engines DESedeEngine))
  (:import (org.bouncycastle.crypto.generators DESedeKeyGenerator))
  (:import (org.bouncycastle.crypto.modes CBCBlockCipher))
  (:import (org.apache.commons.lang3 StringUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; using amap below causes reflection warnings, I can't fix it, so turn checking
;; off explicitly for this file.
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^chars ^:private VISCHS
  (.toCharArray (str " @N/\\Ri2}aP`(xeT4F3mt;8~%r0v:L5$+Z{'V)\"CKI_c>z.*"
       "fJEwSU7juYg<klO&1?[h9=n,yoQGsW]BMHpXb6A|D#q^_d!-")))
(def ^:private VISCHS_LEN (alength ^chars VISCHS))

;;(def ^:private PCHS "abcdefghijklmnopqrstuvqxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`1234567890-_~!@#$%^&*()" )
(def ^String ^:private PCHS "Ha$4Jep8!`g)GYkmrIRN72^cObZ%oXlSPT39qLMD&iC*UxKWhE#F5@qvV6j0f1dyBs-~tAQn(z_u" )
;;(def ^:private ACHS "abcdefghijklmnopqrstuvqxyz1234567890-_ABCDEFGHIJKLMNOPQRSTUVWXYZ" )
(def ^String ^:private ACHS "nhJ0qrIz6FmtPCduWoS9x8vT2-KMaO7qlgApVX5_keyZDjfE13UsibYRGQ4NcLBH" )

(def ^chars ^:private s_asciiChars (.toCharArray ^String ACHS))
(def ^chars ^:private s_pwdChars (.toCharArray ^String PCHS))

(def ^String ^:private PWD_PFX "CRYPT:" )
(def ^:private PWD_PFXLEN (.length PWD_PFX))

(def ^String ^:private T3_DES "TripleDES" ) ;; AES/ECB/PKCS5Padding/TripleDES
(def ^String ^:private C_KEY "ed8xwl2XukYfdgR2aAddrg0lqzQjFhbs" )
(def ^String ^:private C_ALGO T3_DES) ;; default javax supports this
;;(def ^:private ALPHA_CHS 26)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ensureKeySize ""

  ^String
  [^String keystr ^String algo]

  (let [ len (alength (Bytesify keystr)) ]
    (when (and (= T3_DES algo)
               (< len 24))
      (ThrowBadArg "Encryption key length must be 24, when using TripleDES"))
    keystr
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyAsBits ""

  ^bytes
  [^String pwd ^String algo]

  (let [ bits (Bytesify pwd) ]
    (if (and (= T3_DES algo)
             (> (alength bits) 24))
      (into-array Byte/TYPE (take 24 bits)) ;; only 24 bits wanted
      bits)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol BaseCryptor

  ""

  (decrypt [_ ^String pkey ^String cipherText] [_ ^String cipherText] )
  (encrypt [_ ^String pkey ^String clearText] [_ ^String clearText] )
  (algo [_] ))

;; BCrypt.checkpw(candidate, hashed)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PasswordAPI

  ""

  (validateHash [_ pwdTarget] )
  (toCharArray [_] )
  (encoded [_] )
  (stronglyHashed [_] )
  (hashed [_] )
  (text [_] ) )

(declare Pwdify)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; caesar cipher
(defn- identify-ch ""

  ^Character
  [pos]

  (aget ^chars VISCHS ^long pos))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- locate-ch ""

  ^long
  [^Character ch]

  (let [ idx (some (fn [i] (if (= ch (aget ^chars VISCHS i)) i nil))
                   (range VISCHS_LEN)) ]
    (ternary idx -1)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slide-forward ""

  [delta cpos]

  (let [ ptr (+ cpos delta)
         np (if (>= ptr VISCHS_LEN) (- ptr VISCHS_LEN) ptr) ]
    (identify-ch np)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slide-back ""

  [delta cpos]

  (let [ ptr (- cpos delta)
         np (if (< ptr 0) (+ VISCHS_LEN ptr) ptr) ]
    (identify-ch np)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- shiftenc ""

  [shiftpos delta cpos]

  (if (< shiftpos 0)
    (slide-forward delta cpos)
    (slide-back delta cpos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- shiftdec ""

  [shiftpos delta cpos]

  (if (< shiftpos 0)
    (slide-back delta cpos)
    (slide-forward delta cpos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CaesarEncrypt "Encrypt clear text by character rotation."

  ^String
  [^String text shiftpos]

  (if (or (StringUtils/isEmpty text) (= shiftpos 0))
    text
    (let [ delta (mod (math/abs shiftpos) VISCHS_LEN)
           ca (.toCharArray text)
           out (amap ^chars ca pos ret
                     (let [ ch (aget ^chars ca pos)
                            p (locate-ch ch) ]
                       (if (< p 0)
                           ch
                           (shiftenc shiftpos delta p)))) ]
      (String. ^chars out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CaesarDecrypt "Decrypt text which was encrypted by the caesar method."

  ^String
  [^String text shiftpos]

  (if (or (StringUtils/isEmpty text) (= shiftpos 0))
    text
    (let [ delta (mod (math/abs shiftpos) VISCHS_LEN)
           ca (.toCharArray text)
           out (amap ^chars ca pos ret
                  (let [ ch (aget ^chars ca pos)
                         p (locate-ch ch) ]
                    (if (< p 0)
                        ch
                        (shiftdec shiftpos delta p)))) ]
      (String. ^chars out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; jasypt cryptor
(defn- jaDecr ""

  ^String
  [pkey text]

  (let [ c (StrongTextEncryptor.) ]
    (.setPassword c ^String pkey)
    (.decrypt c ^String text)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jaEncr ""

  ^String
  [pkey text]

  (let [ c (StrongTextEncryptor.) ]
    (.setPassword c ^String pkey)
    (.encrypt c ^String text)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JasyptCryptor "Make a cryptor using Jasypt lib."

  ^comzotohlabscljc.crypto.codec.BaseCryptor
  []

  (reify BaseCryptor

    (decrypt [this cipherText] (.decrypt this C_KEY cipherText))
    (decrypt [_ pkey cipherText] (jaDecr pkey cipherText))

    (encrypt [this clearText] (.encrypt this C_KEY clearText))
    (encrypt [_ pkey clearText] (jaEncr pkey clearText))

    (algo [_] "PBEWithMD5AndTripleDES")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; java cryptor
(defn- getCipher ""

  ^Cipher
  [pkey mode ^String algo]

  (let [ spec (SecretKeySpec. (keyAsBits pkey algo) algo) ]
    (doto (Cipher/getInstance algo)
          (.init ^long mode spec))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jcEncr ""

  ^String
  [pkey ^String text algo]

  (if (StringUtils/isEmpty text)
    text
    (let [ c (getCipher pkey Cipher/ENCRYPT_MODE algo )
           p (Bytesify text)
           baos (MakeBitOS)
           out (byte-array (max 4096 (.getOutputSize c (alength p))))
           n (.update c p 0 (alength p) out 0) ]
      (when (> n 0) (.write baos out 0 n))
      (let [ n2 (.doFinal c out 0) ]
        (when (> n2 0) (.write baos out 0 n2)))
      (Base64/encodeBase64URLSafeString (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jcDecr ""

  ^String
  [pkey ^String encoded algo]

  (if (StringUtils/isEmpty encoded)
    encoded
    (let [ c (getCipher pkey Cipher/DECRYPT_MODE algo )
           p (Base64/decodeBase64 encoded)
           baos (MakeBitOS)
           out (byte-array (max 4096 (.getOutputSize c (alength p))))
           n (.update c p 0 (alength p) out 0) ]
      (when (> n 0) (.write baos out 0 n))
      (let [ n2 (.doFinal c out 0) ]
        (when (> n2 0) (.write baos out 0 n2)))
      (Stringify (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JavaCryptor "Make a Standard Java cryptor."

  ^comzotohlabscljc.crypto.codec.BaseCryptor
  []

  (reify BaseCryptor

    (decrypt [this cipherText] (.decrypt this C_KEY cipherText))
    (decrypt [this pkey cipherText]
      (ensureKeySize pkey (.algo this))
      (jcDecr pkey cipherText (.algo this)))

    (encrypt [this clearText] (.encrypt this C_KEY clearText))
    (encrypt [this pkey clearText]
      (ensureKeySize pkey (.algo this))
      (jcEncr pkey clearText (.algo this)))

    (algo [_] T3_DES) ;;PBEWithMD5AndDES
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BC cryptor
(defn- bcDecr ""

  ^String
  [^String pkey ^String text ^String algo]

  (if (StringUtils/isEmpty text)
    text
    (let [ cipher (doto (-> (DESedeEngine.)
                            (CBCBlockCipher. )
                            (PaddedBufferedBlockCipher. ))
                        (.init false (KeyParameter. (keyAsBits pkey algo))))
           p (Base64/decodeBase64 text)
           out (byte-array 1024)
           baos (MakeBitOS)
           c (.processBytes cipher p 0 (alength p) out 0) ]
      (when (> c 0) (.write baos out 0 c))
      (let [ c2 (.doFinal cipher out 0) ]
        (when (> c2 0) (.write baos out 0 c2)))
      (Stringify (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- bcEncr ""

  ^String
  [pkey ^String text algo]

  (if (StringUtils/isEmpty text)
    text
    (let [ cipher (doto (-> (DESedeEngine.)
                                (CBCBlockCipher. )
                                (PaddedBufferedBlockCipher. ))
                        (.init true (KeyParameter. (keyAsBits pkey algo))))
           out (byte-array 4096)
           baos (MakeBitOS)
           p (Bytesify text)
           c (.processBytes cipher p 0 (alength p) out 0) ]
      (when (> c 0) (.write baos out 0 c))
      (let [ c2 (.doFinal cipher out 0) ]
        (when (> c2 0) (.write baos out 0 c2)) )
      (Base64/encodeBase64String (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BouncyCryptor  "Make a cryptor using BouncyCastle."

  ^comzotohlabscljc.crypto.codec.BaseCryptor
  []

  (reify BaseCryptor
    (decrypt [this cipherText] (.decrypt this C_KEY cipherText))
    (decrypt [this pkey cipherText]
      (ensureKeySize pkey (.algo this))
      (bcDecr pkey cipherText (.algo this)))

    (encrypt [this clearText] (.encrypt this C_KEY clearText))
    (encrypt [this pkey clearText]
      (ensureKeySize pkey (.algo this))
      (bcEncr pkey clearText (.algo this)))

    (algo [_] T3_DES)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; passwords
(defn- createXXX ""

  [ len ^chars chArray]

  (cond
    (< len 0)
    nil

    (= len 0)
    ""

    :else
    (let [ ostr (char-array len)
           cl (alength chArray)
           r (NewRandom)
           rc (amap ^chars ostr pos ret
                    (let [ n (mod (.nextInt r Integer/MAX_VALUE) cl) ]
                      (aget chArray n))) ]
      (String. ^chars rc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Password [^String pwdStr ^String pkey]

  Object

  (equals [this obj] (and (instance? Password obj)
                          (= (.toString this)
                             (.toString ^Object obj))) )
  (hashCode [this] (.hashCode (nsb (.pwdStr this))))
  (toString [this] (.text this))

  PasswordAPI

  (toCharArray [_] (if (nil? pwdStr) (char-array 0) (.toCharArray pwdStr)))

  (stronglyHashed [_]
    (if (nil? pwdStr)
      [ ""  "" ]
      (let [ s (BCrypt/gensalt 12) ]
        [ (BCrypt/hashpw pwdStr s) s ] )))

  (hashed [_]
    (if (nil? pwdStr)
      [ "" "" ]
      (let [ s (BCrypt/gensalt 10) ]
        [ (BCrypt/hashpw pwdStr s) s ] )))

  (validateHash [this pwdTarget]
    (BCrypt/checkpw (.text this) pwdTarget))

  (encoded [_]
    (if (StringUtils/isEmpty pwdStr)
      ""
      (str PWD_PFX (.encrypt (JasyptCryptor) pkey pwdStr))))

  (text [_] (nsb pwdStr) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Pwdify "Create a password object."

  ([^String pwdStr] (Pwdify pwdStr C_KEY))

  ([^String pwdStr  ^String pkey]
   (cond
      (StringUtils/isEmpty pwdStr)
      (Password. "" pkey)

      (.startsWith pwdStr PWD_PFX)
      (Password. (.decrypt (JasyptCryptor) pkey (.substring pwdStr PWD_PFXLEN)) pkey)

      :else
      (Password. pwdStr pkey)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateRandomString "Randomly generate some text."

  ^String
  [len]

  (createXXX len s_asciiChars))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateStrongPwd "Generate a strong password."

  ^comzotohlabscljc.crypto.codec.Password
  [len]

  (Pwdify (createXXX len s_pwdChars)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private codec-eof nil)

