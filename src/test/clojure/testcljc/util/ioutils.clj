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

  testcljc.util.ioutils

  (:require [czlab.xlib.util.core :as CU]
            [czlab.xlib.util.io :as IO])

  (:use [clojure.test])

  (:import  [org.apache.commons.io FileUtils]
            [java.io FileReader File InputStream
                     OutputStream FileOutputStream]
            [com.zotohlab.frwk.io XData XStream]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private TMP_DIR (File. (System/getProperty "java.io.tmpdir")))
(def ^:private TMP_FP (File. ^File TMP_DIR (str (CU/juid) ".txt")))
(eval '(do (FileUtils/writeStringToFile ^File TMP_FP "heeloo" "utf-8")))
;; force to use file
;;(eval '(do (com.zotohlab.frwk.io.IO/setStreamLimit 2)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-ioutils

(is (true? (.exists (IO/TempFile))))

(is (true? (let [ v (IO/OpenTempFile)
                  rc (and (.exists ^File (first v)) (instance? OutputStream (nth v 1))) ]
             (when rc (.close ^OutputStream (nth v 1)))
             rc)))

(is (instance? InputStream (IO/Streamify (byte-array 10))))

(is (instance? OutputStream (IO/ByteOS)))

(is (= "616263" (IO/HexifyString (CU/Bytesify "abc"))))

(is (= "heeloo world!" (CU/Stringify (IO/Gunzip (IO/Gzip (CU/Bytesify "heeloo world!"))))))

(is (true? (do (IO/ResetStream! (IO/Streamify (CU/Bytesify "hello"))) true)))

(is (true? (let [ xs (IO/OpenFile (.getCanonicalPath ^File TMP_FP))
                    rc (instance? XStream xs) ] (.close ^XStream xs) rc)))

(is (true? (let [ xs (IO/OpenFile ^File TMP_FP) rc (instance? XStream xs) ] (.close ^XStream xs) rc)))

(is (= "heeloo world" (CU/Stringify (IO/FromGZB64 (IO/ToGZB64 (CU/Bytesify "heeloo world"))))))

(is (>= (with-open [ ^InputStream inp (IO/OpenFile TMP_FP) ] (IO/Available inp)) 6))

(is (true? (let [ ^File fp (with-open [ ^InputStream inp (IO/OpenFile TMP_FP) ]
                       (IO/CopyStream inp)) ]
             (.exists fp))))

(is (true? (let [ v (IO/TempFile) ]
                (with-open [^InputStream inp (IO/OpenFile TMP_FP) ]
                  (with-open [ os (FileOutputStream. ^File v) ]
                    (IO/CopyBytes inp os 4)))
                (>= (.length ^File v) 4))))

(is (true? (.isDiskFile (IO/MakeXData true))))
(is (false? (.isDiskFile (IO/MakeXData))))

(is (true? (let [ x (with-open [ ^InputStream inp (IO/OpenFile TMP_FP) ] (IO/ReadBytes inp true)) ]
                (and (instance? XData x) (.isDiskFile ^XData x) (> (.size ^XData x) 0))) ))

(is (true? (let [ x (with-open [ ^InputStream inp (IO/OpenFile TMP_FP) ] (IO/ReadBytes inp)) ]
                (and (instance? XData x) (not (.isDiskFile ^XData x)) (> (.size ^XData x) 0))) ))

(is (true? (let [ x (with-open [ rdr (FileReader. ^File TMP_FP) ] (IO/ReadChars rdr true)) ]
                (and (instance? XData x) (.isDiskFile ^XData x) (> (.size ^XData x) 0))) ))

(is (true? (let [ x (with-open [ rdr (FileReader. ^File TMP_FP) ] (IO/ReadChars rdr)) ]
                (and (instance? XData x) (not (.isDiskFile ^XData x)) (> (.size ^XData x) 0))) ))

(is (= "heeloo" (String. (IO/MorphChars (CU/Bytesify "heeloo")))))


)

(def ^:private ioutils-eof nil)

;;(clojure.test/run-tests 'testcljc.util.ioutils)

