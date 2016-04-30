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

  testcljc.util.guids

  (:require [czlab.xlib.util.guids :as GU])

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

