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

  testcljc.util.codes

  (:require [czlab.xlib.util.countries :as CC])
  (:use [clojure.test]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-codes

(is (= (CC/FindCountry "AU") (CC/FindCountry "au")))
(is (= "Australia" (CC/FindCountry "AU")))
(is (= "AU" (CC/FindCountryCode "Australia")))
(is (false? (CC/IsUSA? "aa")))
(is (and (CC/IsUSA? "US") (= (CC/IsUSA? "US") (CC/IsUSA? "us"))))
(is (> (count (CC/ListCodes)) 0))

(is (= (CC/FindState "CA") (CC/FindState "ca")))
(is (= "California" (CC/FindState "ca")))
(is (= "CA" (CC/FindStateCode "California")))
(is (> (count (CC/ListStates)) 0))

)

(def ^:private codes-eof nil)

;;(clojure.test/run-tests 'testcljc.util.codes)

