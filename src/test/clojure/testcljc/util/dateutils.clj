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

  testcljc.util.dateutils

  (:require [czlab.xlib.util.dates :as DU])

  (:use [clojure.test])

  (:import  [java.util Date]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-dateutils

(is (false? (DU/LeapYear? 1999)))
(is (true? (DU/LeapYear? 2000)))
(is (true? (DU/LeapYear? 2020)))
(is (instance? Date (DU/ParseDate "1999/12/12 13:13:13" "yyyy/MM/dd HH:mm:ss")))
(is (instance? String (DU/FmtDate (Date.) "yyyy/MM/dd HH:mm:ss Z")))


)

(def ^:private dateutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.dateutils)

