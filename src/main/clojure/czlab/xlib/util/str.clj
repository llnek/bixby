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

(ns ^{:doc "String utilities"
      :author "kenl" }

  czlab.xlib.util.str

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:import
    [java.util Arrays Collection Iterator StringTokenizer]
    [org.apache.commons.lang3 StringUtils]
    [java.io CharArrayWriter File
     OutputStream OutputStreamWriter Reader Writer]
    [java.lang StringBuilder]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro lcase "Lowercase string" [s] `(if-some [s# ~s] (cs/lower-case s#) ""))
(defmacro ucase "Uppercase string" [s] `(if-some [s# ~s] (cs/upper-case s#) ""))
(defmacro stror "" [s s2] `(let [s# ~s] (if (empty? s#) ~s2 s#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn TrimL "" [^String src ^String s] (StringUtils/stripStart src s))
(defn TrimR "" [^String src ^String s] (StringUtils/stripEnd src s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasNocase?

  "true if this sub-string is inside this bigger string"

  [^String bigs ^String s]

  (>= (.indexOf (lcase bigs) (lcase s)) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Embeds?

  "true if this sub-string is inside this bigger string"

  [^String bigs ^String s]

  (>= (.indexOf bigs s) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Has?

  "true if this character is inside this string"

  [^String bigs ^Character ch]

  (>= (.indexOf bigs (int (.charValue ch))) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro sname

  "Safely get the name of this object"

  ^String
  [n]

  `(when-some [n# ~n] (name n#)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nsb

  "Empty string if obj is null, or obj.toString"

  ^String
  [^Object obj]

  (cond
    (keyword? obj) (name obj)
    (nil? obj) ""
    :else
    (.toString obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToKW

  "Concatenate all args and return it as a keyword"

  [& args]

  (keyword (cs/join "/" args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nsn

  "(null) if obj is null, or obj.toString"

  ^String
  [^Object obj]

  (if (nil? obj) "(null)" (.toString obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Same?

  "true if these 2 strings are the same"

  [^String a ^String b]

  (cond
    (and (nil? a) (nil? b))
    true

    (or (nil? a) (nil? b))
    false

    (not= (.length a) (.length b))
    false

    :else
    (Arrays/equals (.toCharArray a) (.toCharArray b)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro hgl?

  "true if this string is not empty"

  [s]

  `(not (empty? ~s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro nichts?

  "true if this string is empty"

  [s]

  `(empty? ~s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn strim

  "Safely trim this string - handles null"

  ^String
  [s]

  (if (nil? s) "" (cs/trim (nsb s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddDelim!

  "Append to a string-builder, optionally
   inserting a delimiter if the buffer is not empty"

  ^StringBuilder
  [^StringBuilder buf ^String delim ^String item]

  (when (some? item)
    (when (and (> (.length buf) 0)
               (some? delim))
      (.append buf delim))
    (.append buf item))
  buf)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Splunk

  "Split a large string into chunks, each chunk having a specific length"

  [^String largeString chunkLength]

  (if (nil? largeString)
    []
    (loop [ret (transient [])
           src largeString ]
      (if (<= (.length src) chunkLength)
        (persistent! (if (> (.length src) 0)
                       (conj! ret src)
                       ret))
        (recur (conj! ret (.substring src 0 chunkLength))
               (.substring src chunkLength))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasicAny?

  "Match against a list of possible args. (ignoring case)"

  [^String src substrs]

  (if (or (empty? substrs)
          (nil? src))
    false
    (let [lc (lcase src)]
      (true? (some #(>= (.indexOf lc (lcase %)) 0) substrs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasAny? "Returns true if src contains one of these substrings"

  [^String src substrs]

  (if (or (empty? substrs)
          (nil? src))
    false
    (true? (some #(>= (.indexOf src ^String %) 0) substrs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SWicAny? "Tests startWith() no-case, looping through
                the list of possible prefixes"

  [^String src pfxs]

  (if (or (empty? pfxs)
          (nil? src))
    false
    (let [lc (lcase src)]
      (true? (some #(.startsWith lc (lcase %)) pfxs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SWAny? "Tests startWith(), looping through
              the list of possible prefixes"

  [^String src pfxs]

  (if (or (empty? pfxs)
          (nil? src))
    false
    (true? (some #(.startsWith src ^String %) pfxs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EqicAny? "Tests String.equals() against a
                list of possible args. (ignore-case)"

  [^String src strs]

  (if (or (empty? strs)
          (nil? src))
    false
    (let [lc (lcase src)]
      (true? (some #(.equals lc (lcase %)) strs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EqAny? "Tests String.equals() against a list of possible args"

  [^String src strs]

  (if (or (empty? strs)
          (nil? src))
    false
    (true? (some #(.equals src %) strs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeString "Make a string of certain length"

  ^String
  [^Character ch cnt]

  (let [buf (StringBuilder.) ]
    (dotimes [n cnt]
      (.append buf ch))
    (.toString buf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Right "Gets the rightmost len characters of a String"

  ^String
  [^String src len]

  (if (or (<= len 0)
          (nil? src))
    ""
    (StringUtils/right src (int len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Left "Gets the leftmost len characters of a String"

  ^String
  [^String src len]

  (if (or (<= len 0)
          (nil? src))
    ""
    (StringUtils/left src (int len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Format "Format a string using Java String format syntax"

  [^String fmt & args]

  (String/format fmt (into-array Object args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

