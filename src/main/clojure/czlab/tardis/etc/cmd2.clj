;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.tardis.etc.cmd2

  (:require [czlab.xlib.util.ini :refer [ParseInifile]]
            [czlab.xlib.util.str :refer [strim nsb TrimL TrimR]]
            [czlab.xlib.util.guids :refer [NewUUid]]
            [czlab.xlib.util.format :refer [ReadEdn]]
            [czlab.xlib.util.files :refer [ReplaceFile]]
            [czlab.tpcl.antlib :as ant]
            [czlab.xlib.util.core
             :refer
             [GetUser
              GetCwd
              juid
              IsWindows?
              prn!!
              prn!
              FPath]]
            [czlab.xlib.util.files
             :refer
             [ReadOneFile
              WriteOneFile
              CopyFileToDir
              DeleteDir
              CopyFile
              CopyToDir
              CopyFiles
              Unzip
              Mkdirs]])

  ;;(:refer-clojure :rename {first fst second snd })

  (:require [clojure.tools.logging :as log]
            [clojure.string :as cstr]
            [clojure.java.io :as io])

  (:use [czlab.tardis.core.consts])

  (:import  [org.apache.commons.io.filefilter FileFileFilter
                                              FileFilterUtils]
            [com.zotohlab.skaro.etc CmdHelpError]
            [org.apache.commons.io FilenameUtils FileUtils]
            [org.apache.commons.lang3 StringUtils]
            [java.util ResourceBundle UUID]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; some globals
(def SKARO-RSBUNDLE (atom nil))
(def SKARO-HOME-DIR (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResBdl "Return the system resource bundle."

  ^ResourceBundle
  []

  @SKARO-RSBUNDLE)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetHomeDir "Return the home directory."

  ^File
  []

  @SKARO-HOME-DIR)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare CreateBasic CreateNetty CreateJetty)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain ""

  [appDomain]

  (-> (nsb appDomain)
      (TrimL ".")
      (TrimR ".")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn CreateApp "Create a new app."

  [verb path]

  (let [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
        t (re-matches rx path)
        cwd (GetCwd)
        ;; treat as domain e.g com.acme => app = acme
        ;; regex gives ["com.acme" ".acme"]
        app (when-not (nil? t)
              (if-let [tkn (last t) ]
                (TrimL tkn ".")
                (first t))) ]
    (when (nil? app) (throw (CmdHelpError.)))
    (case verb
      ("mvc" "web")
      (CreateNetty cwd app path)
      "jetty"
      (CreateJetty cwd app path)
      "basic"
      (CreateBasic cwd app path)
      (throw (CmdHelpError.)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunAppBg "Run the application in the background."

  [^File hhh]

  (let [progW (io/file hhh "bin/skaro.bat")
        prog (io/file hhh "bin/skaro")
        cwd (GetCwd)
        tk (if (IsWindows?)
             (ant/AntExec {:executable "cmd.exe"
                           :dir cwd}
                          [[:argvalues [ "/C" "start" "/B"
                                        "/MIN"
                                        (FPath progW)
                                        "start" ]]])
             (ant/AntExec {:executable (FPath prog)
                           :dir cwd}
                          [[:argvalues [ "start" "bg" ]]])) ]
    (ant/RunTasks* tk)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BundleApp "Bundle an app."

  [^File hhh ^File app ^String out]

  (let [dir (Mkdirs (io/file out))
        tk (ant/AntZip {:destFile (io/file dir (.getName app) ".zip")
                        :basedir app
                        :excludes "build/**"
                        :includes "**/*"}) ]
    (ant/RunTasks* tk)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro mk-demo-path "" [dn] `(str "demo." ~dn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneJavaDemo ""

  [^File demo ^File out]

  (let [top (io/file out (.getName demo))
        dn (.getName top)]
    (prn!! "Generating demo[%s]..." dn)
    (CreateBasic out dn (mk-demo-path dn))
    (CopyFiles demo
               (io/file top DN_CONF) "conf")
    (CopyFiles demo
               (io/file top
                        "src/main/java/demo" dn)
               "java")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo ""

  [^File demo ^File out]

  (let [top (io/file out (.getName demo))
        dn (.getName top)
        dom (mk-demo-path dn)]
    (prn!! "Generating demo[%s]..." dn)
    (case dn
      "jetty" (CreateJetty out dn dom)
      "mvc" (CreateNetty out dn dom)
      (CreateBasic out dn dom))
    (CopyFiles demo
               (io/file top DN_CONF) "conf")
    (CopyFiles demo
               (io/file top
                        "src/main/clojure/demo" dn)
               "clj")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genJavaDemos ""

  [^File out]

  (let [top (io/file (GetHomeDir) "src/main/java/demo")
        dss (.listFiles top)]
    (doseq [^File d dss
            :when (.isDirectory d)]
      (genOneJavaDemo d out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos ""

  [^File out]

  (let [top (io/file (GetHomeDir) "src/main/clojure/demo")
        dss (.listFiles top)]
    (doseq [^File d dss
            :when (.isDirectory d)]
      (genOneCljDemo d out))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PublishSamples "Unzip all samples."

  [^String output]

  (let [out (Mkdirs output)]
    (genJavaDemos out)
    (genCljDemos out)
  ))

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
           (cstr/replace appDomain "." "/")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- post-create-app ""

  [^File appDir ^String appId ^String appDomain]

  (let [h2db (str (if (IsWindows?) "/c:/temp/" "/tmp/") (juid))
        h2dbUrl (str h2db "/" appId
                     ";MVCC=TRUE;AUTO_RECONNECT=TRUE")
        appDomainPath (cstr/replace appDomain "." "/")
        hhh (GetHomeDir)
        cljd (mkcljd appDir appDomain) ]
    (Mkdirs h2db)
    (with-local-vars [fp nil]
      (var-set fp (mkcljfp cljd "core.clj"))
      (ReplaceFile @fp
                   #(-> (cstr/replace % "@@APPDOMAIN@@" appDomain)
                        (cstr/replace "@@USER@@" (GetUser))))

      (var-set fp (io/file appDir CFG_ENV_CF))
      (ReplaceFile @fp
                   #(-> (cstr/replace % "@@H2DBPATH@@" h2dbUrl)
                        (cstr/replace "@@APPDOMAIN@@" appDomain))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-app-common ""

  [^File appDir ^String appId ^String appDomain ^String flavor]

  (let [appDomainPath (cstr/replace appDomain "." "/")
        mfDir (io/file appDir DN_CFG)
        cfd (io/file appDir DN_CONF)
        hhh (GetHomeDir)]
    (with-local-vars [fp nil]
      ;; make all the folders
      (doseq [^String s ["i18n" "modules" "logs"
                         DN_CONF DN_CFG
                         "src" "build" "patch" "target"]]
        (Mkdirs (io/file appDir s)))
      (doseq [s [ "clojure" "java"]]
        (Mkdirs (io/file appDir "src/main" s appDomainPath))
        (Mkdirs (io/file appDir "src/test" s appDomainPath)))

      (Mkdirs (io/file appDir "src/main/resources"))
      (Mkdirs (io/file appDir "src/main/artifacts"))

      ;;copy files

      (CopyFile (io/file hhh DN_CFG "log/log4jbuild.properties")
                (io/file appDir "src/main/artifacts/log4j.properties"))
      (CopyFile (io/file hhh DN_CFG "log/logback4build.xml")
                (io/file appDir "src/main/artifacts/logback.xml"))

      (CopyFileToDir (io/file hhh DN_CFGAPP "test.clj")
                     (io/file appDir "src/test/clojure" appDomainPath))

      (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
        (CopyFileToDir (io/file hhh DN_CFGAPP s)
                       (io/file appDir "src/test/java" appDomainPath)))

      (doseq [s ["build.boot" "pom.xml"]]
        (CopyFileToDir (io/file hhh DN_CFGAPP s) appDir))
      (FileUtils/touch (io/file appDir "README.md"))

      (doseq [s ["RELEASE-NOTES.txt" "NOTES.txt"
                 "LICENSE.txt"]]
        (FileUtils/touch (io/file appDir DN_CFG s)))
      (doseq [s [APP_CF ENV_CF ]]
        (CopyFileToDir (io/file hhh DN_CFGAPP s) cfd))
      (CopyFileToDir (io/file hhh DN_CFGAPP DN_RCPROPS)
                     (io/file  appDir "i18n"))
      (CopyFileToDir (io/file hhh DN_CFGAPP MF_FP)
                     (io/file appDir DN_CFG))
      (CopyFileToDir (io/file hhh DN_CFGAPP "core.clj")
                     (mkcljd appDir appDomain))

      ;;modify files, replace placeholders
      (var-set fp (io/file appDir
                           "src/test/clojure" appDomainPath "test.clj"))
      (ReplaceFile @fp
                   #(cstr/replace % "@@APPDOMAIN@@" appDomain))

      (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
        (var-set fp (io/file appDir
                             "src/test/java" appDomainPath s))
        (ReplaceFile @fp
                     #(cstr/replace % "@@APPDOMAIN@@" appDomain)))

      (var-set fp (io/file cfd APP_CF))
      (ReplaceFile @fp
                   #(cstr/replace % "@@USER@@" (GetUser)))

      (var-set fp (io/file appDir DN_CFG MF_FP))
      (ReplaceFile @fp
                   #(-> (cstr/replace % "@@APPKEY@@" (NewUUid))
                        (cstr/replace "@@VER@@" "0.1.0-SNAPSHOT")
                        (cstr/replace "@@APPMAINCLASS@@"
                                  (str appDomain ".core.MyAppMain"))))

      (var-set fp (io/file appDir "pom.xml"))
      (ReplaceFile @fp
                   #(-> (cstr/replace % "@@APPDOMAIN@@" appDomain)
                        (cstr/replace "@@VER@@" "0.1.0-SNAPSHOT")
                        (cstr/replace "@@APPID@@" appId)))

      (var-set fp (io/file appDir "build.boot"))
      (ReplaceFile @fp
                   #(-> (cstr/replace % "@@APPDOMAIN@@" appDomain)
                        (cstr/replace "@@TYPE@@" flavor)
                        (cstr/replace "@@VER@@" "0.1.0-SNAPSHOT")
                        (cstr/replace "@@APPID@@" appId))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-web-common ""

  [^File appDir appId ^String appDomain]

  (let [appDomainPath (cstr/replace appDomain "." "/")
        hhh (GetHomeDir)
        wfc (io/file hhh DN_CFGAPP "weblibs.conf")
        wlib (io/file appDir "public/vendors")
        wbs (ReadEdn wfc)
        buf (StringBuilder.)]

    ;; make folders
    (doseq [s ["pages" "media" "scripts" "styles"]]
      (Mkdirs (io/file appDir "src/web/site" s))
      (Mkdirs (io/file appDir "public" s)))
    (Mkdirs wlib)

    ;; copy files
    (let [des (io/file appDir "src/web/site/pages")
          src (io/file hhh DN_CFG "netty")]
      (CopyFiles src des "ftl")
      (CopyFiles src des "html"))

    (CopyFileToDir (io/file hhh DN_CFG "netty/core.clj")
                   (mkcljd appDir appDomain))

    (CopyFileToDir (io/file hhh DN_CFGWEB "main.scss")
                   (io/file appDir "src/web/site/styles"))
    (CopyFileToDir (io/file hhh DN_CFGWEB "main.js")
                   (io/file appDir "src/web/site/scripts"))

    (CopyFileToDir (io/file hhh DN_CFGWEB "favicon.png")
                   (io/file appDir "src/web/site/media"))
    (CopyFileToDir (io/file hhh DN_CFGWEB "body.jpg")
                   (io/file appDir "src/web/site/media"))

    (FileUtils/copyFile wfc (io/file wlib ".list"))
    (Mkdirs (io/file appDir "src/test/js"))

    (doseq [df (:libs wbs)
            :let [dn (:dir df)
                  dd (io/file hhh DN_CFG "weblibs" dn)
                  td (io/file wlib dn)]
            :when (.isDirectory dd)]
      (CopyToDir dd wlib)
      (when-not (:skip df)
        (doseq [f (:js df) ]
          (-> (.append buf (ReadOneFile (io/file td f)))
              (.append (str "\n\n/* @@@" f "@@@ */"))
              (.append "\n\n")))))

    (WriteOneFile (io/file appDir "public/c/webcommon.css") "")
    (WriteOneFile (io/file appDir "public/c/webcommon.js") buf)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-mvc-web ""

  [^File appDir ^String appId ^String appDomain ^String emType ]

  (let [appDomainPath (cstr/replace appDomain "." "/")
        hhh (GetHomeDir)
        cfd (io/file appDir DN_CONF) ]
    (with-local-vars [fp nil]
      (create-app-common appDir appId appDomain "web")
      (create-web-common appDir appId appDomain)
      ;; copy files
      (CopyFiles (io/file hhh DN_CFG "netty") cfd "conf")
      ;; modify files
      (var-set fp (io/file cfd "routes.conf"))
      (ReplaceFile @fp
                   #(cstr/replace % "@@APPDOMAIN@@" appDomain))

      (var-set fp (io/file cfd ENV_CF))
      (ReplaceFile @fp
                   #(cstr/replace % "@@EMTYPE@@" emType))

      (post-create-app appDir appId appDomain))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateBasic ""

  [^File out appId ^String appDomain]

  (let [appdir (Mkdirs (io/file out appId))]
    (create-app-common appdir appId appDomain "basic")
    (post-create-app appdir appId appDomain)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateJetty ""

  [^File out appId ^String appDomain]

  (let [appdir (Mkdirs (io/file out appId))]
    (create-mvc-web appdir appId appDomain "czc.tardis.io/JettyIO")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateNetty ""

  [^File out appId ^String appDomain]

  (let [appdir (Mkdirs (io/file out appId))]
    (create-mvc-web appdir appId appDomain "czc.tardis.io/NettyMVC")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

