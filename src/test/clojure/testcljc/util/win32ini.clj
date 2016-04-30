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

  testcljc.util.win32ini

  (:require
    [czlab.xlib.util.core :as CU]
    [czlab.xlib.util.ini :as WI])

  (:use [clojure.test])

  (:import
    [com.zotohlab.frwk.util IWin32Conf]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^IWin32Conf ^:private INIFILE (WI/ParseInifile (CU/ResUrl "com/zotohlab/frwk/util/sample.ini")))

;;(println "->>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
;;(.dbgShow INIFILE)
;;(println "-<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-wi32ini

(is (= (count (.sectionKeys INIFILE)) 2))

(is (map? (.getSection INIFILE "operating systems")))
(is (map? (.getSection INIFILE "boot loader")))

(is (true? (.endsWith (.getString INIFILE "boot loader" "default") "WINDOWS")))

(is (true? (= (.getLong INIFILE "boot loader" "timeout") 30)))



)

(def ^:private win32ini-eof nil)

;;(clojure.test/run-tests 'testcljc.util.win32ini)

