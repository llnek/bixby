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

(ns ^{:doc "Functions to load and query a .ini file"
      :author "kenl" }

  czlab.xlib.util.ini

  (:require
    [czlab.xlib.util.core
     :refer [ThrowBadData ThrowIOE
             ConvBool ConvInt ConvLong ConvDouble]]
    [czlab.xlib.util.files :refer [FileRead?]]
    [czlab.xlib.util.str :refer [nsb strim lcase]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs])

  (:use [flatland.ordered.map])

  (:import
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.frwk.util IWin32Conf]
    [java.net URL]
    [java.io File IOException
     InputStreamReader LineNumberReader PrintStream]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^IWin32Conf ParseInifile "Parse a INI config file" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadIni ""

  [^LineNumberReader rdr]

  (ThrowBadData (str "Bad ini line: " (.getLineNumber rdr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadKey

  [k]

  (ThrowBadData (str "No such property " k)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadMap

  [s]

  (ThrowBadData (str "No such section " s )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeSection

  [^LineNumberReader rdr
   ncmap
   ^String line]

  (let [s (strim (StringUtils/strip line "[]")) ]
    (when (empty? s) (throwBadIni rdr))
    (let [k (keyword (lcase s))]
      (when-not (contains? @ncmap k)
        (->> (assoc @ncmap k (with-meta (sorted-map) {:name s}))
             (reset! ncmap)))
      k)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLine

  [^LineNumberReader rdr
   ncmap
   section
   ^String line]

  (let [kvs (get @ncmap section) ]
    (when (nil? kvs) (throwBadIni rdr))
    (let [pos (.indexOf line (int \=))
          nm (if (> pos 0)
               (strim (.substring line 0 pos))
               "" ) ]
      (when (empty? nm) (throwBadIni rdr))
      (let [k (keyword (lcase nm))]
        (->> (assoc kvs k [ nm  (strim (.substring line (+ pos 1))) ])
             (swap! ncmap assoc section)))
      section)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evalOneLine

  [^LineNumberReader rdr
   ncmap
   curSec
   ^String line]

  (let [ln (strim line) ]
    (cond
      (or (empty? ln)
          (.startsWith ln "#"))
      curSec

      (.matches ln "^\\[.*\\]$")
      (maybeSection rdr ncmap ln)

      :else
      (maybeLine rdr ncmap curSec ln))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getKV ""

  ^String
  [sects s k err]

  (let [sn (keyword (lcase s))
        kn (keyword (lcase k))
        mp (get sects sn)]
    (cond
      (nil? mp) (when err (throwBadMap s))
      (nil? k) (when err (throwBadKey k))
      (not (contains? mp kn)) (when err (throwBadKey k))
      :else (nsb (last (get mp kn))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWinini ""

  [sects]

  (reify IWin32Conf

    (sectionKeys [_]
      (reduce
        #(conj %1 (:name (meta %2)))
        #{}
        (vals sects)))

    (getSection [_ sect]
      (let [sn (keyword (lcase sect))]
        (reduce #(assoc %1 (first %2) (last %2))
                (sorted-map)
                (or (vals (get sects sn)) []))))

    (getString [this section property]
      (nsb (getKV sects section property true)))

    (getString [this section property dft]
      (if-some [rc (getKV sects section property false) ]
        rc
        dft))

    (getLong [this section property dft]
      (if-some [rc (getKV sects section property false) ]
        (ConvLong rc)
        dft))

    (getLong [this section property]
      (ConvLong (getKV sects section property true) 0))

    (getInt [this section property dft]
      (if-some [rc (getKV sects section property false) ]
        (ConvInt rc dft)
        dft))

    (getInt [this section property]
      (ConvInt (getKV sects section property true) ))

    (getDouble [this section property dft]
      (if-some [rc (getKV sects section property false) ]
        (ConvDouble rc dft)
        dft))

    (getDouble [this section property]
      (ConvDouble (getKV sects section property true) ))

    (getBool [this section property dft]
      (if-some [rc (getKV sects section property false) ]
        (ConvBool rc dft)
        dft))

    (getBool [this section property]
      (ConvBool (getKV sects section property true) ))

    (dbgShow [_]
      (let [buf (StringBuilder.)]
        (doseq [v (vals sects)]
          (.append buf (str "[" (:name (meta v)) "]\n"))
          (doseq [[x y] (vals v)]
            (.append buf (str x "=" y "\n")))
          (.append buf "\n"))
        (println (.toString buf))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile String

  ^IWin32Conf
  [fpath]

  (when (some? fpath)
    (ParseInifile (io/file fpath))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile File

  ^IWin32Conf
  [file]

  (when (FileRead? file)
    (ParseInifile (io/as-url file))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseFile

  ^IWin32Conf
  [^URL fUrl]

  (with-open [inp (.openStream fUrl) ]
    (loop [rdr (->> (io/reader inp :encoding "utf-8")
                    (LineNumberReader. ))
           total (atom (sorted-map))
           line (.readLine rdr)
           curSec nil]
      (if (nil? line)
        (makeWinini @total)
        (recur rdr
               total
               (.readLine rdr)
               (evalOneLine rdr total curSec line))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile URL

  ^IWin32Conf
  [^URL fileUrl]

  (when (some? fileUrl)
    (parseFile fileUrl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

