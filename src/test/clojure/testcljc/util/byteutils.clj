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

  testcljc.util.byteutils

  (:require [czlab.xlib.util.io :as BU])
  (:use [clojure.test])
  (:import  [java.nio.charset Charset]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private CS_UTF8 "utf-8")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutils-byteutils

(is (= "heeloo" (String. (BU/ToChars (BU/ToBytes (.toCharArray "heeloo") CS_UTF8) CS_UTF8))))

(is (= 4 (alength ^bytes (BU/WriteBytes (Integer/MAX_VALUE)))))
(is (= 8 (alength ^bytes (BU/WriteBytes (Long/MAX_VALUE)))))

(is (= (Integer/MAX_VALUE) (BU/ReadInt (BU/WriteBytes (Integer/MAX_VALUE)))))
(is (= (Long/MAX_VALUE) (BU/ReadLong (BU/WriteBytes (Long/MAX_VALUE)))))

)

(def ^:private byteutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.byteutils)

