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

  testcljc.util.metautils

  (:use [clojure.test])
  (:require [comzotohlabscljc.util.meta :as MU]))


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

(is (= "" (MU/MakeObj "java.lang.String")))

(is (= 1 (count (MU/ListParents (Class/forName "java.lang.String")))))

(is (> (count (MU/ListMethods (Class/forName "java.lang.String"))) 40))
(is (> (count (MU/ListFields (Class/forName "java.lang.String"))) 5))


)

(def ^:private metautils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.metautils)

