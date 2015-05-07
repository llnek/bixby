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

(ns ^{:doc "Functions to load and query a .ini file."
      :author "kenl" }

  czlabclj.xlib.util.ini

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core
         :only
         [ThrowBadData ThrowIOE
          ConvBool ConvInt ConvLong ConvDouble]]
        [czlabclj.xlib.util.files :only [FileRead?]]
        [czlabclj.xlib.util.str :only [sname nsb strim]])

  (:import  [com.zotohlab.frwk.util NCOrderedMap]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.frwk.util IWin32Conf]
            [java.net URL]
            [java.io File IOException
             InputStreamReader LineNumberReader PrintStream]
            [java.util Map]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^IWin32Conf ParseInifile "Parse a INI config file." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadIni ""

  [^LineNumberReader rdr]

  (ThrowBadData (str "Bad ini line: " (.getLineNumber rdr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadKey

  [k]

  (ThrowBadData (str "No such property " k ".")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadMap

  [s]

  (ThrowBadData (str "No such section " s ".")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeSection

  ^String
  [^LineNumberReader rdr
   ^Map ncmap
   ^String line]

  (let [s (strim (StringUtils/strip line "[]")) ]
    (when (cstr/blank? s) (throwBadIni rdr))
    (when-not (.containsKey ncmap s)
      (.put ncmap s (NCOrderedMap.)))
    s
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLine

  [^LineNumberReader rdr
   ^Map ncmap
   ^Map section
   ^String line]

  (let [^Map kvs (.get ncmap section) ]
    (when (nil? kvs) (throwBadIni rdr))
    (let [pos (.indexOf line (int \=))
          nm (if (> pos 0)
               (strim (.substring line 0 pos))
               "" ) ]
      (when (cstr/blank? nm) (throwBadIni rdr))
      (.put kvs nm (strim (.substring line (+ pos 1)))) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evalOneLine

  ^String
  [^LineNumberReader rdr
   ^Map ncmap
   ^String line
   ^String curSec]

  (let [ln (strim line) ]
    (cond
      (or (cstr/blank? ln) (.startsWith ln "#"))
      curSec

      (.matches ln "^\\[.*\\]$")
      (maybeSection rdr ncmap ln)

      :else
      (do (maybeLine rdr ncmap curSec ln) curSec))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hasKV ""

  [^Map m k]

  (let [kn (sname k) ]
    (if (or (nil? kn)
            (nil? m))
      nil
      (.containsKey m kn))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getKV ""

  ^String
  [^IWin32Conf cf s k err]

  (let [kn (sname k)
        sn (sname s)
        ^Map mp (.getSection cf sn) ]
    (cond
      (nil? mp) (when err (throwBadMap sn))
      (nil? k) (when err (throwBadKey ""))
      (not (hasKV mp k)) (when err (throwBadKey kn))
      :else (nsb (.get mp kn)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWinini ""

  [^Map sects]

  (reify IWin32Conf

    (sectionKeys [_] (.keySet sects))

    (getSection [_ sect]
      (when-let [sn (sname sect)]
      (when-let [m (.get sects sn)]
        (into {} m))))

    (getString [this section property]
      (nsb (getKV this section property true)))

    (optString [this section property dft]
      (if-let [rc (getKV this section property false) ]
        rc
        dft))

    (getLong [this section property]
      (ConvLong (getKV this section property true) 0))

    (getInt [this section property]
      (ConvInt (getKV this section property true) 0))

    (optLong [this section property dft]
      (if-let [rc (getKV this section property false) ]
        (ConvLong rc 0)
        dft))

    (optInt [this section property dft]
      (if-let [rc (getKV this section property false) ]
        (ConvInt rc 0)
        dft))

    (getDouble [this section property]
      (ConvDouble (getKV this section property true) 0.0))

    (optDouble [this section property dft]
      (if-let [rc (getKV this section property false) ]
        (ConvDouble rc 0.0)
        dft))

    (getBool [this section property]
      (ConvBool (getKV this section property true) false))

    (optBool [this section property dft]
      (if-let [rc (getKV this section property false) ]
        (ConvBool rc false)
        dft))

    (dbgShow [_]
      (let [buf (StringBuilder.)]
        (doseq [[k v] (seq sects)]
          (.append buf (str "[" (sname k) "]\n"))
          (doseq [[x y] (seq v)]
            (.append buf (str (sname x) "=" y)))
          (.append buf "\n"))
        (println buf)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile String

  ^IWin32Conf
  [fpath]

  (when-not (nil? fpath)
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
    (loop [rdr (LineNumberReader. (io/reader inp :encoding "utf-8"))
           total (NCOrderedMap.)
           curSec ""
           line (.readLine rdr)  ]
      (if (nil? line)
        (makeWinini total)
        (recur rdr total
               (evalOneLine rdr total line curSec)
               (.readLine rdr) )
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile URL

  ^IWin32Conf
  [^URL fileUrl]

  (when-not (nil? fileUrl)
    (parseFile fileUrl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ini-eof nil)

