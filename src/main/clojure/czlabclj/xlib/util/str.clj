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

(ns ^{:doc "String utilities."
      :author "kenl" }

  czlabclj.xlib.util.str

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [notnil?]])

  (:import  [java.util Arrays Collection Iterator StringTokenizer]
            [org.apache.commons.lang3 StringUtils]
            [java.io CharArrayWriter File
             OutputStream OutputStreamWriter Reader Writer]
            [java.lang StringBuilder]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro lcase "Lowercase string." [s] `(cstr/lower-case ~s))
(defmacro ucase "Uppercase string." [s] `(cstr/upper-case ~s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasNocase? "Returns true if this sub-string
                 is inside this bigger string."

  [^String bigs s]

  (>= (.indexOf (lcase bigs) (lcase s)) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Embeds? "Returns true if this sub-string
              is inside this bigger string."

  [^String bigs ^String s]

  (>= (.indexOf bigs s) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Has? "Returns true if this character
           is inside this string."

  [^String bigs ^Character ch]

  (>= (.indexOf bigs (int (.charValue ch))) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nsb "Returns empty string if obj is null,
          or obj.toString."

  ^String
  [^Object obj]

  (cond

    (nil? obj) ""

    (keyword? obj) (name obj)

    :else
    (.toString obj)

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToKW "Concatenate all args and return it as a keyword."

  [& args]

  (keyword (cstr/join "/" args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nsn "Returns (null) if obj is null, or obj.toString."

  ^String
  [^Object obj]

  (if (nil? obj) "(null)" (.toString obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Same? "Returns true if these 2 strings are the same."

  [^String a ^String b]

  (cond
    (and (nil? a) (nil? b))
    true

    (or (nil? a) (nil? b))
    false

    (not= (.length a) (.length b))
    false

    :else
    (Arrays/equals (.toCharArray a) (.toCharArray b))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn hgl? "Returns true if this string is not empty."

  [^String s]

  (if (nil? s) false (> (.length s) 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn nichts?  "Returns true if this string is empty."

  [^String s]

  (not (hgl? s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn strim "Safely trim this string - handles null."

  ^String
  [s]

  (if (nil? s) "" (cstr/trim (nsb s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddDelim! "Append to a string-builder, optionally
                inserting a delimiter if the buffer is not empty."

  ^StringBuilder
  [^StringBuilder buf ^String delim ^String item]

  (when-not (nil? item)
    (when (and (> (.length buf) 0) (notnil? delim))
      (.append buf delim))
    (.append buf item))
  buf)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Splunk "Split a large string into chucks,
             each chunk having a specific length."

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
               (.substring src chunkLength))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasicAny? "Tests String.indexOf() against
                a list of possible args. (ignoring case)."

  [^String src substrs]

  (if (or (empty? substrs)
          (nil? src))
    false
    (let [lc (lcase src)]
      (some #(>= (.indexOf lc (lcase %)) 0) substrs))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HasAny? "Returns true if src contains one of these substrings."

  [^String src substrs]

  (if (or (empty? substrs)
          (nil? src))
    false
    (some #(>= (.indexOf src ^String %) 0) substrs)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SWicAny? "Tests startWith() no-case, looping through
               the list of possible prefixes."

  [^String src pfxs]

  (if (or (empty? pfxs)
          (nil? src))
    false
    (let [lc (lcase src)]
      (some #(.startsWith lc (lcase %)) pfxs))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SWAny? "Tests startWith(), looping through
             the list of possible prefixes."

  [^String src pfxs]

  (if (or (empty? pfxs)
          (nil? src))
    false
    (some #(.startsWith src %) pfxs)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EqicAny? "Tests String.equals() against a list of possible args. (ignore-case)."

  [^String src strs]

  (if (or (empty? strs)
          (nil? src))
    false
    (let [lc (lcase src)]
      (some #(.equals lc (lcase %)) strs))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EqAny? "Tests String.equals() against a list of possible args."

  [^String src strs]

  (if (or (empty? strs)
          (nil? src))
    false
    (some #(.equals src %) strs)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeString "Make a string of certain length."

  ^String
  [^Character ch cnt]

  (let [buf (StringBuilder.) ]
    (dotimes [n cnt ]
      (.append buf ch))
    (.toString buf)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Right "Gets the rightmost len characters of a String."

  ^String
  [^String src len]

  (if (or (<= len 0)
          (nil? src))
    ""
    (StringUtils/right src (int len))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Left "Gets the leftmost len characters of a String."

  ^String
  [^String src len]

  (if (or (<= len 0)
          (nil? src))
    ""
    (StringUtils/left src (int len))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Format "Format a string using Java String format syntax."

  [^String fmt & args]

  (String/format fmt (into-array Object args)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private str-eof nil)

