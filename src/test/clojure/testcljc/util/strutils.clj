;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns

  testcljc.util.strutils

  (:require [cmzlabclj.xlib.util.str :as SU])

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

