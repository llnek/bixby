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

(ns czlabtest.xlib.coreutils

  (:require [czlab.xlib.core :as CU])

  (:use [clojure.test])

  (:import  [java.util Properties Date Calendar]
            [java.sql Timestamp]
            [czlab.xlib Muble]
            [java.net URL]
            [java.io FileOutputStream File]
            [java.nio.charset Charset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private VAR_USER (System/getProperty "user.name"))
(def ^:private VAR_PATH (System/getenv "PATH"))

(def ^:private dummyResourcePath "czlab/xlib/Resources_en.properties")
(def ^:private dummyProperties (Properties.))
(eval '(do
  (.put ^Properties dummyProperties "1" "hello${user.name}")
  (.put ^Properties dummyProperties "2" "hello${PATH}")
  (.put ^Properties dummyProperties "3" "${user.name}${PATH}")
  (def ^:private dummyPropertiesResult (CU/subsProps dummyProperties))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestxlib-coreutils

  (is (CU/isNichts? CU/NICHTS))
  (is (not (CU/isNichts? "")))
  (is (= (CU/nilNichts nil) CU/NICHTS))
  (is (= (CU/nilNichts "") ""))

  (is (not (CU/matchChar? \space #{ \a \b \x })))
  (is (CU/matchChar? \x #{ \a \b \x }))

  (is (not (nil? (CU/sysVar "java.io.tmpdir"))))
  (is (not (nil? (CU/envVar "PATH"))))

  (is (not (nil? (CU/juid))))
  (is (< (.indexOf (CU/juid) ":\\-") 0))

  (is (not (nil? (CU/newRandom))))

  (is (instance? Timestamp (CU/nowJTstamp)))
  (is (instance? Date (CU/nowDate)))
  (is (instance? Calendar (CU/nowCal)))

  (is (instance? Charset (CU/toCharset "utf-16")))
  (is (instance? Charset (CU/toCharset)))

  (is (= "/c:/temp/abc.txt" (CU/fpath (File. "/c:\\temp\\abc.txt"))))
  (is (= "/c:/temp/abc.txt" (CU/fpath "/c:\\temp\\abc.txt")))

  (is (= (str "hello" VAR_PATH "world" VAR_USER) (CU/subsVar "hello${PATH}world${user.name}")))
  (is (= (str "hello" VAR_PATH) (CU/subsEVar "hello${PATH}")))
  (is (= (str "hello" VAR_USER) (CU/subsSVar "hello${user.name}")))

  (is (= (str VAR_USER VAR_PATH) (.getProperty ^Properties dummyPropertiesResult "3")))
  (is (= (str "hello" VAR_USER) (.getProperty ^Properties dummyPropertiesResult "1")))
  (is (= (str "hello" VAR_PATH) (.getProperty ^Properties dummyPropertiesResult "2")))

  (is (= "Java Virtual Machine Specification" (CU/sysProp "java.vm.specification.name")))

  (is (= "/tmp/a/b/c" (CU/trimLastPathSep  "/tmp/a/b/c////")))
  (is (= "c:\\temp" (CU/trimLastPathSep  "c:\\temp\\\\\\\\")))

  (is (= "heeloo" (CU/deserialize (CU/serialize "heeloo"))))

  (is (= "java.lang.String" (CU/getClassname "")))

;;(is (= "/tmp/a/b/c" (CU/filePath (File. "/tmp/a/b/c"))))
;;(is (true? (CU/isUnix?)))

  (is (= (double 100) (CU/convDouble  "xxxx" 100.0)))
  (is (= 23.23 (CU/convDouble  "23.23" 100.0)))
  (is (= 100 (CU/convLong "xxxx" 100)))
  (is (= 23 (CU/convLong "23" 100)))

  (is (false? (CU/convBool "false")))
  (is (true? (CU/convBool "true")))
  (is (true? (CU/convBool "yes")))
  (is (true? (CU/convBool "1")))
  (is (false? (CU/convBool "no")))
  (is (false? (CU/convBool "0")))

  (is (= 3 (.size
    (let [ fp (File. (str (System/getProperty "java.io.tmpdir") "/" (CU/juid))) ]
      (with-open [ os (FileOutputStream. fp) ] (.store ^Properties dummyProperties os ""))
      (CU/loadJavaProps fp)) )))

  (is (= "heeloo" (CU/stringify (CU/bytesify "heeloo"))))

  (is (instance? (class (byte-array 0))
                 (CU/resBytes dummyResourcePath)))
  (is (> (.length (CU/resStr dummyResourcePath)) 0))
  (is (instance? java.net.URL (CU/resUrl dummyResourcePath)))

  (is (= "heeloo" (CU/stringify (CU/inflate (CU/deflate (CU/bytesify "heeloo"))))))

  (is (= "0x24A0x3cb0x3eZ0x21" (CU/normalize "$A<b>Z!")))

  (is (> (CU/nowMillis) 0))

  (is (= "/tmp/abc.txt" (CU/getFPath "file:/tmp/abc.txt")))

  (is (instance? URL (CU/fmtFileUrl "/tmp/abc.txt")))

  (is (true? (do (CU/test-isa "" (Class/forName "java.lang.Long") (Class/forName "java.lang.Number")) true)))
  (is (true? (do (CU/test-isa "" "" (Class/forName "java.lang.Object")) true)))
  (is (true? (do (CU/test-nonil "" (Object.)) true)))
  (is (true? (do (CU/test-cond "" true) true)))
  (is (true? (do (CU/test-nestr "" "heeloo") true)))

  (is (true? (do (CU/test-nonegnum "" 23.0) true)))
  (is (true? (do (CU/test-nonegnum "" 23) true)))
  (is (true? (do (CU/test-nonegnum "" 0.0) true)))
  (is (true? (do (CU/test-nonegnum "" 0) true)))

  (is (true? (do (CU/test-posnum "" 23.0) true)))
  (is (true? (do (CU/test-posnum "" 23) true)))

  (is (true? (do (CU/test-neseq "" [ 1 2]) true)))

  (is (false? (CU/notnil? nil)))
  (is (true? (CU/notnil? "")))
  (is (= 3 (count (CU/flattenNil '(1 2 nil nil 3)))))
  (is (= 3 (count (CU/flattenNil '(1 2 3)))))
  (is (= 3 (count (CU/flattenNil [1 nil 2 nil 3]))))
  (is (= 0.0 (CU/ndz nil)))
  (is (= 0 (CU/nnz nil)))
  (is (false? (CU/nbf nil)))

  (is (thrown? IllegalArgumentException (CU/throwBadArg "a")))

  (is (true? (let [ x (IllegalArgumentException. "") ] (identical? x (CU/rootCause x)))))

  (is (= "java.lang.IllegalArgumentException: heeloo" (CU/rootCauseMsg (IllegalArgumentException. "heeloo"))))

  (is (= "ACZ" (CU/sortJoin [ "Z" "C" "A"])))
  (is (= 3 (count (CU/genNumbers 1 10 3))))

  (is (false? (nil? (:1 (CU/intoMap dummyProperties)))))
  (is (= 3 (count (CU/intoMap dummyProperties))))

  (is (= 100 (.getv (doto (CU/mubleObj!) (.setv :1 100)) :1)))

)

;;(clojure.test/run-tests 'czlabtest.xlib.coreutils)

