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

  testcljc.util.strutils

  (:require [czlab.xlib.util.str :as SU])

  (:use [clojure.test]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-strutils

(is (false? (SU/Has? "hallowed are the ori" \z)))
(is (true? (SU/Has? "hallowed are the ori" \w)))

(is (= "heeloo" (SU/nsb "heeloo")))
(is (= "" (SU/nsb nil)))

(is (= "heeloo" (SU/nsn "heeloo")))
(is (= "(null)" (SU/nsn nil)))

(is (false? (SU/Same? "aaa" "axa")))
(is (true? (SU/Same? "aaa" "aaa")))

(is (true? (SU/hgl? "haha")))
(is (false? (SU/hgl? "")))

(is (= "haha" (SU/strim "            haha                          ")))
(is (= "" (SU/strim nil)))

(is (= "joe;blogg" (let [ x (StringBuilder.) ]
                (SU/AddDelim! x ";" "joe")
                (SU/AddDelim! x ";" "blogg")
                (.toString x))))

(is (= 4 (count (SU/Splunk "hello, how are you" 5))))

(is (true? (SU/HasicAny? "hallowed are the ori" [ "sdfsdg" "jffflf" "Are" ])))
(is (false? (SU/HasAny? "hallowed are the ori" [ "sdfsdg" "jffflf" "Are" ])))

(is (true? (SU/SWicAny? "hallowed are the ori" [ "sdfsdg" "jffflf" "Hall" ])))
(is (true? (SU/SWAny? "hallowed are the ori" [ "sdfsdg" "jffflf" "ha" ])))
(is (false? (SU/SWAny? "hallowed are the ori" [ "sdfsdg" "jffflf" ])))

(is (true? (SU/EqicAny? "heeloo" [ "sdfsdg" "jffflf" "HeeLoo" ])))
(is (true? (SU/EqAny? "heeloo" [ "sdfsdg" "jffflf" "heeloo" ])))
(is (false? (SU/EqAny? "heeloo" [ "sdfsdg" "jffflf" ])))

(is (= 10 (.length (SU/MakeString \x 10))))
(is (= "ori" (SU/Right "Hallowed are the ori" 3)))
(is (= "Hal" (SU/Left "Hallowed are the ori" 3)))


)

(def ^:private strutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.strutils)

