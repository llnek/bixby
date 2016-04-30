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

  testcljc.i18n.i18nstuff

  (:use [czlab.xlib.i18n.resources]
        [czlab.xlib.util.core]
        [clojure.test]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testi18n-i18nstuff

(is (= "hello joe, how is your dawg"
       (let [ rs (LoadResource (ResUrl "com/zotohlab/frwk/i18n/Resources_en.properties")) ]
           (RStr rs "test"  "joe" "dawg" ))))

)

(def ^:private i18nstuff-eof nil)

;;(clojure.test/run-tests 'testcljc.i18n.i18nstuff)

