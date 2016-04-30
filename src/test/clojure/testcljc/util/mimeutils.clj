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

  testcljc.util.mimeutils

  (:require [czlab.xlib.util.core :as CU]
            [czlab.xlib.util.io :as IO]
            [czlab.xlib.util.mime :as MU])

  (:use [clojure.test])

  (:import  [java.io File InputStream]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(eval '(MU/SetupCache (CU/ResUrl "com/zotohlab/frwk/mime/mime.properties")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-mimeutils

(is (= "utf-16" (MU/GetCharset "text/plain; charset=utf-16")))

(is (true? (MU/IsSigned? "saljas application/x-pkcs7-mime laslasdf lksalfkla multipart/signed signed-data ")))
(is (true? (MU/IsEncrypted? "saljas laslasdf lksalfkla application/x-pkcs7-mime  enveloped-data ")))
(is (true? (MU/IsCompressed? "saljas laslasdf lksalfkla application/pkcs7-mime compressed-data")))
(is (true? (MU/IsMDN? "saljas laslasdf lksalfkla multipart/report   disposition-notification    ")))

(is (instance? InputStream (MU/MaybeStream (IO/Streamify (CU/Bytesify "hello")))))
(is (instance? InputStream (MU/MaybeStream (CU/Bytesify "hello"))))
(is (instance? InputStream (MU/MaybeStream "hello")))
(is (not (instance? InputStream (MU/MaybeStream 3))))

(is (= "a b" (MU/UrlDecode (MU/UrlEncode "a b"))))

(is (>= (.indexOf (MU/GuessMimeType (File. "/tmp/abc.jpeg")) "image/") 0))
(is (> (.indexOf (MU/GuessContentType (File. "/tmp/abc.pdf")) "/pdf") 0))





)

(def ^:private mimeutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.mimeutils)

