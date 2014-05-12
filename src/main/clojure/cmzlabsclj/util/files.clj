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

(ns ^{ :doc "General file related utilities."
       :author "kenl" }

  cmzlabsclj.util.files

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [ cmzlabsclj.util.core :only [notnil?] ])
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.io File FileInputStream FileOutputStream
                    InputStream OutputStream ))
  (:import (java.util ArrayList))
  (:import (org.apache.commons.io IOUtils FileUtils))
  (:import (java.util.zip ZipFile ZipEntry))
  (:import (com.zotohlabs.frwk.io XData)))

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

  (let [ f (File. des (jiggleZipEntryName en) ) ]
    (if (.isDirectory en)
        (.mkdirs f)
        (do
          (.mkdirs (.getParentFile f))
          (with-open [ inp (.getInputStream src en) ]
            (with-open [ os (FileOutputStream. f) ]
              (IOUtils/copy inp os)))
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Unzip "Unzip contents of zip file to a target folder."

  [^File src ^File des]

  (let [ fpz (ZipFile. src)
         ents (.entries fpz) ]
    (.mkdirs des)
    (while (.hasMoreElements ents)
           (doOneEntry fpz des (.nextElement ents)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SaveFile "Save a file to a directory."

  [^File dir ^String fname ^XData xdata]

  (let [ fp (File. dir fname) ]
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

  (let [ fp (File. dir fname)
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

