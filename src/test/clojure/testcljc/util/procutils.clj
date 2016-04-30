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

  testcljc.util.procutils

  (:require [czlab.xlib.util.core :as CU]
            [czlab.xlib.util.process :as PU])

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

