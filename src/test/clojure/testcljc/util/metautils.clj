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

  testcljc.util.metautils

  (:require [czlab.xlib.util.meta :as MU])

  (:use [clojure.test]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-metautils

(is (true? (MU/IsChild? (Class/forName "java.lang.Number") (Class/forName "java.lang.Integer"))))
(is (true? (MU/IsChild? (Class/forName "java.lang.Number") (Integer. 3))))
(is (identical? (MU/BytesClass) (class (byte-array 0))))
(is (identical? (MU/CharsClass) (class (char-array 0))))

(is (true? (MU/IsBoolean? (class (boolean true)))))
(is (true? (MU/IsChar? (class (char 3)))))
(is (true? (MU/IsInt? (class (int 3)))))
(is (true? (MU/IsLong? (class (long 3)))))
(is (true? (MU/IsFloat? (class (float 3.2)))))
(is (true? (MU/IsDouble? (class (double 3.2)))))
(is (true? (MU/IsByte? (class (aget (byte-array 1) 0)))))
(is (true? (MU/IsShort? (class (short 3)))))
(is (true? (MU/IsString? (class ""))))
(is (true? (MU/IsBytes? (class (byte-array 0)))))

(is (not (nil? (MU/ForName "java.lang.String"))))
(is (not (nil? (MU/GetCldr))))

(is (true? (do (MU/SetCldr (MU/GetCldr)) true)))

(is (not (nil? (MU/LoadClass "java.lang.String"))))

(is (= "" (MU/NewObj* "java.lang.String")))

(is (= 1 (count (MU/ListParents (Class/forName "java.lang.String")))))

(is (>= (count (MU/ListMethods (Class/forName "java.lang.String"))) 40))
(is (>= (count (MU/ListFields (Class/forName "java.lang.String"))) 5))


)

(def ^:private metautils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.metautils)

