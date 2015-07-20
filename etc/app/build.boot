(set-env!

  :buildVersion "0.1.0-SNAPSHOT"
  :buildDebug true
  :buildType "@@TYPE@@"

  :DOMAIN "@@APPDOMAIN@@"
  :PID "@@APPID@@"

  :source-paths #{"src/main/clojure"
                  "src/main/java"}

  :dependencies '[ ] )

(require '[clojure.string :as cs]
         '[clojure.java.io :as io]
         '[boot.core :as bc]
         '[czlabclj.tpcl.boot
           :as b
           :refer :all]
         '[czlabclj.tpcl.antlib :as a])

(import '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/BootEnvVars)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/BootEnvPaths)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; task definitions ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

