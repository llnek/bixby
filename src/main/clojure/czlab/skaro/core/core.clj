;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns ^{:doc "General utilities."
      :author "Kenneth Leung" }

  czlab.xlib.core

  (:require
    [czlab.xlib.logging :as log]
    [clojure.walk :refer :all]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [clojure.core :as ccore]
    [clojure.edn :as edn])

  (:import
    [java.util.concurrent.atomic AtomicLong AtomicInteger]
    [java.util.zip DataFormatException Deflater Inflater]
    [java.security SecureRandom]
    [czlab.xlib BadDataError]
    [clojure.lang Keyword]
    [czlab.xlib Muble]
    [java.net URL]
    [java.nio.charset Charset]
    [java.io
     Serializable
     InputStream
     File
     FileInputStream
     ObjectOutputStream
     ObjectInputStream
     ByteArrayInputStream
     ByteArrayOutputStream]
    [java.util
     Map
     Properties
     Date
     Calendar
     HashMap
     HashSet
     ArrayList
     GregorianCalendar
     TimeZone]
    [java.sql Timestamp]
    [java.rmi.server UID]
    [org.apache.commons.lang3.text StrSubstitutor]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^String fpath
  "Convert the path into nice format (no) backslash" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Properties loadJavaProps
  "Load java properties from input-stream" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _BOOLS #{ "true" "yes"  "on"  "ok"  "active"  "1"})
(def ^:private _PUNCS #{ \_ \- \. \( \) \space })

(deftype TypeNichts [])
(ns-unmap *ns* '->TypeNichts)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (meta nil) is fine, so no need to worry
(defmacro getTypeId

  "Get the typeid from the metadata"
  [m]

  `(:typeid (meta ~m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro tryclr

  "Catch exception,log it and return a default value"

  [defv & exprs]

  `(try ~@exprs (catch Throwable e# (log/warn e# "") ~defv )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro trycr

  "Catch exception, and return a default value"

  [defv & exprs]

  `(try ~@exprs (catch Throwable _# ~defv )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro tryc

  "Catch exception and log it, returns nil"

  [& exprs]

  `(trycr nil ~@exprs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro try!

  "Eat all exceptions"

  [& exprs]

  `(try ~@exprs (catch Throwable e# nil )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro trylet!

  "Try and eat the error"

  [bindings & body]

  `(try! (let ~bindings ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro tryletc

  "Try and eat(log) the error"

  [bindings & body]

  `(tryc (let ~bindings ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro doto->>

  "Combine doto and ->>"

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
(defmacro exp!

  "Create an exception instance"

  [e & args]

  (if (empty? args)
    `(new ~e)
    `(new ~e ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro trap!

  "Throw this exception"

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
(defmacro inst?

  "Same as clojure's instance?"

  [theType theObj]

  `(instance? ~theType ~theObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro cast?

  "If object is an instance of this type,
   return it else nil"

  [someType obj]

  `(let [x# ~obj]
     (if (instance? ~someType x#) x#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cexp?

  "Try to cast into an exception"

  ^Throwable
  [e]

  (cast? Throwable e))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro notnil?

  "is x not nil"

  [x]

  `(not (nil? ~x)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce GigaBytes (* 1024 1024 1024))
(defonce KiloBytes 1024)
(defonce MegaBytes (* 1024 1024))
(defonce NICHTS (TypeNichts.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ternary

  "The ternary operator"

  [x y]

  `(let [x# ~x]
     (if (nil? x#) ~y x#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; local hack
(defn- get-czldr ""

  (^ClassLoader [] (get-czldr nil) )

  (^ClassLoader [^ClassLoader cl]
    (or cl (.getContextClassLoader (Thread/currentThread)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc nilNichts

  "If object is nil, return a NICHTS"

  ^Object
  [obj]

  (or obj NICHTS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc isNichts?

  "Returns true if the object is the NICHTS"

  [obj]

  (identical? obj NICHTS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn throwUOE

  "Force throw an unsupported operation exception"

  [^String fmt & xs]

  (->> ^String
       (apply format fmt xs)
       (trap! UnsupportedOperationException )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn throwBadArg

  "Force throw a bad parameter exception"

  [^String fmt & xs]

  (->> ^String
       (apply format fmt xs)
       (trap! IllegalArgumentException )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti throwIOE "Throw an IO Exception" (fn [a & xs] (class a)))

(defmethod throwIOE

  Throwable

  [^Throwable t & xs]

  (trap! java.io.IOException t))

(defmethod throwIOE

  String

  [^String fmt & xs]

  (->> ^String
       (apply format fmt xs)
       (trap! java.io.IOException )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn throwBadData

  "Throw an Bad Data Exception"

  [^String fmt & xs]

  (->> ^String
       (apply format fmt xs)
       (trap! BadDataError )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro flattenNil

  "Get rid of any nil(s) in a sequence"

  ;; a vector
  [somesequence]

  `(into [] (remove nil? ~somesequence)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro rnil

  "Get rid of any nil(s) in a sequence"

  [somesequence]

  `(remove nil? ~somesequence))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn interject

  "Run the function on the current field value,
   replacing the key with the returned value"

  [pojo field func]

  {:pre [(fn? func)]}

  (let [nv (apply func pojo field [])]
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
(defmacro bool!

  "Make this into a real boolean value"
  [e]

  `(if-some [e# ~e] (not (false? e#)) false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn toJavaInt

  "Make this into a java int"

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
(defn matchChar?

  "true if this char is inside this set of chars"

  [ch setOfChars]

  (if (set? setOfChars) (ccore/contains? setOfChars ch) false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro sysVar

  "Get value for this system property"

  ^String
  [propname]

  `(when-some [p# ~propname] (System/getProperty p#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro envVar

  "Get value for this env var"

  ^String
  [envname]

  `(when-some [e# ~envname] (System/getenv e#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn asFQKeyword

  "Scope a name as keyword"

  ^Keyword
  [^String t]

  (keyword (str *ns* "/" t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn juid

  "Generate a unique id using std java"

  ^String
  []

  (.replaceAll (str (UID.)) "[:\\-]+" ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn randomSign

  "Randomly choose a sign, positive or negative"

  []

  (if (even? (rand-int 1000000)) 1 -1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn randomBoolValue

  "Randomly choose a boolean value"

  []

  (if (> (randomSign) 0) true false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn newRandom

  "A new random object"

  ^SecureRandom
  [ & [numBytes] ]

  (-> (long (or numBytes 4))
      (SecureRandom/getSeed )
      (SecureRandom.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nowJTstamp

  "A java sql Timestamp"

  ^Timestamp
  []

  (Timestamp. (.getTime (Date.))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nowDate

  "A java Date"

  ^Date
  []

  (Date.) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nowCal

  "A Gregorian Calendar"

  ^Calendar
  []

  (GregorianCalendar. ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn toCharset

  "a java Charset of the encoding"

  (^Charset [^String enc] (Charset/forName enc))

  (^Charset [] (toCharset "utf-8")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod fpath String

  ^String
  [^String fp]

  (if-not (nil? fp)
    (fpath (File. fp))
    fp))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod fpath File

  ^String
  [^File aFile]

  (if (nil? aFile)
    ""
    (.getCanonicalPath aFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn subsVar

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
(defn subsSVar

  "Expand any sys-var found inside the string value"

  ^String
  [^String value]

  (if (nil? value)
    ""
    (StrSubstitutor/replaceSystemProperties value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn subsEVar

  "Expand any env-var found inside the string value"

  ^String
  [^String value]

  (if (nil? value)
    ""
    (.replace (StrSubstitutor. (System/getenv)) value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn subsProps

  "Expand any env & sys vars found inside the property values"

  ^Properties
  [^Properties props]

  (reduce
    (fn [^Properties memo k]
      (.put memo
            k
            (subsVar (.get props k)))
      memo)
    (Properties.)
    (.keySet props)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn sysProp

  "Get the value of a system property"

  ^String
  [prop]

  (System/getProperty (str prop) ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn homeDir

  "Get the user's home directory"

  ^File
  []

  (io/file (sysProp "user.home")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getUser

  "Get the current user login name"

  ^String
  []

  (sysProp "user.name"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getCwd

  "Get the current dir"

  ^File
  []

  (io/file (sysProp "user.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn trimLastPathSep

  "Get rid of trailing dir paths"

  ^String
  [path]

  (.replaceFirst (str path) "[/\\\\]+$"  ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serialize

  "Object serialization"

  ^bytes
  [^Serializable obj]

  {:pre [(some? obj)]}

  (with-open [out (ByteArrayOutputStream. 4096)
              oos (ObjectOutputStream. out)]
    (.writeObject oos obj)
    (.toByteArray out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deserialize

  "Object deserialization"

  ^Serializable
  [^bytes bits]

  {:pre [(some? bits)]}

  (with-open [in (ByteArrayInputStream. bits)
              ois (ObjectInputStream. in)]
    (.readObject ois)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getClassname

  "Get the object's class name"

  ^String
  [^Object obj]

  (if (nil? obj)
    "null"
    (.getName (.getClass obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn filePath

  "Get the file path"

  ^String
  [aFile]

  (fpath aFile))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isWindows?

  "true if platform is windows"

  []

  (>= (.indexOf (cs/lower-case (sysProp "os.name")) "windows") 0 ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn isUnix?

  "true if platform is *nix"

  []

  (not (isWindows?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn convLong

  "Parse string as a long value"

  (^long
    [^String s dftLongVal]
    (trycr
      dftLongVal
      (Long/parseLong s) ))

  (^long
    [^String s]
    (convLong s 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn convInt

  "Parse string as an int value"

  (^java.lang.Integer
    [^String s dftIntVal]
    (trycr
      (int dftIntVal)
      (Integer/parseInt s)))

  (^java.lang.Integer
    [^String s]
    (convInt s 0)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn convDouble

  "Parse string as a double value"

  (^double
    [^String s dftDblVal]
    (trycr
      dftDblVal
      (Double/parseDouble s) ))

  (^double
    [^String s]
    (convDouble s 0.0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn convBool

  "Parse string as a boolean value"

  ^Boolean
  [^String s]

  (ccore/contains? _BOOLS (cs/lower-case (str s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loadJavaProps InputStream

  [^InputStream inp]

  (doto (Properties.) (.load inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loadJavaProps File

  [^File aFile]

  (loadJavaProps (io/as-url aFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loadJavaProps URL

  [^URL aFile]

  (with-open
    [inp (.openStream aFile)]
    (loadJavaProps inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn stringify

  "Make a string from bytes"

  (^String
    [^bytes bits]
    (stringify bits "utf-8"))

  (^String
    [^bytes bits
     ^String encoding]
    (when (some? bits)
      (String. bits encoding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bytesify

  "Get bytes with the right encoding"

  (^bytes
    [^String s]
    (bytesify s "utf-8"))

  (^bytes
    [^String s
     ^String encoding]
    (when (some? s)
      (.getBytes s encoding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn resStream

  "Load the resource as stream"

  (^InputStream
    [^String rcPath]
    (resStream rcPath nil))

  (^InputStream
    [^String rcPath
     ^ClassLoader czLoader]
    (when (some? rcPath)
      (-> (get-czldr czLoader)
          (.getResourceAsStream  rcPath)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn resUrl

  "Load the resource as URL"

  (^URL
    [^String rcPath]
    (resUrl rcPath nil))

  (^URL
    [^String rcPath
     ^ClassLoader czLoader]
    (when (some? rcPath)
      (-> (get-czldr czLoader)
          (.getResource rcPath))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn resStr

  "Load the resource as string"

  (^String
    [^String rcPath
     ^String encoding]
    (resStr rcPath encoding nil))

  (^String
    [^String rcPath]
    (resStr rcPath "utf-8" nil))

  (^String
    [^String rcPath
     ^String encoding
     ^ClassLoader czLoader]
    (with-open
      [out (ByteArrayOutputStream. 4096)
       inp (resStream rcPath czLoader)]
      (io/copy inp out :buffer-size 4096)
      (-> (.toByteArray out)
          (stringify  encoding))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn resBytes

  "Load the resource as byte[]"

  (^bytes
    [^String rcPath]
    (resBytes rcPath nil))

  (^bytes
    [^String rcPath
     ^ClassLoader czLoader]
    (with-open
      [out (ByteArrayOutputStream. 4096)
       inp (resStream rcPath czLoader) ]
      (io/copy inp out :buffer-size 4096)
      (.toByteArray out))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deflate

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
(defn inflate

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
(defn normalize

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
(defn nowMillis

  "the current time in milliseconds"

  ^long
  []

  (java.lang.System/currentTimeMillis))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getFPath

  "the file path only"

  ^String
  [^String fileUrlPath]

  (if (nil? fileUrlPath)
    ""
    (.getPath (io/as-url fileUrlPath))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fmtFileUrl

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
    (if (instance? Class b) :class :object)))

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

  [^String param ^String v]

  (assert (and (notnil? v)(> (.length v) 0))
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
      (throwBadArg "allow numbers only"))))

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
      (throwBadArg "allow numbers only"))))

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
(defn rootCause

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
(defn rootCauseMsg

  "Dig into error and find the root exception message"

  [root]

  (let [e (rootCause root) ]
    (if (nil? e)
      ""
      (str (.getName (.getClass e)) ": " (.getMessage e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn genNumbers

  "A list of random int numbers between a range"

  ^clojure.lang.IPersistentCollection
  [start end howMany]

  (if (or (>= start end)
          (< (- end start) howMany) )
    []
    (loop [_end (if (< end Integer/MAX_VALUE)
                  (+ end 1)
                  end)
           r (newRandom)
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
(defn sortJoin

  "Sort a list of strings and then concatenate them"

  ([ss]
   (sortJoin "" ss))

  ([sep ss]
   (if (nil? ss)
     ""
     (cs/join sep (sort ss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn intoMap

  "Convert Java Map into Clojure Map"

  [^java.util.Map props]

  (persistent!
    (reduce (fn [sum k]
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
  (clear [_ ] (set! data {} )))
(ns-unmap *ns* '->UnsynchedMObj)

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
  (clear [_ ] (set! data {} )))
(ns-unmap *ns* '->VolatileMObj)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mubleObj!!

  "Create a volatile, mutable object"

  ^Muble
  [ & [opts] ]

  (let [m (VolatileMObj. {})
        opts (or opts {})]
    (doseq [[k v] (seq opts)]
      (.setv m k v))
    m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mubleObj!

  "Create a unsynchronized, mutable object"

  ^Muble
  [ & [opts] ]

  (let [m (UnsynchedMObj. {})
        opts (or opts {})]
    (doseq [[k v] (seq opts)]
      (.setv m k v))
    m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn printMutableObj

  "Print out this mutable object"

  [^Muble ctx & [dbg] ]

  (let [buf (StringBuilder.) ]
    (.append buf "\n")
    (doseq [[k v] (.seq ctx) ]
      (.append buf (str k " = " v "\n")))
    (.append buf "\n")
    (let [s (str buf)]
      (if dbg (log/debug "%s" s)(log/info "%s" s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn prtStk

  "Print stack trace"

  [^Throwable e]

  (.printStackTrace e))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn stripNSPath

  "Remove the leading colon"

  ^String
  [path]

  (let [s (str path)]
    (if (.startsWith s ":")
      (.substring s 1)
      s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn normalizeEmail

  "Normalize an email address"

  ^String
  [^String email]

  (cond

    (empty? email)
    email

    (or (not (> (.indexOf email (int \@)) 0))
        (not= (.lastIndexOf email (int \@))
              (.indexOf email (int \@))))
    (throwBadData (str "Bad email address " email))

    :else
    (let [ss (.split email "@") ]
      (if (= 2 (alength ss))
        (str (aget ss 0) "@" (cs/lower-case (aget ss 1)))
        (throwBadData (str "Bad email address " email))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare toJava)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- convList

  "Convert sequence to Java List"

  ^ArrayList
  [obj]

  (let [rc (ArrayList.)]
    (doseq [v (seq obj)]
      (.add rc (toJava v)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- convSet

  "Convert to Java Set"

  ^HashSet
  [obj]

  (let [rc (HashSet.)]
    (doseq [v (seq obj)]
      (.add rc (toJava v)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- convMap

  "Convert to Java Map"

  ^HashMap
  [obj]

  (let [rc (HashMap.)]
    (doseq [[k v] (seq obj)]
      (.put rc (name k) (toJava v)))
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- toJava

  "Convert a clojure collection to its Java equivalent"

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
(defn convToJava

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
(defn nextInt

  "A sequence number (integer)"

  ^Integer
  []

  (.getAndIncrement ^AtomicInteger _numInt))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nextLong

  "A sequence number (long)"

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
;;EOF


