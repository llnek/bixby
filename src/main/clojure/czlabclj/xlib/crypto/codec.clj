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

  czlabclj.xlib.crypto.codec

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr]
            [clojure.math.numeric-tower :as math])

  (:use [czlabclj.xlib.util.core
         :only
         [NewRandom Bytesify Stringify ThrowBadArg]]
        [czlabclj.xlib.util.io :only [MakeBitOS]]
        [czlabclj.xlib.util.str :only [nichts? nsb]])

  (:import  [org.bouncycastle.crypto.params DESedeParameters KeyParameter]
            [org.bouncycastle.crypto.paddings PaddedBufferedBlockCipher]
            [org.bouncycastle.crypto.generators DESedeKeyGenerator]
            [org.jasypt.encryption.pbe StandardPBEStringEncryptor]
            [org.apache.commons.codec.binary Base64]
            [javax.crypto.spec SecretKeySpec]
            [org.jasypt.util.text StrongTextEncryptor]
            [java.io ByteArrayOutputStream]
            [java.security Key KeyFactory SecureRandom]
            [java.security.spec PKCS8EncodedKeySpec
                                X509EncodedKeySpec]
            [javax.crypto Cipher]
            [com.zotohlab.frwk.crypto PasswordAPI]
            [org.mindrot.jbcrypt BCrypt]
            [org.bouncycastle.crypto KeyGenerationParameters]
            [org.bouncycastle.crypto.engines BlowfishEngine
             AESEngine RSAEngine DESedeEngine]
            [org.bouncycastle.crypto.modes CBCBlockCipher]
            [org.apache.commons.lang3 StringUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;; AES (128,256)
;; DES (8)
;; DESede (TripleDES - 8 x 3 = 24bytes -> 192 bits)
;; RSA  1024 +
;; Blowfish

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^chars VISCHS (.toCharArray (str " @N/\\Ri2}aP`(xeT4F3mt;8~%r0v:L5$+Z{'V)\"CKIc>z.*"
                                                "fJEwSU7juYg<klO&1?[h9=n,yoQGsW]BMHpXb6A|D#q^_d!-")))
(def ^:private VISCHS_LEN (alength ^chars VISCHS))

(def ^:private ^String PCHS "Ha$4Jep8!`g)GYkmrIRN72^cObZ%oXlSPT39qLMD&iC*UxKWhE#F5@qvV6j0f1dyBs-~tAQn(z_u" )
;;(def ^:private PCHS "abcdefghijklmnopqrstuvqxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`1234567890-_~!@#$%^&*()" )
(def ^:private ^String ACHS "nhJ0qrIz6FmtPCduWoS9x8vT2-KMaO7qlgApVX5_keyZDjfE13UsibYRGQ4NcLBH" )
;;(def ^:private ACHS "abcdefghijklmnopqrstuvqxyz1234567890-_ABCDEFGHIJKLMNOPQRSTUVWXYZ" )

(def ^:private ^chars s_asciiChars (.toCharArray ACHS))
(def ^:private ^chars s_pwdChars (.toCharArray PCHS))

(def ^:private ^String PWD_PFX "CRYPT:" )
(def ^:private PWD_PFXLEN (.length PWD_PFX))

(def ^:private ^String C_KEY "ed8xwl2XukYfdgR2aAddrg0lqzQjFhbs" )
(def ^:private ^String T3_DES "DESede" ) ;; TripleDES
(def ^:private ^chars C_KEYCS (.toCharArray C_KEY))
(def ^:private ^bytes C_KEYBS (Bytesify C_KEY))

(def ^:private ^String C_ALGO T3_DES) ;; default javax supports this
;;(def ^:private ALPHA_CHS 26)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ensureKeySize "Given an algo, make sure the key has enough bits."

  ^bytes
  [^bytes keyBits ^String algo]

  (let [len (* 8 (alength keyBits)) ]
    (when (and (= T3_DES algo)
               (< len 192)) ;; 8x 3 = 24 bytes
      (ThrowBadArg "TripleDES key length must be 192."))
    (when (and (= "AES" algo)
               (< len 128))
      (ThrowBadArg "AES key length must be 128 or 256."))
    keyBits
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyAsBits "Given the algo, sanitize the key, chop length if necessary."

  ^bytes
  [^bytes pwd ^String algo]

  (let [blen (alength pwd)
        len (* 8 blen) ]
    (condp = algo
      "AES"
      (cond
        (> len 256) ;; 32 bytes
        (into-array Byte/TYPE (take 32 pwd))
        ;; 128 => 16 bytes
        (and (> len 128) (< len 256))
        (into-array Byte/TYPE (take 16 pwd))
        :else pwd)

      T3_DES
      (if (> blen 24)
        ;; 24 bytes
        (into-array Byte/TYPE (take 24 pwd))
        pwd)

      pwd)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol BaseCryptor

  "Methods supported by a crypto object."

  (decrypt [_ ^bytes pkey ^String cipherText]
           [_ ^String cipherText] )

  (encrypt [_ ^bytes pkey ^String text]
           [_ ^String text] )

  (algo [_] ))

(declare Pwdify)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; caesar cipher
(defn- identify-ch "Lookup a character by the given index."

  ^Character
  [pos]

  (aget ^chars VISCHS ^long pos))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- locate-ch "Given a character, return the index."

  ^long
  [^Character ch]

  (let [idx (some #(if (= ch (aget ^chars VISCHS %1)) %1 nil)
                  (range VISCHS_LEN)) ]
    (or idx -1)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slide-forward ""

  ^Character
  [delta cpos]

  (let [ptr (+ cpos delta)
        np (if (>= ptr VISCHS_LEN) (- ptr VISCHS_LEN) ptr) ]
    (identify-ch np)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slide-back ""

  ^Character
  [delta cpos]

  (let [ptr (- cpos delta)
        np (if (< ptr 0) (+ VISCHS_LEN ptr) ptr) ]
    (identify-ch np)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- shiftenc ""

  ^Character
  [shiftpos delta cpos]

  (if (< shiftpos 0)
    (slide-forward delta cpos)
    (slide-back delta cpos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- shiftdec ""

  ^Character
  [shiftpos delta cpos]

  (if (< shiftpos 0)
    (slide-back delta cpos)
    (slide-forward delta cpos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- caesar-amap-expr ""

  ^Character
  [^chars ca pos shiftFunc]

  (let [ch (aget ca pos)
        p (locate-ch ch) ]
    (if (< p 0)
      ch
      (shiftFunc p))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CaesarEncrypt "Encrypt clear text by character rotation."

  ^String
  [^String text shiftpos]

  (if (or (== shiftpos 0)
          (nichts? text))
    text
    (let [delta (mod (math/abs shiftpos) VISCHS_LEN)
          ca (.toCharArray text)
          pf (partial shiftenc shiftpos delta)
          out (amap ca pos ret
                    (caesar-amap-expr ca pos pf)) ]
      (String. ^chars out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CaesarDecrypt "Decrypt text which was encrypted by the caesar method."

  ^String
  [^String text shiftpos]

  (if (or (== shiftpos 0)
          (nichts? text))
    text
    (let [delta (mod (math/abs shiftpos) VISCHS_LEN)
          ca (.toCharArray text)
          pf (partial shiftdec shiftpos delta)
          out (amap ca pos ret
                    (caesar-amap-expr ca pos pf)) ]
      (String. ^chars out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; jasypt cryptor
(defn- jaDecr "Decrypt using Jasypt."

  ^String
  [^chars pkey ^String text]

  (-> (doto (StrongTextEncryptor.)
            (.setPasswordCharArray pkey))
      (.decrypt text)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jaEncr "Encrypt using Jasypt."

  ^String
  [^chars pkey ^String text]

  (-> (doto (StrongTextEncryptor.)
            (.setPasswordCharArray pkey))
      (.encrypt text)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JasyptCryptor "Make a cryptor using Jasypt lib."

  ^czlabclj.xlib.crypto.codec.BaseCryptor
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
(defn- getCipher "Given an algo, create a Java Cipher instance."

  ^Cipher
  [^bytes pkey mode ^String algo]

  (doto (Cipher/getInstance algo)
        (.init (int mode)
               (SecretKeySpec. (keyAsBits pkey algo) algo))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaEncr "Encrypt using Java.  Returns a base64 encoded string."

  ^String
  [^bytes pkey ^String text ^String algo]

  (if (nichts? text)
    text
    (let [c (getCipher pkey Cipher/ENCRYPT_MODE algo)
          p (Bytesify text)
          plen (alength p)
          baos (MakeBitOS)
          out (byte-array (max 4096 (.getOutputSize c plen)))
          n (.update c p 0 plen out 0) ]
      (when (> n 0) (.write baos out 0 n))
      (let [n2 (.doFinal c out 0) ]
        (when (> n2 0) (.write baos out 0 n2)))
      (Base64/encodeBase64String (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaDecr "Decrypt using Java. Input is a base64 encoded cipher."

  ^String
  [^bytes pkey ^String encoded ^String algo]

  (if (nichts? encoded)
    encoded
    (let [c (getCipher pkey Cipher/DECRYPT_MODE algo)
          p (Base64/decodeBase64 encoded)
          plen (alength p)
          baos (MakeBitOS)
          out (byte-array (max 4096 (.getOutputSize c plen)))
          n (.update c p 0 plen out 0) ]
      (when (> n 0) (.write baos out 0 n))
      (let [n2 (.doFinal c out 0) ]
        (when (> n2 0) (.write baos out 0 n2)))
      (Stringify (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JavaCryptor "Make a Standard Java cryptor."

  ^czlabclj.xlib.crypto.codec.BaseCryptor
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

  (condp = algo
    "DESede" (DESedeEngine.)
    "AES" (AESEngine.)
    "RSA" (RSAEngine.)
    "Blowfish" (BlowfishEngine.)
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 1024 - 2048 bits RSA
(defn AsymEncr "Encrypt using a public key.  Returns a base64 encoded cipher."

  ^String
  [^bytes pubKey ^bytes data]

  (if (nil? data)
    data
    (let [^Key pk (-> (KeyFactory/getInstance "RSA")
                      (.generatePublic (X509EncodedKeySpec. pubKey)))
          cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding")
                       (.init Cipher/ENCRYPT_MODE pk))
          out (.doFinal cipher data) ]
      (Base64/encodeBase64String out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AsymDecr "Decrypt using a private key.  Input is a base64 encoded cipher."

  ^bytes
  [^bytes prvKey ^String cipherText]

  (when-not (nichts? cipherText)
    (let [^Key pk (-> (KeyFactory/getInstance "RSA")
                      (.generatePrivate (PKCS8EncodedKeySpec. prvKey)))
          cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding")
                   (.init Cipher/DECRYPT_MODE pk))]
      (.doFinal cipher (Base64/decodeBase64 cipherText)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BC cryptor
(defn BcDecr "Decrypt using BouncyCastle."

  ^String
  [^bytes pkey ^String text ^String algo]

  (if (nichts? text)
    text
    (let [cipher (doto (-> (bcXrefCipherEngine algo)
                           (CBCBlockCipher. )
                           (PaddedBufferedBlockCipher. ))
                   (.init false
                          (KeyParameter. (keyAsBits pkey algo))))
          p (Base64/decodeBase64 text)
          out (byte-array 1024)
          baos (MakeBitOS)
          c (.processBytes cipher p 0 (alength p) out 0) ]
      (when (> c 0) (.write baos out 0 c))
      (let [c2 (.doFinal cipher out 0) ]
        (when (> c2 0) (.write baos out 0 c2)))
      (Stringify (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BcEncr "Encrypt using BouncyCastle.  Returning a base64 encoded cipher."

  ^String
  [^bytes pkey ^String text ^String algo]

  (if (nichts? text)
    text
    (let [cipher (doto (-> (bcXrefCipherEngine algo)
                           (CBCBlockCipher. )
                           (PaddedBufferedBlockCipher. ))
                   (.init true
                          (KeyParameter. (keyAsBits pkey algo))))
          out (byte-array 4096)
          baos (MakeBitOS)
          p (Bytesify text)
          c (.processBytes cipher p 0 (alength p) out 0) ]
      (when (> c 0) (.write baos out 0 c))
      (let [c2 (.doFinal cipher out 0) ]
        (when (> c2 0) (.write baos out 0 c2)) )
      (Base64/encodeBase64String (.toByteArray baos)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BouncyCryptor  "Make a cryptor using BouncyCastle."

  ^czlabclj.xlib.crypto.codec.BaseCryptor
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

  [len ^chars chArray]

  (cond
    (== len 0)
    ""

    (< len 0)
    nil

    :else
    (let [ostr (char-array len)
          cl (alength chArray)
          r (NewRandom)
          rc (amap ^chars ostr pos ret
                    (let [n (mod (.nextInt r Integer/MAX_VALUE) cl) ]
                      (aget chArray n))) ]
      (String. ^chars rc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Password [^String pwdStr ^String pkey]

  Object

  (equals [this obj] (and (instance? Password obj)
                          (= (.toString this)
                             (nsb obj))) )
  (hashCode [this] (.hashCode (nsb (.text this))))
  (toString [this] (.text this))

  PasswordAPI

  (toCharArray [_] (if (nil? pwdStr)
                     (char-array 0)
                     (.toCharArray pwdStr)))

  (stronglyHashed [_]
    (if (nil? pwdStr)
      [""  ""]
      (let [s (BCrypt/gensalt 12) ]
        [ (BCrypt/hashpw pwdStr s) s ] )))

  (hashed [_]
    (if (nil? pwdStr)
      ["" ""]
      (let [s (BCrypt/gensalt 10) ]
        [ (BCrypt/hashpw pwdStr s) s ] )))

  (validateHash [this pwdHashed]
    (BCrypt/checkpw (.text this) pwdHashed))

  (encoded [_]
    (cond
      (nil? pwdStr)
      nil

      (nichts? pwdStr)
      ""

      :else
      (str PWD_PFX (.encrypt (JasyptCryptor)
                             (.toCharArray pkey)
                             pwdStr))))

  (text [_] (when-not (nil? pwdStr) (nsb pwdStr) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Pwdify "Create a password object."

  (^PasswordAPI [^String pwdStr] (Pwdify pwdStr C_KEY))

  (^PasswordAPI [^String pwdStr ^String pkey]
                (cond
                  (.startsWith (nsb pwdStr) PWD_PFX)
                  (Password. (.decrypt (JasyptCryptor)
                                       (.toCharArray pkey)
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

  ^PasswordAPI
  [len]

  (Pwdify (createXXX len s_pwdChars)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private codec-eof nil)

