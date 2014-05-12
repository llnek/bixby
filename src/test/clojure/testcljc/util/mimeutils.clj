;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.


(ns

  testcljc.util.mimeutils

  (:use [clojure.test])
  (:import (java.io File InputStream))
  (:require [cmzlabsclj.util.core :as CU])
  (:require [cmzlabsclj.util.io :as IO])
  (:require [cmzlabsclj.util.mime :as MU]))


(eval '(MU/SetupCache (CU/ResUrl "com/zotohlabs/frwk/mime/mime.properties")))


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

