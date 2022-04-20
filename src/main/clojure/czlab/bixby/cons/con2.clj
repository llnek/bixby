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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns czlab.bixby.cons.con2

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.bixby.core :as b])

  (:import [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;doing this to get rid reflection warning from stencil
;seems to work
(comment
(binding
  [*warn-on-reflection* false]
  (require '[stencil.core :as sc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;simulate what lein new template does
(defn create-pod

  "Create a new bixby application."
  {:arglists '([name & args])}
  [name & args]

  (c/prn!! "Generating new 'bixby' project...")
  (try (let [dir (c/_2 (drop-while
                         #(c/!eq? "--to-dir" %) args))
             options {:renderer-fn nil ;sc/render-string
                      :force? (some? (c/_1 (drop-while
                                             #(c/!eq? "--force" %) args)))
                      :dir (or dir (-> (u/get-user-dir) (io/file name) .getPath))}]
         ;;(c/prn!! "opts = %s" options)
         ;TODO:
         ;(apply ws/new<> name options args)
         )
       (catch Throwable t
         (c/prn!! "Failed to generate project.\n%s." (u/emsg t)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn publish-samples

  "Generate all samples."
  {:arglists '([outDir])}
  [outDir] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

