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


(ns

  testcljc.util.fileutils

  (:use [clojure.test])
  (:import (org.apache.commons.io FileUtils))
  (:import (com.zotohlab.frwk.io XData))
  (:import (java.io File))
  (:require [cmzlabsclj.nucleus.util.files :as FU])
  (:require [cmzlabsclj.nucleus.util.core :as CU]))


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

