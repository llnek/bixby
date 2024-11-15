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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns czlab.bixby.cons.con4

  (:gen-class)

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.bixby.core :as b]
            [czlab.bixby.cons.con1 :as c1])

  (:import [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main

  "Main function."
  [& args]

  (let [[options args] (u/parse-options args)
        home (io/file (or (:home options)
                          (u/get-user-dir)))
        f (io/file home "bixby.pid")]
    (if (.exists f)
      (c1/on-stop args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

