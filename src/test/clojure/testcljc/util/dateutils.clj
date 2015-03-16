;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Ken Leung. All rights reserved.


(ns

  testcljc.util.dateutils

  (:require [cmzlabclj.nucleus.util.dates :as DU])

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

