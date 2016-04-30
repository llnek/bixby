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

  testcljc.util.coreutils

  (:require [czlab.xlib.util.core :as CU])

  (:use [clojure.test])

  (:import  [java.util Properties Date Calendar]
            [java.sql Timestamp]
            [com.zotohlab.skaro.core Muble]
            [java.net URL]
            [java.io FileOutputStream File]
            [java.nio.charset Charset]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private VAR_USER (System/getProperty "user.name"))
(def ^:private VAR_PATH (System/getenv "PATH"))

(def ^:private dummyResourcePath "com/zotohlab/frwk/i18n/Resources_en.properties")
(def ^:private dummyProperties (Properties.))
(eval '(do
  (.put ^Properties dummyProperties "1" "hello${user.name}")
  (.put ^Properties dummyProperties "2" "hello${PATH}")
  (.put ^Properties dummyProperties "3" "${user.name}${PATH}")
  (def ^:private dummyPropertiesResult (CU/SubsProps dummyProperties))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-coreutils

(is (CU/IsNichts? CU/NICHTS))
(is (not (CU/IsNichts? "")))
(is (= (CU/NilNichts nil) CU/NICHTS))
(is (= (CU/NilNichts "") ""))

(is (not (CU/MatchChar? \space #{ \a \b \x })))
(is (CU/MatchChar? \x #{ \a \b \x }))

(is (not (nil? (CU/SysVar "java.io.tmpdir"))))
(is (not (nil? (CU/EnvVar "PATH"))))

(is (not (nil? (CU/juid))))
(is (< (.indexOf (CU/juid) ":\\-") 0))

(is (not (nil? (CU/NewRandom))))

(is (instance? Timestamp (CU/NowJTstamp)))
(is (instance? Date (CU/NowDate)))
(is (instance? Calendar (CU/NowCal)))

(is (instance? Charset (CU/ToCharset "utf-16")))
(is (instance? Charset (CU/ToCharset)))

(is (= "/c:/temp/abc.txt" (CU/FPath (File. "/c:\\temp\\abc.txt"))))
(is (= "/c:/temp/abc.txt" (CU/FPath "/c:\\temp\\abc.txt")))

(is (= (str "hello" VAR_PATH "world" VAR_USER) (CU/SubsVar "hello${PATH}world${user.name}")))
(is (= (str "hello" VAR_PATH) (CU/SubsEVar "hello${PATH}")))
(is (= (str "hello" VAR_USER) (CU/SubsSVar "hello${user.name}")))

(is (= (str VAR_USER VAR_PATH) (.getProperty ^Properties dummyPropertiesResult "3")))
(is (= (str "hello" VAR_USER) (.getProperty ^Properties dummyPropertiesResult "1")))
(is (= (str "hello" VAR_PATH) (.getProperty ^Properties dummyPropertiesResult "2")))

(is (= "Java Virtual Machine Specification" (CU/SysProp "java.vm.specification.name")))

(is (= "/tmp/a/b/c" (CU/TrimLastPathSep  "/tmp/a/b/c////")))
(is (= "c:\\temp" (CU/TrimLastPathSep  "c:\\temp\\\\\\\\")))

(is (= "heeloo" (CU/Deserialize (CU/Serialize "heeloo"))))

(is (= "java.lang.String" (CU/GetClassname "")))

;;(is (= "/tmp/a/b/c" (CU/FilePath (File. "/tmp/a/b/c"))))

;;(is (true? (CU/IsUnix?)))

(is (= (double 100) (CU/ConvDouble  "xxxx" 100.0)))
(is (= 23.23 (CU/ConvDouble  "23.23" 100.0)))
(is (= 100 (CU/ConvLong "xxxx" 100)))
(is (= 23 (CU/ConvLong "23" 100)))

(is (true? (CU/ConvBool "true")))
(is (true? (CU/ConvBool "yes")))
(is (true? (CU/ConvBool "1")))
(is (false? (CU/ConvBool "false")))
(is (false? (CU/ConvBool "no")))
(is (false? (CU/ConvBool "0")))

(is (= 3 (.size
  (let [ fp (File. (str (System/getProperty "java.io.tmpdir") "/" (CU/juid))) ]
    (with-open [ os (FileOutputStream. fp) ] (.store ^Properties dummyProperties os ""))
    (CU/LoadJavaProps fp)) )))

(is (= "heeloo" (CU/Stringify (CU/Bytesify "heeloo"))))

(is (instance? (class (byte-array 0)) (CU/ResBytes dummyResourcePath)))
(is (> (.length (CU/ResStr dummyResourcePath)) 0))
(is (instance? java.net.URL (CU/ResUrl dummyResourcePath)))

(is (= "heeloo" (CU/Stringify (CU/Inflate (CU/Deflate (CU/Bytesify "heeloo"))))))

(is (= "0x24A0x3cb0x3eZ0x21" (CU/Normalize "$A<b>Z!")))

(is (> (CU/NowMillis) 0))

(is (= "/tmp/abc.txt" (CU/GetFPath "file:/tmp/abc.txt")))

(is (instance? URL (CU/FmtFileUrl "/tmp/abc.txt")))


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

(is (true? (do (CU/test-neseq "" [ 1 2 ]) true)))

(is (false? (CU/notnil? nil)))
(is (true? (CU/notnil? "")))
(is (= 3 (count (CU/FlattenNil '(1 2 nil nil 3)))))
(is (= 3 (count (CU/FlattenNil '(1 2 3)))))
(is (= 3 (count (CU/FlattenNil [1 nil 2 nil 3]))))
(is (= 0.0 (CU/ndz nil)))
(is (= 0 (CU/nnz nil)))
(is (false? (CU/nbf nil)))

(is (thrown? IllegalArgumentException (CU/ThrowBadArg "a")))

(is (true? (let [ x (IllegalArgumentException. "") ] (identical? x (CU/RootCause x)))))

(is (= "java.lang.IllegalArgumentException: heeloo" (CU/RootCauseMsg (IllegalArgumentException. "heeloo"))))

(is (= 3 (count (CU/GenNumbers 1 10 3))))

(is (= "ACZ" (CU/SortJoin [ "Z" "C" "A"])))

(is (false? (nil? (:1 (CU/IntoMap dummyProperties)))))
(is (= 3 (count (CU/IntoMap dummyProperties))))

(is (= 100 (.getv (doto (CU/MubleObj!) (.setv :1 100)) :1)))


)

(def ^:private coreutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.coreutils)

