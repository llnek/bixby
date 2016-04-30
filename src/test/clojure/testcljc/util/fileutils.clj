;;
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
;;



(ns

  testcljc.util.fileutils

  (:require [czlab.xlib.util.files :as FU]
            [czlab.xlib.util.core :as CU])

  (:use [clojure.test])

  (:import  [org.apache.commons.io FileUtils]
            [com.zotohlab.frwk.io XData]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private TMP_DIR (File. (System/getProperty "java.io.tmpdir")))
(def ^:private TMP_FP (File. ^File TMP_DIR (str (CU/juid) ".txt")))
(eval '(do (FileUtils/writeStringToFile ^File TMP_FP "heeloo")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-fileutils

(is (true? (FU/FileReadWrite? TMP_FP)))
(is (true? (FU/FileRead? TMP_FP)))

(is (true? (FU/DirReadWrite? TMP_DIR)))
(is (true? (FU/DirRead? TMP_DIR)))

(is (false? (FU/CanExec? TMP_FP)))
(is (true? (FU/CanExec? TMP_DIR)))

(is (= "/tmp/a/b" (FU/ParentPath "/tmp/a/b/c")))
(is (nil?  (FU/ParentPath nil)))

(is (= "heeloo" (let [ fp (str (CU/juid) ".txt") ]
                    (FU/SaveFile ^File TMP_DIR fp (FU/GetFile ^File TMP_DIR (.getName ^File TMP_FP)))
                    (FileUtils/readFileToString (File. ^File TMP_DIR fp) "utf-8")) ))


)

(def ^:private fileutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.fileutils)

