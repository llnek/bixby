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

(ns ^{ :doc "General utilties."
       :author "kenl" }

  cmzlabclj.nucleus.util.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr ]
            [clojure.core :as ccore ]
            [clojure.edn :as edn])

  (:import  [com.zotohlab.frwk.util CrappyDataError]
            [java.security SecureRandom]
            [com.google.gson JsonObject JsonElement]
            [java.net URL]
            [java.nio.charset Charset]
            [java.io InputStream File FileInputStream
                    ByteArrayInputStream ByteArrayOutputStream]
            [java.util Properties Date Calendar
                      GregorianCalendar TimeZone]
            [java.util.zip DataFormatException
                          Deflater Inflater]
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
(defmulti ^String NiceFPath
  "Convert the path into nice format (no) backslash." class)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Properties LoadJavaProps
  "Load java properties from input-stream." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _BOOLS #{ "true" "yes"  "on"  "ok"  "active"  "1"} )
(def ^:private _PUNCS #{ \_ \- \. \( \) \space } )
(deftype TYPE_NICHTS [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro TryC "Catch exception and log it."

  [& exprs]

  `(try (do ~@exprs) (catch Throwable e# (log/warn e# "") nil )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro Try! "Eat all exceptions."

  [& exprs]

  `(try (do ~@exprs) (catch Throwable e# nil )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro notnil? "True is x is not nil."

  [x]

  `(not (nil? ~x)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def NICHTS (TYPE_NICHTS.) )
(def KBS 1024)
(def MBS (* 1024 1024))
(def GBS (* 1024 1024 1024))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ternary ""

  [x y]

  (if (nil? x) y x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; local hack
(defn- nsb ""

  ^String
  [s]

  (if (nil? s) "" (.toString ^Object s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; local hack
(defn- get-czldr ""

  (^ClassLoader [] (get-czldr nil) )

  (^ClassLoader [^ClassLoader cl]
    (ternary cl (.getContextClassLoader (Thread/currentThread)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NilNichts ""

  ^Object
  [obj]

  (ternary obj NICHTS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsNichts? ""

  [obj]

  (identical? obj NICHTS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowUOE "Force throw an unsupported operation exception."

  [^String msg]

  (throw (UnsupportedOperationException. msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowBadArg "Force throw a bad parameter exception."

  [^String msg]

  (throw (IllegalArgumentException. msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowIOE "Throw an IO Exception."

  [^String msg]

  (throw (java.io.IOException. msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThrowBadData "Throw an Bad Data Exception."

  [^String msg]

  (throw (CrappyDataError. msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FlattenNil "Get rid of any nil(s) in a sequence."

  ;; a vector
  [somesequence]

  (cond
    (nil? somesequence) nil
    (empty? somesequence) []
    :else (into [] (remove nil? somesequence))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Interject ""

  [pojo field func]

  (let [nv (apply func [ (get pojo field) ])]
    (assoc pojo field nv)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro spos? "Safely test positive number."

  [e]

  `(and (number? ~e)(pos? ~e)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToJavaInt ""

  ^java.lang.Integer
  [n]

  (int n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ndz "Returns 0.0 if param is nil."

  ^double
  [d]

  (ternary d 0.0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nnz "Returns 0 is param is nil."

  ^long
  [n]

  (ternary n 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nbf "Returns false if param is nil."

  ;; boolean
  [b]

  (ternary b false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonObject ""

  ^JsonObject
  [^JsonObject json ^String field]

  (if (.has json field)
    (-> (.get json field)
        (.getAsJsonObject))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonString ""

  ^String
  [^JsonObject json ^String field]

  (if (.has json field)
    (-> (.get json field)
        (.getAsString))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonDouble ""

  [^JsonObject json ^String field]

  (if (.has json field)
    (-> (.get json field)
        (.getAsDouble))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonInt ""

  [^JsonObject json ^String field]

  (if (.has json field)
    (-> (.get json field)
        (.getAsInt))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeGetJsonBool ""

  [^JsonObject json ^String field]

  (if (.has json field)
    (-> (.get json field)
        (.getAsBoolean))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MatchChar? "Returns true if this char exists inside this set of chars."

  ;; boolean
  [ch setOfChars]

  (if (nil? setOfChars) false (ccore/contains? setOfChars ch)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SysVar "Get value for this system property."

  ^String
  [^String propname]

  (if (cstr/blank? propname)
    nil
    (System/getProperty propname)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EnvVar "Get value for this env var."

  ^String
  [^String envname]

  (if (cstr/blank? envname)
    nil
    (System/getenv envname)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn juid "Generate a unique id using std java."

  ^String
  []

  (.replaceAll (nsb (UID.)) "[:\\-]+" ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RandomSign ""

  []

  (if (even? (rand-int 1000000)) 1 -1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewRandom "Return a new random object."

  (^SecureRandom
    []
    (NewRandom 4))

  (^SecureRandom
    [numBytes]
    (SecureRandom. (SecureRandom/getSeed numBytes)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowJTstamp "Return a java sql Timestamp."

  ^Timestamp
  []

  (Timestamp. (.getTime (Date.))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowDate "Return a java Date."

  ^Date
  []

  (Date.) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowCal "Return a Gregorian Calendar."

  ^Calendar
  []

  (GregorianCalendar. ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToCharset "Return a java Charset of the encoding."

  (^Charset [^String enc] (Charset/forName enc))

  (^Charset [] (ToCharset "utf-8")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod NiceFPath String

  ^String
  [^String fpath]

  (FilenameUtils/normalizeNoEndSeparator (nsb fpath) true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod NiceFPath File

  ^String
  [^File aFile]

  (if (nil? aFile)
    ""
    (NiceFPath (.getCanonicalPath aFile))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsVar "Replaces all system & env variables in the value."

  ^String
  [^String value]

  (if (nil? value)
    ""
    (.replace (StrSubstitutor. (System/getenv))
              (StrSubstitutor/replaceSystemProperties value))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsSVar "Expand any sys-var found inside the string value."

  ^String
  [^String value]

  (if (nil? value)
    ""
    (StrSubstitutor/replaceSystemProperties value)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsEVar "Expand any env-var found inside the string value."

  ^String
  [^String value]

  (if (nil? value)
    ""
    (.replace (StrSubstitutor. (System/getenv)) value)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SubsProps "Expand any env & sys vars found inside the property values."

  ^Properties
  [^Properties props]

  (reduce (fn [^Properties memo k]
            (.put memo k (SubsVar (.get props k)))
            memo )
          (Properties.)
          (.keySet props)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SysProp "Get the value of a system property."

  ^String
  [^String prop]

  (System/getProperty (nsb prop) ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HomeDir "Get the user's home directory."

  ^File
  []

  (File. (SysProp "user.home")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetUser "Get the current user login name."

  ^String
  []

  (SysProp "user.name"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCwd "Get the current dir."

  ^String
  []

  (SysProp "user.dir"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn TrimLastPathSep "Get rid of trailing dir paths."

  ^String
  [path]

  (.replaceFirst (nsb path) "[/\\\\]+$"  ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Serialize "Object serialization."

  ^bytes
  [obj]

  (if (nil? obj)
    nil
    (SerializationUtils/serialize obj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Deserialize "Object deserialization."

  ^Object
  [^bytes bits]

  (if (nil? bits)
    nil
    (SerializationUtils/deserialize bits)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetClassname "Get the object's class name."

  ^String
  [^Object obj]

  (if (nil? obj)
    "null"
    (.getName (.getClass obj))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FilePath "Get the file path."

  ^String
  [aFile]

  (if (nil? aFile)
    ""
    (NiceFPath ^File aFile)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsWindows? "Returns true if platform is windows."

  []

  (>= (.indexOf (cstr/lower-case (SysProp "os.name"))
                "windows") 0 ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsUnix? "Returns true if platform is *nix."

  []

  (not (IsWindows?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvLong "Parse string as a long value."

  ^long
  [^String s dftLongVal]

  (try
    (Long/parseLong s)
    (catch Throwable e# dftLongVal)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvInt "Parse string as an int value."

  ^java.lang.Integer
  [^String s dftIntVal]

  (try
    (Integer/parseInt s)
    (catch Throwable e# (int dftIntVal))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvDouble "Parse string as a double value."

  ^double
  [^String s dftDblVal]

  (try
    (Double/parseDouble s)
    (catch Throwable e# dftDblVal)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConvBool "Parse string as a boolean value."

  ^Boolean
  [^String s]

  (ccore/contains? _BOOLS (cstr/lower-case (nsb s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadJavaProps InputStream

  [^InputStream inp]

  (doto (Properties.) (.load inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadJavaProps File

  [^File aFile]

  (LoadJavaProps (-> aFile (.toURI) (.toURL) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadJavaProps URL

  [^URL aFile]

  (with-open [inp (.openStream aFile) ]
    (LoadJavaProps inp)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Stringify "Make a string from bytes."

  (^String [^bytes bits]
           (Stringify bits "utf-8"))

  (^String [^bytes bits
            ^String encoding]
           (if (nil? bits)
             nil
             (String. bits encoding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Bytesify "Get bytes with the right encoding."

  (^bytes [^String s]
          (Bytesify s "utf-8"))

  (^bytes [^String s
           ^String encoding]
          (if (nil? s)
            nil
            (.getBytes s encoding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResStream "Load the resource as stream."

  (^InputStream [^String rcPath]
                (ResStream rcPath nil))

  (^InputStream [^String rcPath
                 ^ClassLoader czLoader]
                (if (nil? rcPath)
                  nil
                  (.getResourceAsStream (get-czldr czLoader)
                                        rcPath))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResUrl "Load the resource as URL."

  (^URL [^String rcPath]
        (ResUrl rcPath nil))

  (^URL [^String rcPath
         ^ClassLoader czLoader]
        (if (nil? rcPath)
          nil
          (.getResource (get-czldr czLoader)
                        rcPath))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResStr "Load the resource as string."

  (^String [^String rcPath
            ^String encoding]
           (ResStr rcPath encoding nil))

  (^String [^String rcPath]
           (ResStr rcPath "utf-8" nil))

  (^String [^String rcPath
            ^String encoding
            ^ClassLoader czLoader]
           (with-open [inp (ResStream rcPath czLoader) ]
             (Stringify (IOUtils/toByteArray inp)
                        encoding ))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResBytes "Load the resource as byte[]."

  (^bytes [^String rcPath]
          (ResBytes rcPath nil))

  (^bytes [^String rcPath
           ^ClassLoader czLoader]
          (with-open [inp (ResStream rcPath czLoader) ]
            (IOUtils/toByteArray inp))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Deflate "Compress the given byte[]."

  ^bytes
  [^bytes bits]

  (if (nil? bits)
    nil
    (let [buf (byte-array 1024)
          cpz (Deflater.) ]
      (doto cpz
        (.setLevel (Deflater/BEST_COMPRESSION))
        (.setInput bits)
        (.finish))
      (with-open [baos (ByteArrayOutputStream. (alength bits)) ]
        (loop []
          (if (.finished cpz)
            (.toByteArray baos)
            (do
              (.write baos
                      buf
                      0
                      (.deflate cpz buf))
              (recur))))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Inflate "Decompress the given byte[]."

  ^bytes
  [^bytes bits]

  (if (nil? bits)
    nil
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
            (recur)))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Normalize "Normalize a filepath, hex-code all non-alpha characters."

  ^String
  [^String fname]

  (str ""
       (reduce (fn [^StringBuilder buf ^Character ch]
                 (if (or (java.lang.Character/isLetterOrDigit ch)
                         (ccore/contains? _PUNCS ch))
                   (.append buf ch)
                   (.append buf (str "0x"
                                     (Integer/toString (int ch) 16)))))
               (StringBuilder.)
               (seq fname))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NowMillis "Return the current time in milliseconds."

  ^long
  []

  (java.lang.System/currentTimeMillis))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFPath "Return the file path only."

  ^String
  [^String fileUrlPath]

  (if (nil? fileUrlPath)
    ""
    (.getPath (URL. fileUrlPath))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtFileUrl "Return the file path as URL."

  ^URL
  [^String path]

  (if (cstr/blank? path)
    nil
    (.toURL (.toURI (File. path)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetch-tmpdir

  ^File
  [extra]

  (doto (File. (str (SysProp "java.io.tmpdir")
                    "/"
                    extra))
    (.mkdirs )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeTmpDir "Generate and return a new temp File dir."

  ^File
  []

  (fetch-tmpdir (juid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetTmpDir "Return the current temp File dir."

  ^File
  []

  (fetch-tmpdir ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; test and assert funcs
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti test-isa "Tests if object is subclass of parent."
  (fn [a b c]
    (cond
      (instance? Class b) :class
      :else :object)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-isa :class

  [^String param ^Class childz ^Class parz]

  (assert (and (notnil? childz) (.isAssignableFrom parz childz))
          (str "" param " not-isa " (.getName parz))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-isa :object

  [^String param ^Object obj ^Class parz]

  (assert (and (notnil? obj) (.isAssignableFrom parz (.getClass obj)))
          (str "" param " not-isa " (.getName parz))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-nonil "Assert object is not null."

  [^String param obj]

  (assert (notnil? obj)
          (str "" param " is null.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-cond "Assert a condition."

  [^String msg cnd ]

  (assert (= cnd true)
          (str msg)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-nestr "Assert string is not empty."

  [^String param v]

  (assert (not (StringUtils/isEmpty v))
          (str "" param " is empty.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti test-nonegnum "Assert number is not negative."
  (fn [a b]
    (condp instance? b
      Double  :double
      Long  :long
      Float  :double
      Integer  :long
      (ThrowBadArg "allow numbers only"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti test-posnum "Assert number is positive."
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
          (str "" param " must be >= 0.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-nonegnum :long

  [^String param v]

  (assert (>= v 0)
          (str "" param " must be >= 0.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-posnum :double

  [^String param v]

  (assert (> v 0.0)
          (str "" param " must be > 0.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod test-posnum :long

  [^String param v]

  (assert (> v 0)
          (str "" param " must be > 0.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn test-neseq "Assert sequence is not empty."

  [^String param v]

  (assert (not (nil? (not-empty v)))
          (str  param  " must be non empty.")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RootCause "Dig into error and find the root exception."

  ^Throwable
  [root]

  (loop [r root t (if (nil? root) nil (.getCause ^Throwable root)) ]
    (if (nil? t)
      r
      (recur t (.getCause t)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RootCauseMsg "Dig into error and find the root exception message."

  [root]

  (let [ e (RootCause root) ]
    (if (nil? e)
      ""
      (str (.getName (.getClass e)) ": " (.getMessage e)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenNumbers "Return a list of random int numbers between a range."

  ^clojure.lang.IPersistentCollection
  [start end howMany]

  (if (or (>= start end) (< (- end start) howMany) )
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
            (recur _end r rc cnt) ))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SortJoin "Sort a list of strings and then concatenate them."

  ([ss]
   (SortJoin "" ss))

  ([sep ss]
   (if (nil? ss)
     ""
     (cstr/join sep (sort ss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IntoMap ""

  [^java.util.Map props]

  (persistent! (reduce (fn [sum k]
                         (assoc! sum (keyword k) (.get props k)))
                       (transient {})
                       (seq (.keySet props)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol MubleAPI

  "A Mutable Interface."

  (setf! [_ k v] )
  (seq* [_] )
  (toEDN [_])
  (getf [_ k] )
  (clear! [_] )
  (clrf! [_ k] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype MutableMap

  ;;[ ^:volatile-mutable data ]
  [ ^:unsynchronized-mutable data ]

  MubleAPI

  (setf! [_ k v] (set! data (assoc data k v)))
  (clrf! [_ k] (set! data (dissoc data k)))
  (toEDN [_] (edn/write-string data))
  (seq* [_] (seq data))
  (getf [_ k] (get data k))
  (clear! [_ ] (set! data {} )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeMMap ""

  ^cmzlabclj.nucleus.util.core.MubleAPI
  []

  (MutableMap. {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PrintMutableObj ""

  ([^cmzlabclj.nucleus.util.core.MubleAPI ctx dbg ]
   (let [buf (StringBuilder.) ]
     (doseq [[k v] (.seq* ctx) ]
       (.append buf (str k " = " v "\n")))
     (.append buf "\n")
     (when-let [s (str buf) ]
       (if dbg (log/debug s)(log/info s)))))

  ([ctx]
   (PrintMutableObj ctx false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StripNSPath ""

  ^String
  [path]

  (let [s (str path) ]
    (if (.startsWith s ":")
      (.substring s 1)
      s)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NormalizeEmail ""

  ^String
  [^String email]

  (cond

    (cstr/blank? email)
    email

    (or (not (> (.indexOf email (int \@)) 0))
        (not= (.lastIndexOf email (int \@))
              (.indexOf email (int \@))))
    (ThrowBadData (str "Bad email address " email))

    :else
    (let [ss (StringUtils/split email "@" 2) ]
      (str (aget ss 0) "@" (cstr/lower-case (aget ss 1))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

