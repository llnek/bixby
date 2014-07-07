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

  cmzlabsclj.nucleus.crypto.codec

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr]
            [clojure.math.numeric-tower :as math])
  (:use [cmzlabsclj.nucleus.util.core
         :only [NewRandom Bytesify Stringify ThrowBadArg ternary] ]
        [cmzlabsclj.nucleus.util.io :only [MakeBitOS] ]
        [cmzlabsclj.nucleus.util.str :only [nsb] ])
  (:import  [org.apache.commons.codec.binary Base64]
            [javax.crypto.spec SecretKeySpec]
            [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
            [org.jasypt.util.text StrongTextEncryptor]
            [java.io ByteArrayOutputStream]
            [java.security Key KeyFactory SecureRandom]
            [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
            [javax.crypto Cipher]
            [org.mindrot.jbcrypt BCrypt]
            [org.bouncycastle.crypto.params DESedeParameters KeyParameter]
            [org.bouncycastle.crypto.paddings PaddedBufferedBlockCipher]
            [org.bouncycastle.crypto KeyGenerationParameters]
            [org.bouncycastle.crypto.engines BlowfishEngine AESEngine
                                                      RSAEngine DESedeEngine]
            [org.bouncycastle.crypto.generators DESedeKeyGenerator]
            [org.bouncycastle.crypto.modes CBCBlockCipher]
            [org.apache.commons.lang3 StringUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; using amap below causes reflection warnings, I can't fix it, so turn checking
;; off explicitly for this file.
;;(set! *warn-on-reflection* false)
;; AES (128,256)
;; DES (8)
;; DESede (TripleDES - 8 x 3 = 24)
;; RSA  1024 +
;; Blowfish

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^chars ^:private VISCHS (.toCharArray (str " @N/\\Ri2}aP`(xeT4F3mt;8~%r0v:L5$+Z{'V)\"CKIc>z.*"
                                                "fJEwSU7juYg<klO&1?[h9=n,yoQGsW]BMHpXb6A|D#q^_d!-")))
(def ^:private VISCHS_LEN (alength ^chars VISCHS))

(def ^String ^:private PCHS "Ha$4Jep8!`g)GYkmrIRN72^cObZ%oXlSPT39qLMD&iC*UxKWhE#F5@qvV6j0f1dyBs-~tAQn(z_u" )
;;(def ^:private PCHS "abcdefghijklmnopqrstuvqxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`1234567890-_~!@#$%^&*()" )
(def ^String ^:private ACHS "nhJ0qrIz6FmtPCduWoS9x8vT2-KMaO7qlgApVX5_keyZDjfE13UsibYRGQ4NcLBH" )
;;(def ^:private ACHS "abcdefghijklmnopqrstuvqxyz1234567890-_ABCDEFGHIJKLMNOPQRSTUVWXYZ" )

(def ^chars ^:private s_asciiChars (.toCharArray ACHS))
(def ^chars ^:private s_pwdChars (.toCharArray PCHS))

(def ^String ^:private PWD_PFX "CRYPT:" )
(def ^:private PWD_PFXLEN (.length PWD_PFX))

(def ^String ^:private C_KEY "ed8xwl2XukYfdgR2aAddrg0lqzQjFhbs" )
(def ^String ^:private T3_DES "DESede" ) ;; TripleDES
(def ^chars ^:private C_KEYCS (.toCharArray C_KEY))
(def ^bytes ^:private C_KEYBS (Bytesify C_KEY))

(def ^String ^:private C_ALGO T3_DES) ;; default javax supports this
;;(def ^:private ALPHA_CHS 26)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ensureKeySize ""

  ^bytes
  [^bytes keyBits ^String algo]

  (let [len (* 8 (alength keyBits)) ]
    (when (and (= T3_DES algo)
               (< len 192)) ;; 8x 3 = 24 bytes
      (ThrowBadArg "Encryption key length must be 192, when using TripleDES"))
    (when (and (= "AES" algo)
               (< len 128))
      (ThrowBadArg "Encryption key length must be 128 or 256, when using AES"))
    keyBits
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyAsBits ""

  ^bytes
  [^bytes pwd ^String algo]

  (let [len (* 8 (alength pwd)) ]
    ;;(println "keyAsBits len of input key = " len)
    (cond
      (= "AES" algo)
      (cond
        (> len 256) ;; 32 bytes
        (into-array Byte/TYPE (take 32 pwd))
        ;; 128 => 16 bytes
        (and (> len 128) (< len 256))
        (into-array Byte/TYPE (take 16 pwd))

        :else pwd)

      (= T3_DES algo)
      (if (> len 192)
          ;; 24 bits => 3 bytes
          (into-array Byte/TYPE (take 24 pwd))
          pwd)

      :else pwd)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol BaseCryptor

  ""

  (decrypt [_ ^bytes pkey ^String cipherText] [_ ^String cipherText] )
  (encrypt [_ ^bytes pkey ^String text] [_ ^String text] )
  (algo [_] ))

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

  (let [idx (some (fn [i] (if (= ch (aget ^chars VISCHS i)) i nil))
                  (range VISCHS_LEN)) ]
    (ternary idx -1)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slide-forward ""

  [delta cpos]

  (let [ptr (+ cpos delta)
        np (if (>= ptr VISCHS_LEN) (- ptr VISCHS_LEN) ptr) ]
    (identify-ch np)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slide-back ""

  [delta cpos]

  (let [ptr (- cpos delta)
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
    (let [delta (mod (math/abs shiftpos) VISCHS_LEN)
          ca (.toCharArray text)
          out (amap ^chars ca pos ret
                     (let [ch (aget ^chars ca pos)
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
    (let [delta (mod (math/abs shiftpos) VISCHS_LEN)
          ca (.toCharArray text)
          out (amap ^chars ca pos ret
                  (let [ch (aget ^chars ca pos)
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
  [^chars pkey ^String text]

  (let [c (doto (StrongTextEncryptor.)
            (.setPasswordCharArray pkey)) ]
    (.decrypt c text)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jaEncr ""

  ^String
  [^chars pkey ^String text]

  (let [c (doto (StrongTextEncryptor.)
            (.setPasswordCharArray pkey)) ]
    (.encrypt c text)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JasyptCryptor "Make a cryptor using Jasypt lib."

  ^cmzlabsclj.nucleus.crypto.codec.BaseCryptor
  []

  (reify BaseCryptor

    (decrypt [this cipherText] (.decrypt this C_KEYCS cipherText))
    (decrypt [_ pkey cipherText] (jaDecr pkey cipherText))

    (encrypt [this text] (.encrypt this C_KEYCS text))
    (encrypt [_ pkey text] (jaEncr pkey text))

    (algo [_] "PBEWithMD5AndTripleDES")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; java cryptor
(defn- getCipher ""

  ^Cipher
  [^bytes pkey mode ^String algo]

  (let [spec (SecretKeySpec. (keyAsBits pkey algo) algo) ]
    (doto (Cipher/getInstance algo)
      (.init ^long mode spec))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaEncr ""

  ^String
  [^bytes pkey ^String text ^String algo]

  (if (StringUtils/isEmpty text)
    text
    (let [c (getCipher pkey Cipher/ENCRYPT_MODE algo)
          p (Bytesify text)
          baos (MakeBitOS)
          out (byte-array (max 4096 (.getOutputSize c (alength p))))
          n (.update c p 0 (alength p) out 0) ]
      (when (> n 0) (.write baos out 0 n))
      (let [n2 (.doFinal c out 0) ]
        (when (> n2 0) (.write baos out 0 n2)))
      (Base64/encodeBase64String (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaDecr ""

  ^String
  [^bytes pkey ^String encoded ^String algo]

  (if (StringUtils/isEmpty encoded)
    encoded
    (let [c (getCipher pkey Cipher/DECRYPT_MODE algo)
          p (Base64/decodeBase64 encoded)
          baos (MakeBitOS)
          out (byte-array (max 4096 (.getOutputSize c (alength p))))
          n (.update c p 0 (alength p) out 0) ]
      (when (> n 0) (.write baos out 0 n))
      (let [n2 (.doFinal c out 0) ]
        (when (> n2 0) (.write baos out 0 n2)))
      (Stringify (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JavaCryptor "Make a Standard Java cryptor."

  ^cmzlabsclj.nucleus.crypto.codec.BaseCryptor
  []

  (reify BaseCryptor

    (decrypt [this cipherText] (.decrypt this C_KEYBS cipherText))
    (decrypt [this pkey cipherText]
      (ensureKeySize pkey (.algo this))
      (javaDecr pkey cipherText (.algo this)))

    (encrypt [this clearText] (.encrypt this C_KEYBS clearText))
    (encrypt [this pkey clearText]
      (ensureKeySize pkey (.algo this))
      (javaEncr pkey clearText (.algo this)))

    (algo [_] T3_DES) ;;PBEWithMD5AndDES
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BC cryptor
(defn- bcXrefCipherEngine ""

  [^String algo]

  (case algo
    "DESede" (DESedeEngine.)
    "AES" (AESEngine.)
    "RSA" (RSAEngine.)
    "Blowfish" (BlowfishEngine.)
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 1024 - 2048 bits RSA
(defn AsymEncr ""

  ^String
  [^bytes pubKey ^String text]

  (if (StringUtils/isEmpty text)
    text
    (let [ ^Key pk (-> (KeyFactory/getInstance "RSA")
                        (.generatePublic (X509EncodedKeySpec. pubKey)))
           cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding")
                        (.init Cipher/ENCRYPT_MODE pk))
           out (.doFinal cipher (Bytesify text)) ]
      (Base64/encodeBase64String out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AsymDecr ""

  ^String
  [^bytes prvKey ^String cipherText]

  (if (StringUtils/isEmpty cipherText)
    cipherText
    (let [ ^Key pk (-> (KeyFactory/getInstance "RSA")
                        (.generatePrivate (PKCS8EncodedKeySpec. prvKey)))
           cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding")
                        (.init Cipher/DECRYPT_MODE pk))
           out (.doFinal cipher (Base64/decodeBase64 cipherText)) ]
      (Base64/encodeBase64String out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BC cryptor
(defn BcDecr ""

  ^String
  [^bytes pkey ^String text ^String algo]

  (if (= "RSA" algo)
      (AsymDecr pkey text)
      (if (StringUtils/isEmpty text)
        text
        (let [ cipher (doto (-> (bcXrefCipherEngine algo)
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
      )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BcEncr ""

  ^String
  [^bytes pkey ^String text ^String algo]

  (if (= "RSA" algo)
      (AsymEncr pkey text)
      (if (StringUtils/isEmpty text)
        text
        (let [ cipher (doto (-> (bcXrefCipherEngine algo)
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
      )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BouncyCryptor  "Make a cryptor using BouncyCastle."

  ^cmzlabsclj.nucleus.crypto.codec.BaseCryptor
  []

  (reify BaseCryptor
    (decrypt [this cipherText] (.decrypt this C_KEYBS cipherText))
    (decrypt [this pkey cipherText]
      (ensureKeySize pkey (.algo this))
      (BcDecr pkey cipherText (.algo this)))

    (encrypt [this clearText] (.encrypt this C_KEYBS clearText))
    (encrypt [this pkey clearText]
      (ensureKeySize pkey (.algo this))
      (BcEncr pkey clearText (.algo this)))

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
  (hashCode [this] (.hashCode (nsb (.text this))))
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

  (validateHash [this pwdHashed]
    (BCrypt/checkpw (.text this) pwdHashed))

  (encoded [_]
    (if (StringUtils/isEmpty pwdStr)
      ""
      (str PWD_PFX (.encrypt (JasyptCryptor) (.toCharArray pkey) pwdStr))))

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
      (Password. (.decrypt (JasyptCryptor) (.toCharArray pkey)
                                           (.substring pwdStr PWD_PFXLEN)) pkey)

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

  ^cmzlabsclj.nucleus.crypto.codec.Password
  [len]

  (Pwdify (createXXX len s_pwdChars)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private codec-eof nil)

