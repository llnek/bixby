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

(ns czlabtest.wabbit.svcs

  (:require [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlabtest.wabbit.mock]
        [czlab.wabbit.etc.svcs]
        [czlab.wabbit.etc.core]
        [czlab.wabbit.io.core]
        [czlab.flux.wflow.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str]
        [clojure.test])

  (:import [czlab.flux.wflow WorkStream Job]
           [czlab.wabbit.server Container]
           [czlab.wabbit.io FileEvent]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private RESULT (atom 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn testHandler
  ""
  []
  (workStream<>
    (script<>
      (fn [_ _]
        (do->nil
          (swap! RESULT + 8))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fileHandler
  ""
  []
  (workStream<>
    (script<>
      (fn [_ ^Job job]
        (let [^FileEvent e (.event job)
              {:keys [targetFolder recvFolder]}
              (.. e source config)
              tp (fpath targetFolder)
              rp (fpath recvFolder)
              nm (juid)
              f (.file e)
              fp (fpath f)
              s (slurpUtf8 f)
              n (convLong s 0)]
          ;;the file should be in the recv-folder
          (when (>= (.indexOf fp rp) 0)
            ;; generate a new file in target-folder
            (spitUtf8 (io/file tp nm) s)
            (swap! RESULT + n)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestwabbit-svcs

  (is (let [etype :czlab.wabbit.io.loops/OnceTimer
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :delaySecs 1
                     :handler "czlabtest.wabbit.svcs/testHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.start s)
        (safeWait 2000)
        (.stop s)
        (.dispose ctr)
        (== 8 @RESULT)))

  (is (let [etype :czlab.wabbit.io.loops/RepeatingTimer
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :delaySecs 1
                     :intervalSecs 1
                     :handler "czlabtest.wabbit.svcs/testHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (reset! RESULT 0)
        (.start s)
        (safeWait 3500)
        (.stop s)
        (.dispose ctr)
        (> @RESULT 8)))

  (is (let [etype :czlab.wabbit.io.files/FilePicker
            m (*emitter-defs* etype)
            root "/wdrive/tmp";;*TEMPFILE-REPO*
            from (str root "/from")
            to (str root "/to")
            firstfn (str from "/" (juid))
            c (assoc (:conf m)
                     :targetFolder from
                     :recvFolder to
                     :fmask ""
                     :intervalSecs 1
                     :delaySecs 0
                     :handler "czlabtest.wabbit.svcs/fileHandler")
            ^Container
            ctr (mock :container)
            s (service<> ctr etype "t" c)]
        (.init s {})
        (deleteDir from)
        (deleteDir to)
        (mkdirs from)
        (mkdirs to)
        (spitUtf8 firstfn "8")
        (reset! RESULT 0)
        (.start s)
        (safeWait 1000)
        (touch! firstfn)
        (safeWait 4500)
        (.stop s)
        (.dispose ctr)
        (deleteDir from)
        (deleteDir to)
        (> @RESULT 8)))





  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.wabbit.svcs)


