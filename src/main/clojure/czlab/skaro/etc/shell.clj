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
      :author "kenl" }

  czlab.skaro.etc.shell

  (:gen-class)

  (:require
    [czlab.xlib.util.files :refer [DirRead?]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io])

  (:use [czlab.xlib.i18n.resources]
        [czlab.skaro.etc.core])

  (:import
    [java.util ResourceBundle Locale]
    [com.zotohlab.frwk.i18n I18N]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private VERPROPS "com/zotohlab/skaro/version.properties")
(def ^:private RCB "czlab.skaro.etc/Resources")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "Main Entry"

  [& args]

  (let [ver (LoadResource VERPROPS)
        rcb (GetResource RCB)]
    (with-local-vars [ok false]
      (->> (.getString ver "version")
           (System/setProperty "skaro.version"))
      (I18N/setBase rcb)
      (when-some [home (io/file (first args))]
        (when (DirRead? home)
          (var-set ok true)
          (apply BootAndRun home rcb (drop 1 args))))
      (when-not @ok (Usage)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

