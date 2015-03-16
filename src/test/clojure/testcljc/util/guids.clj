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

  testcljc.util.guids

  (:require [cmzlabclj.nucleus.util.guids :as GU])

  (:use [clojure.test]))

;;(def ^:private UID_2 (GU/new-uuid))
;;(def ^:private UID_1 (GU/new-uuid))
;;(def ^:private WID_2 (GU/new-wwid))
;;(def ^:private WID_1 (GU/new-wwid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-guids

(is (not (= (GU/NewWWid) (GU/NewWWid))))
(is (not (= (GU/NewUUid) (GU/NewUUid))))

(is (> (.length (GU/NewWWid)) 0))
(is (> (.length (GU/NewUUid)) 0))

)

(def ^:private guids-eof nil)

;;(clojure.test/run-tests 'testcljc.util.guids)

