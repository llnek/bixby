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

  czlab.wabbit.etc.cmd2

  (:require [czlab.xlib.format :refer [writeEdnString readEdn]]
            [czlab.xlib.guids :refer [uuid<>]]
            [czlab.xlib.logging :as log]
            [czlab.xlib.antlib :as a]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.sys.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [org.apache.commons.io.filefilter FileFilterUtils]
           [org.apache.commons.io FileUtils]
           [java.util ResourceBundle UUID]
           [czlab.wabbit.etc CmdHelpError]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; some globals
(def ^:dynamic *wabbit-home* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getHomeDir
  ""
  ^File [] (io/file *wabbit-home*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runAppBg
  "Run the application in the background"
  [homeDir appDir]
  (let
    [progW (io/file homeDir "bin/wabbit.bat")
     prog (io/file homeDir "bin/wabbit")
     tk (if (isWindows?)
          (a/antExec
            {:executable "cmd.exe"
             :dir appDir}
            [[:argvalues ["/C" "start" "/B"
                          "/MIN"
                          (fpath progW) "run"]]])
          (a/antExec
            {:executable (fpath prog)
             :dir appDir}
            [[:argvalues ["run" "bg"]]]))]
    (a/runTasks* tk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bundleApp
  "Bundle an app"
  [homeDir appDir outDir]
  (let [dir (mkdirs (io/file outDir))]
    (->>
      (a/antZip
        {:destFile (io/file dir (str (.getName app) ".zip"))
         :basedir (io/file appDir)
         :includes "**/*"})
      (a/runTasks* ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro mkDemoPath "" [dn] `(str "czlab.wabbit.demo." ~dn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljfp "" ^File [cljd fname] (io/file cljd fname))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd
  ""
  {:tag File}
  ([appDir appDomain] (mkcljd appDir appDomain nil))
  ([appDir appDomain dir]
   (io/file appDir
            "src/main"
            (stror dir "clojure")
            (cs/replace appDomain "." "/"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postConfigApp
  ""
  [appDir appId appDomain]
  (let
    [h2db (str (if (isWindows?)
                 "/c:/temp/" "/tmp/") (juid))
     h2dbUrl (str h2db
                  "/"
                  appId
                  ";MVCC=TRUE;AUTO_RECONNECT=TRUE")
     domPath (cs/replace appDomain "." "/")
     hhh (getHomeDir)
     cljd (mkcljd appDir appDomain)]
    (mkdirs h2db)
    (replaceFile!
      (io/file appDir CFG_APP_CF)
      #(-> (cs/replace % "@@H2DBPATH@@" h2dbUrl)
           (cs/replace "@@APPDOMAIN@@" appDomain)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyOneApp
  ""
  [outDir appId appDomain kind]
  (let
    [domPath (cs/replace appDomain "." "/")
     appDir (mkdirs (io/file outDir appId))
     other (if (= :soa kind) :web :soa)
     srcDir (io/file appDir "src")
     mcloj "main/clojure"
     mjava "main/java"
     hhh (getHomeDir)]
    (FileUtils/copyDirectory
      (io/file hhh DN_ETC "app")
      appDir
      (FileFilterUtils/trueFileFilter))
    (when (= :soa kind)
      (doall
        (map #(->> (io/file appDir %)
                   (FileUtils/deleteDirectory ))
             ["src/web" "public"]))
      (->> (io/file appDir DN_CONF "routes.conf")
           (FileUtils/deleteQuietly )))
    (doall
      (map #(mkdirs (io/file appDir
                             "src/main" % domPath))
           ["clojure" "java"]))
    (FileUtils/moveFile
      (io/file srcDir mcloj (str (name kind) ".clj"))
      (io/file srcDir mcloj domPath "core.clj"))
    (FileUtils/deleteQuietly
      (io/file srcDir mcloj (str (name other) ".clj")))
    (FileUtils/moveToDirectory
      (io/file srcDir mjava "HelloWorld.java")
      (io/file srcDir mjava domPath) true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configOneApp
  ""
  [outDir appId appDomain kind]
  (let
    [domPath (cs/replace appDomain "." "/")
     appDir (io/file outDir appId)
     srcDir (io/file appDir "src")
     verStr "0.1.0-SNAPSHOT"
     hhh (getHomeDir)]
    (doseq [f (FileUtils/listFiles srcDir nil true)]
      (replaceFile!
        f
        #(-> (cs/replace % "@@USER@@" (getUser))
             (cs/replace "@@APPDOMAIN@@" appDomain))))
    (replaceFile!
      (io/file appDir CFG_APP_CF)
      #(-> (cs/replace % "@@USER@@" (getUser))
           (cs/replace "@@APPKEY@@" (uuid<>))
           (cs/replace "@@VER@@" verStr)
           (cs/replace "@@APPDOMAIN@@" appDomain)))
    (replaceFile!
      (io/file srcDir "main/resources/pom.xml")
      #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
           (cs/replace "@@VER@@" verStr)
           (cs/replace "@@APPID@@" appId)))
    (replaceFile!
      (io/file appDir "build.boot")
      #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
           (cs/replace "@@TYPE@@" (name kind))
           (cs/replace "@@VER@@" verStr)
           (cs/replace "@@APPID@@" appId)))
    (when (= :web kind)
      (replaceFile!
        (io/file appDir DN_CONF "routes.conf")
        #(cs/replace % "@@APPDOMAIN@@" appDomain)))
    (postConfigApp appDir appId appDomain)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn createApp
  "Create a new app"
  [option path]
  (let
    [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
     kind (keyword (triml option "-"))
     path (strimAny path ".")
     t (re-matches rx path)
     cwd (getCwd)
     ;; treat as domain e.g com.acme => app = acme
     ;; regex gives ["com.acme" ".acme"]
     app (when (some? t)
           (if-some [tkn (last t)]
             (triml tkn ".")
             (first t)))]
    (if (empty? app) (trap! CmdHelpError))
    (case option
      ("-web" "--web") nil
      ("-soa" "--soa") nil
      (trap! CmdHelpError))
    (copyOneApp cwd app path kind)
    (configOneApp cwd app path kind)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo
  ""
  [^File demo ^File out]
  (let [top (io/file out (.getName demo))
        dn (.getName top)
        appDomain (mkDemoPath dn)
        domPath (cs/replace appDomain "." "/")
        kind (if (in? #{"mvc" "http"} dn) :web :soa)]
    (prn!! "Generating: %s..." appDomain)
    (copyOneApp out dn appDomain kind)
    (FileUtils/copyDirectory
      demo
      (io/file top DN_CONF)
      (FileFilterUtils/suffixFileFilter ".conf"))
    (FileUtils/copyDirectory
      demo
      (io/file top "src/main/clojure" domPath)
      (FileFilterUtils/suffixFileFilter ".clj"))
    (configOneApp out
                  dn
                  appDomain kind)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos
  ""
  [outDir]
  (let
    [dss (->> (io/file (getHomeDir)
                       "src/main/clojure"
                       "czlab/wabbit/demo")
              (.listFiles ))]
    (doseq [d dss
            :when (dirRead? d)]
      (genOneCljDemo d outDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn publishSamples
  "Unzip all samples"
  [outDir]
  (genCljDemos (mkdirs outDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


