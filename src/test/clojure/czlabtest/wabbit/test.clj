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

(ns czlabtest.wabbit.test

  (:require [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.etc.con1]
        [czlab.wabbit.etc.con2]
        [czlab.wabbit.etc.cons]
        [czlab.wabbit.etc.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str]
        [clojure.test])

  (:import [czlab.wabbit.etc CmdError]
           [java.io File ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestwabbit-test

  (is (= "hello"
         (gtid (with-meta {:a 1} {:typeid "hello"}))))

  (is (> (count (expandSysProps "${user.home}")) 0))
  (is (> (count (expandEnvVars "${HOME}")) 0))
  (is (= (str (expandSysProps "${user.home}")
              (expandEnvVars "${HOME}"))
         (expandVars "${user.home}${HOME}")))

  (is (precondDir *TEMPFILE-REPO*))
  (is (let [t (tempFile)
            _ (spitUtf8 t "hello")
            ok (precondFile t)]
        (deleteQ t)
        ok))

  (is (let [m (muble<> {:s "hello.txt"
                        :f (io/file "hello.txt")})]
        (and (inst? File (maybeDir m :s))
             (inst? File (maybeDir m :f)))))

  (is (let [fp (fpath *TEMPFILE-REPO*)
            _ (sysProp! "wabbit.proc.dir" fp)
            t (tempFile)
            _ (spitUtf8 t "${pod.dir}")
            s (readConf t)]
        (deleteQ t)
        (= s fp)))

  (is (let [fp (fpath *TEMPFILE-REPO*)
            tn (juid)
            _ (spitXXXConf fp tn {:a 1})
            m (slurpXXXConf fp tn)]
        (deleteQ (io/file fp tn))
        (and (== 1 (count m))
             (== 1 (:a m)))))

  (is (let [fp (fpath *TEMPFILE-REPO*)
            _ (sysProp! "wabbit.proc.dir" fp)
            tn (juid)
            _ (spitXXXConf fp tn {:a "${pod.dir}"})
            m (slurpXXXConf fp tn true)]
        (deleteQ (io/file fp tn))
        (and (== 1 (count m))
             (string? (:a m))
             (> (count (:a m)) 0))))

  (is (== 17 (-> (with-out-str
                   (onGenerate ["--password" "17"] ))
                 (trimr "\n")
                 count)))
  (is (== 13 (-> (with-out-str
                   (onGenerate ["-p" "13"] ))
                 (trimr "\n")
                 count)))

  (is (> (-> (with-out-str
               (onGenerate ["--hash" "hello"]))
             (trimr "\n")
             count) 0))
  (is (> (-> (with-out-str
               (onGenerate ["-h" "hello"]))
             (trimr "\n")
             count) 0))

  (is (> (-> (with-out-str
               (onGenerate ["--uuid"]))
             (trimr "\n")
             count) 0))
  (is (> (-> (with-out-str
               (onGenerate ["-u"]))
             (trimr "\n")
             count) 0))

  (is (> (-> (with-out-str
               (onGenerate ["--wwid"]))
             (trimr "\n")
             count) 0))
  (is (> (-> (with-out-str
               (onGenerate ["-w"]))
             (trimr "\n")
             count) 0))

  (is (let [e (-> (with-out-str
                    (onGenerate ["--encrypt" "secret" "hello"]))
                  (trimr "\n"))
            d (-> (with-out-str
                    (onGenerate ["--decrypt" "secret" e]))
                  (trimr "\n"))]
        (= d "hello")))

  (is (let [e (-> (with-out-str
                    (onGenerate ["-e" "secret" "hello"]))
                  (trimr "\n"))
            d (-> (with-out-str
                    (onGenerate ["-d" "secret" e]))
                  (trimr "\n"))]
        (= d "hello")))

  (is (thrown? CmdError (onGenerate ["-bbbbb"])))

  (is (let [p (fpath "/private/tmp");;*TEMPFILE-REPO*)
            _ (sysProp! "wabbit.home.dir" (fpath (getCwd)))
            _ (sysProp! "wabbit.proc.dir" p)
            _ (onCreate ["-w" "web"])]
        true))

  (is (let [p (fpath "/private/tmp");;*TEMPFILE-REPO*)
              _ (sysProp! "wabbit.home.dir" (fpath (getCwd)))
              _ (sysProp! "wabbit.proc.dir" p)
              _ (onCreate ["-s" "soa"])]
          true))


  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.wabbit.test)

