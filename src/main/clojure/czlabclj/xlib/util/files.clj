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

(ns ^{:doc "General file related utilities"
      :author "kenl" }

  czlabclj.xlib.util.files

  (:require
    [czlabclj.xlib.util.core :refer [notnil?]]
    [czlabclj.xlib.util.str :refer [nsb]]
    [czlabclj.xlib.util.meta :refer [IsBytes?]])

  (:require
    [czlabclj.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs])

  (:use [czlabclj.xlib.util.io])

  (:import
    [org.apache.commons.io.filefilter
     FileFileFilter FileFilterUtils]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.io FileUtils]
    [java.io File FileFilter
     FileInputStream
     FileOutputStream InputStream OutputStream]
    [java.util ArrayList]
    [java.net URL URI]
    [org.apache.commons.io IOUtils  FileUtils]
    [java.util.zip ZipFile ZipEntry]
    [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FileReadWrite?

  "true if file is readable & writable"

  [^File fp]

  (and (notnil? fp)
       (.exists fp)
       (.isFile fp)
       (.canRead fp)
       (.canWrite fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FileOK?

  "true if file exists"

  [^File fp]

  (and (notnil? fp)
       (.exists fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FileRead?

  "true if file is readable"

  [^File fp]

  (and (notnil? fp)
       (.exists fp)
       (.isFile fp)
       (.canRead fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DirReadWrite?

  "true if directory is readable and writable"

  [^File dir]

  (and (notnil? dir)
       (.exists dir)
       (.isDirectory dir)
       (.canRead dir)
       (.canWrite dir) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DirRead?

  "true if directory is readable"

  [^File dir]

  (and (notnil? dir)
       (.exists dir)
       (.isDirectory dir)
       (.canRead dir) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CanExec?

  "true if file or directory is executable"

  [^File fp]

  (and (notnil? fp)
       (.exists fp)
       (.canExecute fp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParentPath

  "Get the path to the parent directory"

  ^String
  [^String path]

  (if (empty? path)
    path
    (.getParent (File. path))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReplaceFile ""

  [file work]

  {:pre [(fn? work)]}

  (->> (-> (slurp file :encoding "utf-8")
           (work))
       (spit file)))

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

  (let [f (io/file des (jiggleZipEntryName en)) ]
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
(defn Unzip

  "Unzip contents of zip file to a target folder"

  [^File src ^File des]

  (let [fpz (ZipFile. src)
        ents (.entries fpz) ]
    (.mkdirs des)
    (while (.hasMoreElements ents)
      (doOneEntry fpz des (.nextElement ents)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFiles

  "Copy all files with *ext* (no dot) to the destination folder"

  [^File srcDir ^File destDir ext]

  (FileUtils/copyDirectory
    srcDir
    destDir
    (FileFilterUtils/andFileFilter
      FileFileFilter/FILE
      (FileFilterUtils/suffixFileFilter (str "." ext)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFileToDir

  "Copy a file to the target folder"

  [^File fp ^File dir]

  (FileUtils/copyFileToDirectory fp dir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyFile

  "Copy a file"

  [^File fp ^File target]

  (FileUtils/copyFile fp target))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyToDir

  "Copy source folder to be a subfolder of target folder"

  [^File dir ^File targetDir]

  (FileUtils/copyDirectoryToDirectory dir targetDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyDirFiles

  "Copy all contents in source folder to target folder"

  [^File dir ^File targetDir]

  (FileUtils/copyDirectory dir targetDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DeleteDir

  "Erase the folder"

  [^File dir]

  (FileUtils/deleteDirectory dir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanDir

  "Remove contents in this folder"

  [^File dir]

  (FileUtils/cleanDirectory dir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WriteOneFile

  "Write data to file"

  ([^File fout ^Object data ^String enc]
   (if (IsBytes? (class data))
     (FileUtils/writeByteArrayToFile fout ^bytes data)
     (FileUtils/writeStringToFile fout
                                  (nsb data) enc)))

  ([^File fout ^Object data] (WriteOneFile fout data "utf-8")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadFileBytes

  "Read bytes from a file"

  ^bytes
  [^File fp]

  (FileUtils/readFileToByteArray fp))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadOneFile

  "Read data from a file"

  (^String [^File fp] (ReadOneFile fp "utf-8"))

  (^String [^File fp ^String enc] (slurp fp :encoding enc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadOneUrl

  "Read data from a URL"

  (^String [^URL url] (ReadOneUrl url "utf-8"))

  (^String [^URL url ^String enc] (slurp url :encoding enc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SaveFile

  "Save a file to a directory"

  [^File dir ^String fname ^XData xdata]

  ;;(log/debug "saving file: %s" fname)
  (let [fp (io/file dir fname) ]
    (io/delete-file fp true)
    (if-not (.isDiskFile xdata)
      (WriteOneFile fp (.javaBytes xdata))
      (FileUtils/moveFile (.fileRef xdata) fp))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFile

  "Get a file from a directory"

  ^XData
  [^File dir ^String fname]

  ;;(log/debug "getting file: %s" fname)
  (let [fp (io/file dir fname)
        xs (XData.) ]
    (if (FileRead? fp)
      (doto xs
        (.setDeleteFile false)
        (.resetContent fp))
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti Mkdirs "" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod Mkdirs String

  ^File
  [^String f]

  (doto (io/file f) (.mkdirs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod Mkdirs File

  ^File
  [^File f]

  (doto f (.mkdirs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListAnyFiles

  "Look for files with certain extensions, without the dot"

  [dir exts &[recurse?]]

  {:pre [(coll? exts)]}

  (FileUtils/listFiles
    (io/file dir)
    #^"[Ljava.lang.String;"
    (into-array String exts) (some? recurse?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ListFiles

  "Look for files with certain extension, with the dot"

  [dir ext &[recurse?]]

  `(ListAnyFiles ~dir [~ext] ~recurse?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListDirs

  "List sub-directories"

  [dir]

  (->> (reify FileFilter
         (accept [_ f] (.isDirectory f)))
       (.listFiles (io/file dir))
       (into [])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

