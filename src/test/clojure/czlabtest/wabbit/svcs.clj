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

  (:use [czlab.wabbit.etc.svcs]
        [czlab.wabbit.etc.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str]
        [clojure.test])

  (:import [czlab.wabbit.etc CmdError]
           [java.io File ]))

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
      (fn [_ _] (do->nil (reset! RESULT 8))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestwabbit-svcs

  (is (let [etype :czlab.wabbit.io.loops/OnceTimer
            m (*emitter-defs* etype)
            c (assoc (:conf m)
                     :delaySecs 1
                     :handler "czlabtest.wabbit.svcs/testHandler")
            ctr (mock :container)
            s (service<> ctr emType "t" c)]
        (.start s)
        (safeWait 2000)
        (.stop s)
        (.dispose ctr)
        (= 8 @RESULT)))

  (is (string? "That's all folks!")))

;;(clojure.test/run-tests 'czlabtest.wabbit.svcs)

