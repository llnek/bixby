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
    [czlab.xlib.str :refer [strim triml trimr strimAny strbf<>]]
    [czlab.xlib.guids :refer [uuid<>]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.xlib.antlib :as a]
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
(def ^:dynamic *skaro-rb* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn resBdl

  "Return the system resource bundle"
  ^ResourceBundle
  []

  *skaro-rb*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getHomeDir

  "Return the home directory"
  ^File
  []

  *skaro-home*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare createOneApp)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain

  ""
  [appDomain]

  (strimAny appDomain "."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn createApp

  "Create a new app"
  [path]

  (let
    [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
     t (re-matches rx path)
     cwd (getCwd)
     ;; treat as domain e.g com.acme => app = acme
     ;; regex gives ["com.acme" ".acme"]
     app (when (some? t)
           (if-some [tkn (last t)]
             (triml tkn ".")
             (first t))) ]
    (when (empty? app) (trap! CmdHelpError))
    (createOneApp cwd app path)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runAppBg

  "Run the application in the background"
  [^File hhh]

  (let
    [progW (io/file hhh "bin/skaro.bat")
     prog (io/file hhh "bin/skaro")
     cwd (getCwd)
     tk (if (isWindows?)
          (a/antExec
            {:executable "cmd.exe"
             :dir cwd}
            [[:argvalues ["/C" "start" "/B"
                          "/MIN"
                          (fpath progW) "start" ]]])
          (a/antExec
            {:executable (fpath prog)
             :dir cwd}
            [[:argvalues [ "start" "bg" ]]])) ]
    (a/runTasks* tk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bundleApp

  "Bundle an app"
  [^File hhh ^File app ^String out]

  (let [dir (mkdirs (io/file out)) ]
    (->>
      (a/antZip
        {:destFile (io/file dir (str (.getName app) ".zip"))
         :basedir app
         :includes "**/*"})
      (a/runTasks* ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro mkDemoPath "" [dn] `(str "demo." ~dn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo

  ""
  [^File demo ^File out]

  (let [top (io/file out (.getName demo))
        dn (.getName top)
        dom (mkDemoPath dn)]
    (prn!! "Generating demo[%s]..." dn)
    (createOneApp out dn dom)
    (FileUtils/copyDirectory
               demo
               (io/file top DN_CONF)
               (FileFilterUtils/suffixFileFilter ".conf"))
    (FileUtils/copyDirectory
               demo
               (io/file top
                        "src/main/clojure/demo" dn)
               (FileFilterUtils/suffixFileFilter ".clj"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos

  ""
  [^File out]

  (let [dss (->> (io/file (getHomeDir)
                          "src/main/clojure/demo")
                 (.listFiles )) ]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljfp

  ""
  ^File
  [cljd fname]

  (io/file cljd fname))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd

  ""
  ^File
  [appDir appDomain]

  (io/file appDir
           "src/main/clojure"
           (cs/replace appDomain "." "/")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postCreateApp

  ""
  [^File appDir ^String appId ^String appDomain]

  (let [h2db (str (if (isWindows?)
                    "/c:/temp/"
                    "/tmp/")
                  (juid))
        h2dbUrl (str h2db
                     "/"
                     appId
                     ";MVCC=TRUE;AUTO_RECONNECT=TRUE")
        appDomainPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        cljd (mkcljd appDir appDomain) ]
    (mkdirs h2db)
    (with-local-vars [fp nil]
      (var-set fp (mkcljfp cljd "core.clj"))
      (replaceFile! @fp
                    #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
                         (cs/replace "@@USER@@" (getUser))))

      (var-set fp (io/file appDir CFG_APP_CF))
      (replaceFile! @fp
                    #(-> (cs/replace % "@@H2DBPATH@@" h2dbUrl)
                         (cs/replace "@@APPDOMAIN@@" appDomain))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createAppCommon

  ""
  [^File appDir ^String appId ^String appDomain ^String flavor]

  (let [appDomainPath (cs/replace appDomain "." "/")
        mfDir (io/file appDir DN_ETC)
        cfd (io/file appDir DN_CONF)
        hhh (getHomeDir)]
    (with-local-vars [fp nil]
      ;; make all the folders
      (doseq [^String s ["i18n" "modules" "logs"
                         DN_CONF DN_ETC
                         "src" "build" "patch" "target"]]
        (mkdirs (io/file appDir s)))
      (doseq [s [ "clojure" "java"]]
        (mkdirs (io/file appDir "src/main" s appDomainPath))
        (mkdirs (io/file appDir "src/test" s appDomainPath)))

      (mkdirs (io/file appDir "src/main/resources"))
      (mkdirs (io/file appDir "src/main/artifacts"))

      ;;copy files

      (FileUtils/copyFile
        (io/file hhh DN_ETC "log/log4jbuild.properties")
        (io/file appDir "src/main/artifacts/log4j.properties"))
      (FileUtils/copyFileToDirectory
        (io/file hhh DN_CFGAPP "test.clj")
        (io/file appDir "src/test/clojure" appDomainPath))

      (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
        (FileUtils/copyFileToDirectory
          (io/file hhh DN_CFGAPP s)
          (io/file appDir "src/test/java" appDomainPath)))

      (doseq [s ["build.boot" "pom.xml"]]
        (FileUtils/copyFileToDirectory
          (io/file hhh DN_CFGAPP s) appDir))
      (touch! (io/file appDir "README.md"))

      (doseq [s ["RELEASE-NOTES.txt" "NOTES.txt"
                 "LICENSE.txt"]]
        (touch! (io/file appDir DN_ETC s)))
      (FileUtils/copyFileToDirectory
          (io/file hhh DN_CFGAPP APP_CF) cfd)
      (FileUtils/copyFileToDirectory
        (io/file hhh DN_CFGAPP DN_RCPROPS)
        (io/file  appDir "i18n"))
      (FileUtils/copyFileToDirectory
        (io/file hhh DN_CFGAPP "core.clj")
        (mkcljd appDir appDomain))

      ;;modify files, replace placeholders
      (var-set fp (io/file appDir
                           "src/test/clojure"
                           appDomainPath "test.clj"))
      (replaceFile! @fp
                    #(cs/replace % "@@APPDOMAIN@@" appDomain))

      (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
        (var-set fp (io/file appDir
                             "src/test/java" appDomainPath s))
        (replaceFile! @fp
                      #(cs/replace % "@@APPDOMAIN@@" appDomain)))

      (var-set fp (io/file cfd APP_CF))
      (replaceFile! @fp
                    #(-> (cs/replace % "@@USER@@" (getUser))
                         (cs/replace "@@APPKEY@@" (uuid<>))
                         (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
                         (cs/replace "@@APPMAINCLASS@@"
                                     (str appDomain ".core/MyAppMain"))))

      (var-set fp (io/file appDir "pom.xml"))
      (replaceFile! @fp
                    #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
                         (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
                         (cs/replace "@@APPID@@" appId)))

      (var-set fp (io/file appDir "build.boot"))
      (replaceFile! @fp
                    #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
                         (cs/replace "@@TYPE@@" flavor)
                         (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
                         (cs/replace "@@APPID@@" appId))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createWebCommon

  ""
  [^File appDir appId ^String appDomain]

  (let [appDomainPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        buf (strbf<>)]

    ;; make folders
    (doseq [s ["pages" "media" "styles" "scripts"]]
      (mkdirs (io/file appDir "src/web/main" s))
      (mkdirs (io/file appDir "public" s)))

    ;; copy files
    (let [des (io/file appDir "src/web/main/pages")
          src (io/file hhh DN_ETC "netty")]
      (FileUtils/copyDirectory
        src des
        (FileFilterUtils/suffixFileFilter ".ftl"))
      (FileUtils/copyDirectory
        src des
        (FileFilterUtils/suffixFileFilter ".html")))

    (FileUtils/copyFileToDirectory
      (io/file hhh DN_ETC "netty/core.clj")
      (mkcljd appDir appDomain))

    (FileUtils/copyFileToDirectory
      (io/file hhh DN_CFGWEB "main.scss")
      (io/file appDir "src/web/main/styles"))
    (FileUtils/copyFileToDirectory
      (io/file hhh DN_CFGWEB "main.js")
      (io/file appDir "src/web/main/scripts"))

    (FileUtils/copyFileToDirectory
      (io/file hhh DN_CFGWEB "favicon.png")
      (io/file appDir "src/web/main/media"))
    (FileUtils/copyFileToDirectory
      (io/file hhh DN_CFGWEB "body.jpg")
      (io/file appDir "src/web/main/media"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createMvc

  ""
  [^File appDir ^String appId ^String appDomain ]

  (let [appDomainPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        cfd (io/file appDir DN_CONF) ]
    (with-local-vars [fp nil]
      (createAppCommon appDir appId appDomain "web")
      (createWebCommon appDir appId appDomain)
      ;; copy files
      (FileUtils/copyDirectory
        (io/file hhh DN_ETC "netty")
        cfd
        (FileFilterUtils/suffixFileFilter ".conf"))
      ;; modify files
      (var-set fp (io/file cfd "routes.conf"))
      (replaceFile! @fp
                    #(cs/replace % "@@APPDOMAIN@@" appDomain))

      (postCreateApp appDir appId appDomain))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createBasic

  ""
  [^File out appId ^String appDomain]

  (doto (mkdirs (io/file out appId))
    (createAppCommon appId appDomain "basic")
    (postCreateApp appId appDomain)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createOneApp

  ""
  [^File out appId ^String appDomain]

  (-> (mkdirs (io/file out appId))
      (createMvc appId appDomain)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

