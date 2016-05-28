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

  czlab.skaro.etc.cmd2

  (:require
    [czlab.xlib.str :refer [strim triml trimr]]
    [czlab.xlib.ini :refer [parseInifile]]
    [czlab.xlib.guids :refer [newUUid]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.xlib.antlib :as a]
    [czlab.xlib.core
     :refer [getUser getCwd juid
             trap!
             isWindows?
             prn!! prn! fpath]]
    [czlab.xlib.files
     :refer [readOneFile
             writeOneFile
             copyFileToDir
             replaceFile
             deleteDir
             copyFile
             copyToDir
             copyFiles
             mkdirs]])

  (:use [czlab.skaro.core.consts])

  (:import
    [org.apache.commons.io FilenameUtils FileUtils]
    [org.apache.commons.io.filefilter
     FileFileFilter
     FileFilterUtils]
    [java.util ResourceBundle UUID]
    [czlab.skaro.etc CmdHelpError]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; some globals
(defonce SKARO-PROPS (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn setGlobals! "" [k v] (swap! SKARO-PROPS assoc k v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn resBdl

  "Return the system resource bundle"

  ^ResourceBundle
  []

  (:rcb @SKARO-PROPS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getHomeDir

  "Return the home directory"

  ^File
  []

  (:homeDir @SKARO-PROPS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare createBasic createNetty createJetty)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain ""

  [appDomain]

  (-> (str appDomain)
      (triml ".")
      (trimr ".")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn createApp

  "Create a new app"

  [verb path]

  (let [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
        t (re-matches rx path)
        cwd (getCwd)
        ;; treat as domain e.g com.acme => app = acme
        ;; regex gives ["com.acme" ".acme"]
        app (when (some? t)
              (if-some [tkn (last t) ]
                (triml tkn ".")
                (first t))) ]
    (when (empty? app) (trap! CmdHelpError))
    (case verb
      ("mvc" "web")
      (createNetty cwd app path)
      "jetty"
      (createJetty cwd app path)
      "basic"
      (createBasic cwd app path)
      (trap! CmdHelpError))))

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
     (->> (a/antZip
            {:destFile (io/file dir (str (.getName app) ".zip"))
             :basedir app
             :includes "**/*"})
          (a/runTasks* ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro mkDemoPath "" [dn] `(str "demo." ~dn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneJavaDemo ""

  [^File demo ^File out]

  (let [top (io/file out (.getName demo))
        dn (.getName top)]
    (prn!! "Generating demo[%s]..." dn)
    (createBasic out dn (mkDemoPath dn))
    (copyFiles demo
               (io/file top DN_CONF) "conf")
    (copyFiles demo
               (io/file top
                        "src/main/java/demo" dn)
               "java")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo ""

  [^File demo ^File out]

  (let [top (io/file out (.getName demo))
        dn (.getName top)
        dom (mkDemoPath dn)]
    (prn!! "Generating demo[%s]..." dn)
    (case dn
      "jetty" (createJetty out dn dom)
      "mvc" (createNetty out dn dom)
      (createBasic out dn dom))
    (copyFiles demo
               (io/file top DN_CONF) "conf")
    (copyFiles demo
               (io/file top
                        "src/main/clojure/demo" dn)
               "clj")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genJavaDemos ""

  [^File out]

  (let [dss (->> (io/file (getHomeDir)
                          "src/main/java/demo")
                 (.listFiles ))]
    (doseq [^File d dss
            :when (.isDirectory d)]
      (genOneJavaDemo d out))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos ""

  [^File out]

  (let [dss (->> (io/file (getHomeDir)
                          "src/main/clojure/demo")
                 (.listFiles )) ]
    (doseq [^File d dss
            :when (.isDirectory d)]
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
(defn- mkcljfp ""

  ^File
  [^File cljd ^String fname]

  (io/file cljd fname))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd ""

  ^File
  [^File appDir ^String appDomain]

  (io/file appDir
           "src/main/clojure"
           (cs/replace appDomain "." "/")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postCreateApp ""

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
      (replaceFile @fp
                   #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
                        (cs/replace "@@USER@@" (getUser))))

      (var-set fp (io/file appDir CFG_ENV_CF))
      (replaceFile @fp
                   #(-> (cs/replace % "@@H2DBPATH@@" h2dbUrl)
                        (cs/replace "@@APPDOMAIN@@" appDomain))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createAppCommon ""

  [^File appDir ^String appId ^String appDomain ^String flavor]

  (let [appDomainPath (cs/replace appDomain "." "/")
        mfDir (io/file appDir DN_CFG)
        cfd (io/file appDir DN_CONF)
        hhh (getHomeDir)]
    (with-local-vars [fp nil]
      ;; make all the folders
      (doseq [^String s ["i18n" "modules" "logs"
                         DN_CONF DN_CFG
                         "src" "build" "patch" "target"]]
        (mkdirs (io/file appDir s)))
      (doseq [s [ "clojure" "java"]]
        (mkdirs (io/file appDir "src/main" s appDomainPath))
        (mkdirs (io/file appDir "src/test" s appDomainPath)))

      (mkdirs (io/file appDir "src/main/resources"))
      (mkdirs (io/file appDir "src/main/artifacts"))

      ;;copy files

      (copyFile (io/file hhh DN_CFG "log/log4jbuild.properties")
                (io/file appDir "src/main/artifacts/log4j.properties"))
      (copyFile (io/file hhh DN_CFG "log/logback4build.xml")
                (io/file appDir "src/main/artifacts/logback.xml"))

      (copyFileToDir (io/file hhh DN_CFGAPP "test.clj")
                     (io/file appDir "src/test/clojure" appDomainPath))

      (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
        (copyFileToDir (io/file hhh DN_CFGAPP s)
                       (io/file appDir "src/test/java" appDomainPath)))

      (doseq [s ["build.boot" "pom.xml"]]
        (copyFileToDir (io/file hhh DN_CFGAPP s) appDir))
      (FileUtils/touch (io/file appDir "README.md"))

      (doseq [s ["RELEASE-NOTES.txt" "NOTES.txt"
                 "LICENSE.txt"]]
        (FileUtils/touch (io/file appDir DN_CFG s)))
      (doseq [s [APP_CF ENV_CF ]]
        (copyFileToDir (io/file hhh DN_CFGAPP s) cfd))
      (copyFileToDir (io/file hhh DN_CFGAPP DN_RCPROPS)
                     (io/file  appDir "i18n"))
      (copyFileToDir (io/file hhh DN_CFGAPP "core.clj")
                     (mkcljd appDir appDomain))

      ;;modify files, replace placeholders
      (var-set fp (io/file appDir
                           "src/test/clojure" appDomainPath "test.clj"))
      (replaceFile @fp
                   #(cs/replace % "@@APPDOMAIN@@" appDomain))

      (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
        (var-set fp (io/file appDir
                             "src/test/java" appDomainPath s))
        (replaceFile @fp
                     #(cs/replace % "@@APPDOMAIN@@" appDomain)))

      (var-set fp (io/file cfd APP_CF))
      (replaceFile @fp
                   #(-> (cs/replace % "@@USER@@" (getUser))
                        (cs/replace "@@APPKEY@@" (newUUid))
                        (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
                        (cs/replace "@@APPMAINCLASS@@"
                                    (str appDomain ".core/MyAppMain"))))

      (var-set fp (io/file appDir "pom.xml"))
      (replaceFile @fp
                   #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
                        (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
                        (cs/replace "@@APPID@@" appId)))

      (var-set fp (io/file appDir "build.boot"))
      (replaceFile @fp
                   #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
                        (cs/replace "@@TYPE@@" flavor)
                        (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
                        (cs/replace "@@APPID@@" appId))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createWebCommon ""

  [^File appDir appId ^String appDomain]

  (let [appDomainPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        wfc (io/file hhh DN_CFGAPP "weblibs.conf")
        wlib (io/file appDir "public/vendors")
        wbs (readEdn wfc)
        buf (StringBuilder.)]

    ;; make folders
    (doseq [s ["pages" "media" "styles" "scripts"]]
      (mkdirs (io/file appDir "src/web/main" s))
      (mkdirs (io/file appDir "public" s)))
    (mkdirs wlib)

    ;; copy files
    (let [des (io/file appDir "src/web/main/pages")
          src (io/file hhh DN_CFG "netty")]
      (copyFiles src des "ftl")
      (copyFiles src des "html"))

    (copyFileToDir (io/file hhh DN_CFG "netty/core.clj")
                   (mkcljd appDir appDomain))

    (copyFileToDir (io/file hhh DN_CFGWEB "main.scss")
                   (io/file appDir "src/web/main/styles"))
    (copyFileToDir (io/file hhh DN_CFGWEB "main.js")
                   (io/file appDir "src/web/main/scripts"))

    (copyFileToDir (io/file hhh DN_CFGWEB "favicon.png")
                   (io/file appDir "src/web/main/media"))
    (copyFileToDir (io/file hhh DN_CFGWEB "body.jpg")
                   (io/file appDir "src/web/main/media"))

    (FileUtils/copyFile wfc (io/file wlib ".list"))
    (mkdirs (io/file appDir "src/test/js"))

    (doseq [df (:libs wbs)
            :let [dn (:dir df)
                  dd (io/file hhh DN_CFG "weblibs" dn)
                  td (io/file wlib dn)]
            :when (.isDirectory dd)]
      (copyToDir dd wlib)
      (when-not (:skip df)
        (doseq [f (:js df) ]
          (-> (.append buf (readOneFile (io/file td f)))
              (.append (str "\n\n/* @@@" f "@@@ */"))
              (.append "\n\n")))))

    (writeOneFile (io/file appDir "public/c/webcommon.css") "")
    (writeOneFile (io/file appDir "public/c/webcommon.js") buf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createMvcWeb ""

  [^File appDir ^String appId ^String appDomain ^String emType ]

  (let [appDomainPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        cfd (io/file appDir DN_CONF) ]
    (with-local-vars [fp nil]
      (createAppCommon appDir appId appDomain "web")
      (createWebCommon appDir appId appDomain)
      ;; copy files
      (copyFiles (io/file hhh DN_CFG "netty") cfd "conf")
      ;; modify files
      (var-set fp (io/file cfd "routes.conf"))
      (replaceFile @fp
                   #(cs/replace % "@@APPDOMAIN@@" appDomain))

      (var-set fp (io/file cfd ENV_CF))
      (replaceFile @fp
                   #(cs/replace % "@@EMTYPE@@" emType))

      (postCreateApp appDir appId appDomain))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createBasic ""

  [^File out appId ^String appDomain]

  (doto (mkdirs (io/file out appId))
    (createAppCommon appId appDomain "basic")
    (postCreateApp appId appDomain)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createJetty ""

  [^File out appId ^String appDomain]

  (-> (mkdirs (io/file out appId))
      (createMvcWeb appId appDomain "czc.skaro.io/JettyIO")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createNetty ""

  [^File out appId ^String appDomain]

  (-> (mkdirs (io/file out appId))
      (createMvcWeb appId appDomain "czc.skaro.io/NettyMVC")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

