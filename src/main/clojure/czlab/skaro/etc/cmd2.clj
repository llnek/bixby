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

  czlab.skaro.etc.cmd2

  (:require
    [czlab.xlib.format :refer [writeEdnString readEdn]]
    [czlab.xlib.guids :refer [uuid<>]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str
     :refer [strim
             triml
             trimr
             stror
             strimAny
             strbf<>]]
    [czlab.xlib.antlib :as a]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.xlib.core
     :refer [isWindows?
             getUser
             getCwd
             juid
             trap!
             prn!!
             prn!
             fpath]]
    [czlab.xlib.files
     :refer [replaceFile!
             readFile
             spitUTF8
             writeFile
             dirRead?
             touch!
             mkdirs]])

  (:use [czlab.skaro.sys.core])

  (:import
    [org.apache.commons.io.filefilter FileFilterUtils]
    [org.apache.commons.io FileUtils]
    [java.util ResourceBundle UUID]
    [czlab.skaro.etc CmdHelpError]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; some globals
(def ^:dynamic *skaro-home* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getHomeDir "" ^File [] *skaro-home*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runAppBg

  "Run the application in the background"
  [^File hhh ^File appDir]

  (let
    [progW (io/file hhh "bin/skaro.bat")
     prog (io/file hhh "bin/skaro")
     tk (if (isWindows?)
          (a/antExec
            {:executable "cmd.exe"
             :dir appDir}
            [[:argvalues ["/C" "start" "/B"
                          "/MIN"
                          (fpath progW) "start" ]]])
          (a/antExec
            {:executable (fpath prog)
             :dir appDir}
            [[:argvalues [ "start" "bg" ]]])) ]
    (a/runTasks* tk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bundleApp

  "Bundle an app"
  [^File hhh ^File app ^String out]

  (let [dir (mkdirs (io/file out))]
    (->>
      (a/antZip
        {:destFile (io/file dir (str (.getName app) ".zip"))
         :basedir app
         :includes "**/*"})
      (a/runTasks* ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro mkDemoPath "" [dn] `(str "demo." ~dn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljfp "" ^File [cljd fname] (io/file cljd fname))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd

  ""
  ^File
  [appDir appDomain & [dir]]

  (io/file appDir
           "src/main"
           (stror dir "clojure")
           (cs/replace appDomain "." "/")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postCreateApp

  ""
  [^File appDir ^String appId ^String appDomain]

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
(defn- createOneApp

  ""
  [^File out appId ^String appDomain kind]

  (let
    [domPath (cs/replace appDomain "." "/")
     appDir (mkdirs (io/file out appId))
     other (if (= :soa kind) :web :soa)
     srcDir (io/file appDir "src")
     verStr "0.1.0-SNAPSHOT"
     mcloj "main/clojure"
     mjava "main/java"
     hhh (getHomeDir)]
    (FileUtils/copyDirectory
      (io/file hhh DN_ETC "app")
      appDir
      (FileFilterUtils/trueFileFilter))
    ;;defaults to web, so get rid of web stuff
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
      (io/file srcDir mjava domPath) true)
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
      (io/file appDir DN_ETC "pom.xml")
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
    (postCreateApp appDir appId appDomain)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn createApp

  "Create a new app"
  [kind path]

  (let
    [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
     path (strimAny path ".")
     t (re-matches rx path)
     cwd (getCwd)
     ;; treat as domain e.g com.acme => app = acme
     ;; regex gives ["com.acme" ".acme"]
     app (when (some? t)
           (if-some [tkn (last t)]
             (triml tkn ".")
             (first t))) ]
    (when (empty? app) (trap! CmdHelpError))
    (case kind
      ("-web" "--web") nil
      ("-soa" "--soa") nil
      (trap! CmdHelpError))
    (->> (keyword (triml kind "-"))
         (createOneApp cwd app path ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo

  ""
  [^File demo ^File out]

  (let [top (io/file out (.getName demo))
        dn (.getName top)
        dom (mkDemoPath dn)]
    (prn!! "Generating demo[%s]..." dn)
    (createOneApp out dn dom :web)
    (FileUtils/copyDirectory
      demo
      (io/file top DN_CONF)
      (FileFilterUtils/suffixFileFilter ".conf"))
    (FileUtils/copyDirectory
      demo
      (io/file top "src/main/clojure/demo" dn)
      (FileFilterUtils/suffixFileFilter ".clj"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos

  ""
  [^File out]

  (let
    [dss (->> (io/file (getHomeDir)
                       "src/main/clojure/demo")
              (.listFiles ))]
    (doseq [d dss
            :when (dirRead? d)]
      (genOneCljDemo d out))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn publishSamples

  "Unzip all samples"
  [^String output]

  (->> (mkdirs output)
       (genCljDemos )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


