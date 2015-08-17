;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc "General utilties"
      :author "kenl" }

  czlab.xlib.util.core

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [clojure.core :as ccore]
    [clojure.edn :as edn])

  (:import
    [java.util.zip DataFormatException Deflater Inflater]
    [java.util.concurrent.atomic AtomicLong AtomicInteger]
    [com.zotohlab.frwk.util BadDataError]
    [com.zotohlab.skaro.core Muble]
    [java.security SecureRandom]
    [com.google.gson JsonObject JsonElement]
    [java.net URL]
    [java.nio.charset Charset]
    [java.io InputStream File FileInputStream
    ByteArrayInputStream
    ByteArrayOutputStream]
    [java.util Map Properties Date Calendar
    HashMap HashSet ArrayList
    GregorianCalendar TimeZone]
    [java.sql Timestamp]
    [java.rmi.server UID]
    [org.apache.commons.lang3.text StrSubstitutor]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.io IOUtils FilenameUtils]
    [org.apache.commons.lang3 SerializationUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^String FPath
  "Convert the path into nice format (no) backslash" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Properties LoadJavaProps
  "Load java properties from input-stream" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _BOOLS #{ "true" "yes"  "on"  "ok"  "active"  "1"} )
(def ^:private _PUNCS #{ \_ \- \. \( \) \space } )

(deftype TypeNichts [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (meta nil) is fine, so no need to worry
(defmacro GetTypeId "" [m] `(:typeid (meta ~m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro trycr

  "Catch exception,log it and return a default value"

  [defv & exprs]

  `(try ~@exprs (catch Throwable e# (log/warn e# "") ~defv )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro tryc

  "Catch exception and log it"

  [& exprs]

  `(trycr nil ~@exprs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro try!

  "Eat all exceptions"

  [& exprs]

  `(try ~@exprs (catch Throwable e# nil )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro trylet!

  "Try and skip error"

  [bindings & body]

  `(try! (let ~bindings ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro tryletc

  "Try and log error"

  [bindings & body]

  `(tryc (let ~bindings ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro doto->>

  ""

  [x & forms]

  (let [gx (gensym)]
    `(let [~gx ~x]
       ~@(map (fn [f]
                (if (seq? f)
                  `(~@f ~gx)
                  `(~f ~gx)))
              forms)
       ~gx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ex*

  ""

  [e & args]

  (if (empty? args)
    `(new ~e)
    `(new ~e ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro trap!

  ""

  [e & args]

  (if (empty? args)
    `(throw (new ~e))
    `(throw (new ~e ~@args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro do->false "Do and return false" [& exprs] `(do ~@exprs false))
(defmacro do->nil "Do and return nil" [& exprs] `(do ~@exprs nil))
(defmacro do->true "Do and return true" [& exprs] `(do ~@exprs true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro Inst?

  "Same as clojure's instance?"

  [a b]

  `(instance? ~a ~b))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro Cast?

  "If object is an instance of this type,
   return it else nil"

  [someType obj]

  `(let [x# ~obj]
     (if (instance? ~someType x#) x#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ce? "" ^Throwable [e]  (Cast? Throwable e))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro notnil?

  "is x not nil"

  [x]

  `(not (nil? ~x)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce GBS (* 1024 1024 1024))
(defonce KBS 1024)
(defonce MBS (* 1024 1024))
(defonce NICHTS (TypeNichts.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ternary ""

  [x y]

  `(let [x# ~x]
     (if (nil? x#) ~y x#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; local hack
(defn- nsb ""

  ^String
  [^Object s]

  (if (nil? s) "" (.toString s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; local hack
(defn- get-czldr ""

  (^ClassLoader [] (get-czldr nil) )

  (^ClassLoader [^ClassLoader cl]
    (or cl (.getContextClassLoader (Thread/currentThread)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc NilNichts

  "If object is nil, return a NICHTS"

  ^Object
  [obj]

  (or obj NICHTS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc IsNichts?

  "Returns true if the object is the NICHTS"

  [obj]

  (identical? obj NICHTS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowUOE

  "Force throw an unsupported operation exception"

  [^String msg]

  (trap! UnsupportedOperationException msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowBadArg

  "Force throw a bad parameter exception"

  [^String msg]

  (trap! IllegalArgumentException msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ThrowIOE "Throw an IO Exception" class)

(defmethod ThrowIOE Throwable
  [^Throwable t]
  (trap! java.io.IOException t))

(defmethod ThrowIOE String
  [^String msg]
  (trap! java.io.IOException msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowBadData

  "Throw an Bad Data Exception"

  [^String msg]

  (trap! BadDataError msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro FlattenNil

  "Get rid of any nil(s) in a sequence"

  ;; a vector
  [somesequence]

  `(into [] (remove nil? ~somesequence)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro RNil

  "Get rid of any nil(s) in a sequence"

  [somesequence]

  `(remove nil? ~somesequence))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Interject

  "Run the function on the current field value,
   replacing the key with the returned value"

  [pojo field func]

  {:pre [(fn? func)]}

  (let [nv (apply func pojo field  [])]
    (assoc pojo field nv)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro spos?

  "Safely test positive number"

  [e]

  `(let [e# ~e]
     (and (number? e#)(pos? e#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro bool! ""
  [e]
  `(if-some [e# ~e] (not (false? e#)) false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToJavaInt ""

  ^java.lang.Integer
  [n]

  (int n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ndz

  "0.0 if param is nil"

  ^double
  [d]

  (or d 0.0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nnz

  "0 is param is nil"

  ^long
  [n]

  (or n 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nbf

  "false if param is nil"

  ;; boolean
  [b]

  (or b false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonObject

  "If the field is a JsonObject, return it else nil"

  ^JsonObject
  [^JsonObject json ^String field]

  (when (.has json field)
    (-> (.get json field)
        (.getAsJsonObject))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonString

  "If the field is a String, return it else nil"

  ^String
  [^JsonObject json ^String field]

  (when (.has json field)
    (-> (.get json field)
        (.getAsString))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonDouble

  "If the field is a double, return it else nil"

  [^JsonObject json ^String field]

  (when (.has json field)
    (-> (.get json field)
        (.getAsDouble))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonInt

  "If the field is an int, return it else nil"

  [^JsonObject json ^String field]

  (when (.has json field)
    (-> (.get json field)
        (.getAsInt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonBool

  "If the field is a boolean, return it else nil"

  [^JsonObject json ^String field]

  (when (.has json field)
    (-> (.get json field)
        (.getAsBoolean))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MatchChar?

  "true if this char is inside this set of chars"

  ;; boolean
  [ch setOfChars]

  (if (nil? setOfChars) false (ccore/contains? setOfChars ch)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro SysVar

  "Get value for this system property"

  ^String
  [propname]

  `(when-some [p# ~propname] (System/getProperty p#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro EnvVar

  "Get value for this env var"

  ^String
  [envname]

  `(when-some [e# ~envname] (System/getenv e#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn juid

  "Generate a unique id using std java"

  ^String
  []

  (.replaceAll (str (UID.)) "[:\\-]+" ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RandomSign

  "Randomly choose a sign, positive or negative"

  []

  (if (even? (rand-int 1000000)) 1 -1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RandomBoolValue

  "Randomly choose a boolean value"

  []

  (if (> (RandomSign) 0) true false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewRandom

  "a new random object"

  ^SecureRandom
  [ & [numBytes] ]

  (->> (long (or numBytes 4))
       (SecureRandom/getSeed )
       (SecureRandom.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowJTstamp

  "a java sql Timestamp"

  ^Timestamp
  []

  (Timestamp. (.getTime (Date.))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowDate

  "a java Date"

  ^Date
  []

  (Date.) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowCal

  "a Gregorian Calendar"

  ^Calendar
  []

  (GregorianCalendar. ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToCharset

  "a java Charset of the encoding"

  (^Charset [^String enc] (Charset/forName enc))

  (^Charset [] (ToCharset "utf-8")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod FPath String

  ^String
  [^String fpath]

  (FilenameUtils/normalizeNoEndSeparator (str fpath) true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod FPath File

  ^String
  [^File aFile]

  (if (nil? aFile)
    ""
    (FPath (.getCanonicalPath aFile))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsVar

  "Replaces all system & env variables in the value"

  ^String
  [^String value]

  (if (nil? value)
    ""
    (->> value
         (StrSubstitutor/replaceSystemProperties )
         (.replace (StrSubstitutor. (System/getenv))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsSVar

  "Expand any sys-var found inside the string value"

  ^String
  [^String value]

  (if (nil? value)
    ""
    (StrSubstitutor/replaceSystemProperties value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsEVar

  "Expand any env-var found inside the string value"

  ^String
  [^String value]

  (if (nil? value)
    ""
    (.replace (StrSubstitutor. (System/getenv)) value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsProps

  "Expand any env & sys vars found inside the property values"

  ^Properties
  [^Properties props]

  (reduce
    (fn [^Properties memo k]
      (.put memo
            k
            (SubsVar (.get props k)))
      memo)
    (Properties.)
    (.keySet props)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SysProp

  "Get the value of a system property"

  ^String
  [^String prop]

  (System/getProperty (str prop) ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HomeDir

  "Get the user's home directory"

  ^File
  []

  (io/file (SysProp "user.home")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetUser

  "Get the current user login name"

  ^String
  []

  (SysProp "user.name"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCwd

  "Get the current dir"

  ^File
  []

  (io/file (SysProp "user.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn TrimLastPathSep

  "Get rid of trailing dir paths"

  ^String
  [path]

  (.replaceFirst (str path) "[/\\\\]+$"  ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Serialize

  "Object serialization"

  ^bytes
  [obj]

  (when (some? obj)
    (SerializationUtils/serialize obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Deserialize

  "Object deserialization"

  ^Object
  [^bytes bits]

  (when (some? bits)
    (SerializationUtils/deserialize bits)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetClassname

  "Get the object's class name"

  ^String
  [^Object obj]

  (if (nil? obj)
    "null"
    (.getName (.getClass obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FilePath

  "Get the file path"

  ^String
  [^File aFile]

  (if (nil? aFile)
    ""
    (FPath aFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsWindows?

  "true if platform is windows"

  []

  (>= (.indexOf (cs/lower-case (SysProp "os.name"))
                "windows") 0 ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsUnix?

  "true if platform is *nix"

  []

  (not (IsWindows?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvLong

  "Parse string as a long value"

  (^long
    [^String s dftLongVal]
    (try
      (Long/parseLong s)
      (catch Throwable _ dftLongVal)))

  (^long
    [^String s]
    (ConvLong s 0)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvInt

  "Parse string as an int value"

  (^java.lang.Integer
    [^String s dftIntVal]
    (try
      (Integer/parseInt s)
      (catch Throwable _ (int dftIntVal))))

  (^java.lang.Integer
    [^String s]
    (ConvInt s 0)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvDouble

  "Parse string as a double value"

  (^double
    [^String s dftDblVal]
    (try
      (Double/parseDouble s)
      (catch Throwable _ dftDblVal)))

  (^double
      [^String s]
      (ConvDouble s 0.0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvBool

  "Parse string as a boolean value"

  ^Boolean
  [^String s]

  (ccore/contains? _BOOLS (cs/lower-case (str s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadJavaProps InputStream

  [^InputStream inp]

  (doto (Properties.) (.load inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadJavaProps File

  [^File aFile]

  (LoadJavaProps (io/as-url aFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadJavaProps URL

  [^URL aFile]

  (with-open
    [inp (.openStream aFile) ]
    (LoadJavaProps inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Stringify

  "Make a string from bytes"

  (^String
    [^bytes bits]
    (Stringify bits "utf-8"))

  (^String
    [^bytes bits
     ^String encoding]
    (when (some? bits)
      (String. bits encoding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Bytesify

  "Get bytes with the right encoding"

  (^bytes
    [^String s]
    (Bytesify s "utf-8"))

  (^bytes
    [^String s
     ^String encoding]
    (when (some? s)
      (.getBytes s encoding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResStream

  "Load the resource as stream"

  (^InputStream
    [^String rcPath]
    (ResStream rcPath nil))

  (^InputStream
    [^String rcPath
     ^ClassLoader czLoader]
    (when (some? rcPath)
      (-> (get-czldr czLoader)
          (.getResourceAsStream  rcPath))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResUrl

  "Load the resource as URL"

  (^URL
    [^String rcPath]
    (ResUrl rcPath nil))

  (^URL
    [^String rcPath
     ^ClassLoader czLoader]
    (when (some? rcPath)
      (-> (get-czldr czLoader)
          (.getResource rcPath))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResStr

  "Load the resource as string"

  (^String
    [^String rcPath
     ^String encoding]
    (ResStr rcPath encoding nil))

  (^String
    [^String rcPath]
    (ResStr rcPath "utf-8" nil))

  (^String
    [^String rcPath
     ^String encoding
     ^ClassLoader czLoader]
    (with-open
      [inp (ResStream rcPath czLoader) ]
      (-> (IOUtils/toByteArray inp)
          (Stringify  encoding ))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResBytes

  "Load the resource as byte[]"

  (^bytes
    [^String rcPath]
    (ResBytes rcPath nil))

  (^bytes
    [^String rcPath
     ^ClassLoader czLoader]
    (with-open
      [inp (ResStream rcPath czLoader) ]
      (IOUtils/toByteArray inp))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Deflate

  "Compress the given byte[]"

  ^bytes
  [^bytes bits]

  (when (some? bits)
    (let [buf (byte-array 1024)
          cpz (Deflater.) ]
      (doto cpz
        (.setLevel (Deflater/BEST_COMPRESSION))
        (.setInput bits)
        (.finish))
      (with-open
        [baos (ByteArrayOutputStream. (alength bits)) ]
        (loop []
          (if (.finished cpz)
            (.toByteArray baos)
            (do
              (.write baos
                      buf
                      0
                      (.deflate cpz buf))
              (recur))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Inflate

  "Decompress the given byte[]"

  ^bytes
  [^bytes bits]

  (when (some? bits)
    (let [buf (byte-array 1024)
          decr (Inflater.)
          baos (ByteArrayOutputStream. (alength bits)) ]
      (.setInput decr bits)
      (loop []
        (if (.finished decr)
          (.toByteArray baos)
          (do
            (.write baos
                    buf
                    0
                    (.inflate decr buf))
            (recur)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Normalize

  "Normalize a filepath, hex-code all non-alpha characters"

  ^String
  [^String fname]

  (->> (reduce (fn [^StringBuilder buf ^Character ch]
                 (if (or (java.lang.Character/isLetterOrDigit ch)
                         (ccore/contains? _PUNCS ch))
                   (.append buf ch)
                   (.append buf
                            (str "0x"
                                 (Integer/toString (int ch) 16)))))
               (StringBuilder.)
               (seq fname))
       (str "" )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowMillis

  "the current time in milliseconds"

  ^long
  []

  (java.lang.System/currentTimeMillis))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFPath

  "the file path only"

  ^String
  [^String fileUrlPath]

  (if (nil? fileUrlPath)
    ""
    (.getPath (io/as-url fileUrlPath))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtFileUrl

  "the file path as URL"

  ^URL
  [^String path]

  (when (some? path)
    (io/as-url (if (.startsWith "file:" path)
                 path
                 (str "file://" path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; test and assert funcs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti test-isa

  "Tests if object is subclass of parent"

  (fn [a b c]
    (if
      (instance? Class b) :class
      :object)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-isa :class

  [^String param ^Class childz ^Class parz]

  (assert (and (some? childz)
               (.isAssignableFrom parz childz))
          (str "" param " not-isa " (.getName parz))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-isa :object

  [^String param ^Object obj ^Class parz]

  (assert (and (some? obj)
               (.isAssignableFrom parz (.getClass obj)))
          (str "" param " not-isa " (.getName parz))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-nonil

  "Assert object is not null"

  [^String param obj]

  (assert (some? obj)
          (str "" param " is null")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-cond

  "Assert a condition"

  [^String msg cnd ]

  (assert cnd (str msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-nestr

  "Assert string is not empty"

  [^String param v]

  (assert (not (StringUtils/isEmpty v))
          (str "" param " is empty")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti test-nonegnum

  "Assert number is not negative"

  (fn [a b]
    (condp instance? b
      Double  :double
      Long  :long
      Float  :double
      Integer  :long
      (ThrowBadArg "allow numbers only"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti test-posnum

  "Assert number is positive"

  (fn [a b]
    (condp instance? b
      Double  :double
      Long  :long
      Float  :double
      Integer  :long
      (ThrowBadArg "allow numbers only"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-nonegnum :double

  [^String param v]

  (assert (>= v 0.0)
          (str "" param " must be >= 0")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-nonegnum :long

  [^String param v]

  (assert (>= v 0)
          (str "" param " must be >= 0")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-posnum :double

  [^String param v]

  (assert (> v 0.0)
          (str "" param " must be > 0")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-posnum :long

  [^String param v]

  (assert (> v 0)
          (str "" param " must be > 0")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-neseq

  "Assert sequence is not empty"

  [^String param v]

  (assert (not (nil? (not-empty v)))
          (str  param  " must be non empty")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RootCause

  "Dig into error and find the root exception"

  ^Throwable
  [root]

  (loop [r root
         t (if (some? root)
             (.getCause ^Throwable root)) ]
    (if (nil? t)
      r
      (recur t (.getCause t)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RootCauseMsg

  "Dig into error and find the root exception message"

  [root]

  (let [e (RootCause root) ]
    (if (nil? e)
      ""
      (str (.getName (.getClass e)) ": " (.getMessage e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenNumbers

  "a list of random int numbers between a range"

  ^clojure.lang.IPersistentCollection
  [start end howMany]

  (if (or (>= start end)
          (< (- end start) howMany) )
    []
    (loop [_end (if (< end Integer/MAX_VALUE)
                  (+ end 1)
                  end)
           r (NewRandom)
           rc []
           cnt howMany ]
      (if (<= cnt 0)
        rc
        (let [n (.nextInt r _end) ]
          (if (and (>= n start)
                   (not (ccore/contains? rc n)))
            (recur _end r (conj rc n) (dec cnt))
            (recur _end r rc cnt) ))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SortJoin

  "Sort a list of strings and then concatenate them"

  ([ss]
   (SortJoin "" ss))

  ([sep ss]
   (if (nil? ss)
     ""
     (cs/join sep (sort ss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IntoMap

  "Convert Java Map into Clojure Map"

  [^java.util.Map props]

  (persistent! (reduce (fn [sum k]
                         (assoc! sum (keyword k) (.get props k)))
                       (transient {})
                       (seq (.keySet props)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype UnsynchedMObj

  [ ^:unsynchronized-mutable data ]

  Muble

  (setv [_ k v] (set! data (assoc data k v)))
  (unsetv [_ k] (set! data (dissoc data k)))
  (toEDN [_] (pr-str data))
  (seq [_] (seq data))
  (getv [_ k] (get data k))
  (clear [_ ] (set! data {} )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype VolatileMObj

  [ ^:volatile-mutable data ]

  Muble

  (setv [_ k v] (set! data (assoc data k v)))
  (unsetv [_ k] (set! data (dissoc data k)))
  (toEDN [_] (pr-str data))
  (seq [_] (seq data))
  (getv [_ k] (get data k))
  (clear [_ ] (set! data {} )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- MubleObj!XXX

  "Create a mutable object"

  ^Muble
  [ & [opts] ]

  (let [opts (or opts {})
        m (atom {}) ]
    (doseq [[k v] (seq opts)]
      (swap! m assoc k v))
    (reify

      Muble

      (setv [_ k v] (swap! m assoc k v))
      (unsetv [_ k] (swap! m dissoc k))
      (toEDN [_] (pr-str @m))
      (seq [_] (seq @m))
      (getv [_ k] (get @m k))
      (clear [_ ] (reset! m {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MubleObj!!

  "Create a mutable object"

  ^Muble
  [ & [opts] ]

  (let [m (VolatileMObj. {})
        opts (or opts {})]
    (doseq [[k v] (seq opts)]
      (.setv m k v))
    m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MubleObj!

  "Create a mutable object"

  ^Muble
  [ & [opts] ]

  (let [m (UnsynchedMObj. {})
        opts (or opts {})]
    (doseq [[k v] (seq opts)]
      (.setv m k v))
    m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PrintMutableObj

  "Print out this mutable object"

  [^Muble ctx & [dbg] ]

  (let [buf (StringBuilder.) ]
    (.append buf "\n")
    (doseq [[k v] (.seq ctx) ]
      (.append buf (str k " = " v "\n")))
    (.append buf "\n")
    (when-some [s (str buf) ]
      (if dbg (log/debug "%s" s)(log/info "%s" s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PrtStk ""

  [^Throwable ex]

  (.printStackTrace ex))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StripNSPath

  "Remove the leading colon"

  ^String
  [path]

  (let [s (str path) ]
    (if (.startsWith s ":")
      (.substring s 1)
      s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NormalizeEmail

  "Normalize an email address"

  ^String
  [^String email]

  (cond

    (empty? email)
    email

    (or (not (> (.indexOf email (int \@)) 0))
        (not= (.lastIndexOf email (int \@))
              (.indexOf email (int \@))))
    (ThrowBadData (str "Bad email address " email))

    :else
    (let [ss (StringUtils/split email "@" 2) ]
      (str (aget ss 0) "@" (cs/lower-case (aget ss 1))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare toJava)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- convList ""

  ^ArrayList
  [obj]

  (let [rc (ArrayList.)]
    (doseq [v (seq obj)]
      (.add rc (toJava v)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- convSet ""

  ^HashSet
  [obj]

  (let [rc (HashSet.)]
    (doseq [v (seq obj)]
      (.add rc (toJava v)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- convMap ""

  ^HashMap
  [obj]

  (let [rc (HashMap.)]
    (doseq [[k v] (seq obj)]
      (.put rc (name k) (toJava v)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- toJava ""

  ^Object
  [obj]

  (cond
    (map? obj)
    (convMap obj)

    (set? obj)
    (convSet obj)

    (or (vector? obj)
        (list? obj))
    (convList obj)

    :else obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvToJava

  "Convert a clojure object to a Java object"

  ^Object
  [obj]

  (toJava obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _numInt (AtomicInteger. 1))
(def ^:private  _numLong (AtomicLong. 1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NextInt

  "a sequence number (integer)"

  ^Integer
  []

  (.getAndIncrement ^AtomicInteger _numInt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NextLong

  "a sequence number (long)"

  ^long
  []

  (.getAndIncrement ^AtomicLong _numLong))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro prn!! ""

  [fmt & args]

  `(println (apply format ~fmt ~@args [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro prn! ""

  [fmt & args]

  `(print (apply format ~fmt ~@args [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(ns-unmap *ns* '->UnsynchedMObj)
(ns-unmap *ns* '->VolatileMObj)
(ns-unmap *ns* '->TypeNichts)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

