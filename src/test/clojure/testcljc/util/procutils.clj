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


(ns

  testcljc.util.procutils

  (:require [czlabclj.xlib.util.core :as CU]
            [czlabclj.xlib.util.process :as PU])

  (:use [clojure.test])

  (:import  [org.apache.commons.io FileUtils]
            [java.io File]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private CUR_MS (System/currentTimeMillis))
(def ^:private CUR_FP (File. (str (System/getProperty "java.io.tmpdir") "/" CUR_MS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-procutils

(is (true? (do
              (PU/Coroutine (fn [] (FileUtils/writeStringToFile ^File CUR_FP "heeloo" "utf-8")))
              (PU/SafeWait 3500)
              (and (.exists ^File CUR_FP) (>= (.length ^File CUR_FP) 6)))))

(is (> (.length (PU/ProcessPid)) 0))


)

(def ^:private procutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.procutils)

