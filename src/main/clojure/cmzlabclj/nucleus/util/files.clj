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

(ns ^{:doc "General file related utilities."
      :author "kenl" }

  cmzlabclj.nucleus.util.files

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.edn :as edn]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [notnil?] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.nucleus.util.meta :only [IsBytes?] ])
  (:import  [org.apache.commons.lang3 StringUtils]
            [java.io File FileInputStream FileOutputStream
                    InputStream OutputStream]
            [java.util ArrayList]
            [java.net URL URI]
            [org.apache.commons.io IOUtils FileUtils]
            [java.util.zip ZipFile ZipEntry]
            [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FileReadWrite? "Returns true if file is readable & writable."

  [^File fp]

  (and (notnil? fp)
       (.exists fp)
       (.isFile fp)
       (.canRead fp)
       (.canWrite fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FileRead? "Returns true if file is readable."

  [^File fp]

  (and (notnil? fp)
       (.exists fp)
       (.isFile fp)
       (.canRead fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DirReadWrite? "Returns true if directory is readable and writable."

  [^File dir]

  (and (notnil? dir)
       (.exists dir)
       (.isDirectory dir)
       (.canRead dir)
       (.canWrite dir) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DirRead? "Returns true if directory is readable."

  [^File dir]

  (and (notnil? dir)
       (.exists dir)
       (.isDirectory dir)
       (.canRead dir) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CanExec? "Returns true if file or directory is executable."

  [^File fp]

  (and (notnil? fp)
       (.exists fp)
       (.canExecute fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParentPath "Get the path to the parent directory."

  ^String
  [^String path]

  (if (cstr/blank? path)
    path
    (.getParent (File. path))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jiggleZipEntryName ""

  ^String
  [^ZipEntry en]

  (.replaceAll (.getName en) "^[\\/]+",""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOneEntry ""

  [^ZipFile src ^File des ^ZipEntry en]

  (let [f (File. des (jiggleZipEntryName en) ) ]
    (if (.isDirectory en)
      (.mkdirs f)
      (do
        (.mkdirs (.getParentFile f))
        (with-open [inp (.getInputStream src en)
                    os (FileOutputStream. f) ]
          (IOUtils/copy inp os))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Unzip "Unzip contents of zip file to a target folder."

  [^File src ^File des]

  (let [fpz (ZipFile. src)
        ents (.entries fpz) ]
    (.mkdirs des)
    (while (.hasMoreElements ents)
      (doOneEntry fpz des (.nextElement ents)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFileToDir ""

  [^File fp ^File dir]

  (FileUtils/copyFileToDirectory fp dir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFile ""

  [^File fp ^File target]

  (FileUtils/copyFile fp target))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyDir ""

  [^File dir ^File targetDir]

  (FileUtils/copyDirectoryToDirectory dir targetDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WriteOneFile ""

  ([^File fout ^Object data ^String enc]
   (cond
     (nil? data)
     nil

     (IsBytes? data)
     (FileUtils/writeByteArrayToFile fout ^bytes data)

     :else
     (FileUtils/writeStringToFile fout (nsb data) enc)))

  ([^File fout ^Object data] (WriteOneFile fout data "utf-8")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadOneFile ""

  (^String [^File fp] (ReadOneFile fp "utf-8"))

  (^String [^File fp ^String encoding]
    (FileUtils/readFileToString fp encoding)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadOneUrl ""

  (^String [^URL url] (ReadOneUrl url "utf-8"))

  (^String [^URL url ^String encoding]
    (IOUtils/toString url encoding)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WriteEdnString ""

  ^String
  [obj]

  (if-not (nil? obj)
    (pr-str obj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ReadEdn (fn [a] (class a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ReadEdn File

  [^File fp]

  (ReadEdn (.toURL fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ReadEdn String

  [^String s]

  (edn/read-string s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ReadEdn URL

  [^URL url]

  (edn/read-string (ReadOneUrl url)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SaveFile "Save a file to a directory."

  [^File dir ^String fname ^XData xdata]

  (let [fp (File. dir fname) ]
    (log/debug "Saving file: " fp)
    (FileUtils/deleteQuietly fp)
    (if (.isDiskFile xdata)
      (FileUtils/moveFile (.fileRef xdata) fp)
      (FileUtils/writeByteArrayToFile fp (.javaBytes xdata)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFile "Get a file from a directory."

  ^XData
  [^File dir ^String fname]

  (let [fp (File. dir fname)
        xs (XData.) ]
    (log/debug "Getting file: " fp)
    (if (and (.exists fp)
             (.canRead fp))
      (doto xs
        (.setDeleteFile false)
        (.resetContent fp))
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn Mkdirs ""

  ^File
  [^File f]

  (doto f (.mkdirs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private files-eof nil)

