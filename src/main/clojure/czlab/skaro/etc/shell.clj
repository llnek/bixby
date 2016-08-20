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

(ns ^{:doc ""
      :author "Kenneth Leung" }

  czlab.skaro.etc.shell

  (:gen-class)

  (:require
    [czlab.xlib.files :refer [dirRead?]]
    [czlab.xlib.core :refer [sysProp!]]
    [czlab.xlib.str :refer [hgl?]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io])

  (:use [czlab.xlib.resources]
        [czlab.skaro.etc.core])

  (:import
    [java.util ResourceBundle Locale]
    [czlab.xlib I18N]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private VERPROPS "czlab/czlab-skaro/version.properties")
(def ^:private RCB "czlab.skaro.etc/Resources")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main

  "Main Entry"
  [& args]

  (let [^ResourceBundle ver (loadResource VERPROPS)
        rcb (getResource RCB)
        h (first args)]
    (->> (.getString ver "version")
         (sysProp! "skaro.version"))
    (I18N/setBase rcb)
    (if (and (hgl? h)
             (dirRead? (io/file h)))
      (apply bootAndRun h rcb (drop 1 args))
      (usage))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


