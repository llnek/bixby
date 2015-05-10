;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.etc.cmd2

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlabclj.xlib.util.ini :only [ParseInifile]]
        [czlabclj.xlib.util.str :only [strim nsb]]
        [czlabclj.xlib.util.guids :only [NewUUid]]
        [czlabclj.xlib.util.format :only [ReadEdn]]
        [czlabclj.xlib.util.core
         :only
         [GetUser juid IsWindows? NiceFPath]]
        [czlabclj.xlib.util.files
         :only
         [ReadOneFile WriteOneFile CopyFileToDir DeleteDir
          CopyFile CopyToDir CopyFiles
          Unzip Mkdirs]]
        [czlabclj.tardis.core.consts]
        [czlabclj.tardis.etc.task])

  (:import  [org.apache.commons.io.filefilter FileFileFilter
                                              FileFilterUtils]
            [org.apache.commons.io FilenameUtils FileUtils]
            [org.apache.commons.lang3 StringUtils]
            [java.util UUID]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *SKARO-WEBCSSLANG* "scss")
(def ^:dynamic *SKARO-WEBLANG* "js")

(declare CreateBasic)
(declare CreateNetty)
(declare CreateJetty)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain ""

  [appDomain]

  (-> (nsb appDomain)
      (StringUtils/stripStart ".")
      (StringUtils/stripEnd ".")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunAppBg "Run the application in the background."

  [^File hhhHome bg]

  (let [progW (NiceFPath (io/file hhhHome "bin" "skaro.bat"))
        prog (NiceFPath (io/file hhhHome "bin" "skaro"))
        pj (if (IsWindows?)
             (MakeExecTask "cmd.exe"
                           hhhHome
                           [ "/C" "start" "/B" "/MIN" progW "start" ])
             (MakeExecTask prog hhhHome [ "start" "bg" ])) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BundleApp "Bundle an app."

  [^File hhhHome appId]

  (let [pod (io/file hhhHome DN_PODS appId ".pod")
        srcDir (io/file hhhHome DN_BOXX appId)
        pj (MakeZipTask srcDir pod [] [ "build.output.folder/**" ]) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntBuildApp "Run and execute an ant target."

  [^File hhhHome appId antTarget]

  (ExecProj (MakeAntTask hhhHome appId antTarget)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanAppClasses "Clean up stuff."

  [^File webzDir ^File czDir]

  (FileUtils/cleanDirectory webzDir)
  (FileUtils/cleanDirectory czDir)
  (Mkdirs czDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PublishDemo "Unzip the demo pod."

  [^File hhhHome demoId]

  (let [fp (io/file hhhHome "docs" "samples" demoId ".pod")
        dest (io/file hhhHome DN_BOXX demoId) ]
    (log/debug "Unzipping demo pod: " demoId)
    (when (.exists fp)
      (Mkdirs dest)
      (Unzip fp dest))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc XXXPublishSamples "Unzip all samples."

  [^File hhhHome]

  (let [top (io/file hhhHome "docs" "samples")
        fs (.listFiles top) ]
    (log/debug "Unzipping all samples.")
    (doseq [^File f (seq fs) ]
      (when (and (.isFile f)
                 (-> (.getName f)(.endsWith ".pod")))
        (PublishDemo hhhHome (FilenameUtils/getBaseName (nsb f)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneJavaDemo ""

  [^File hhh ^File demo]

  (let [dname (.getName demo)
        dom (str "demo." dname) ]
    (CreateBasic hhh dname dom)
    (let [top (io/file hhh DN_BOXX dname)
          src (io/file top "src" "main" "java" "demo" dname)]
      (CopyFiles demo (io/file top DN_CONF) "conf")
      (CopyFiles demo src "java")
      (DeleteDir (io/file top "src" "main" "clojure"))
      (DeleteDir (io/file top "src" "test" "clojure")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo ""

  [^File hhh ^File demo]

  (let [dname (.getName demo)
        dom (str "demo." dname) ]
    (case dname
      "jetty" (CreateJetty hhh dname dom)
      "mvc" (CreateNetty hhh dname dom)
      (CreateBasic hhh dname dom))
    (let [top (io/file hhh DN_BOXX dname)
          src (io/file top "src" "main" "clojure" "demo" dname)]
      (CopyFiles demo (io/file top DN_CONF) "conf")
      (CopyFiles demo src "clj"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genJavaDemos ""

  [^File hhh]

  (let [top (io/file hhh "src" "main" "java" "demo")
        dss (.listFiles top)]
    (doseq [^File d dss]
      (when (.isDirectory d)
        (genOneJavaDemo hhh d)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos ""

  [^File hhh]

  (let [top (io/file hhh "src" "main" "clojure" "demo")
        dss (.listFiles top)]
    (doseq [^File d dss]
      (when (.isDirectory d)
        (genOneCljDemo hhh d)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PublishSamples "Unzip all samples."

  [^File hhhHome]

  (genJavaDemos hhhHome)
  (genCljDemos hhhHome))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljfp ""

  ^File
  [^File cljd  ^String fname]

  (io/file cljd fname))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd ""

  ^File
  [^File appDir ^String appDomain]

  (io/file appDir
           "src" "main" "clojure"
           (.replace appDomain "." "/")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- post-create-app ""

  [^File hhhHome ^String appId ^String appDomain]

  (let [h2db (str (if (IsWindows?) "/c:/temp/" "/tmp/") (juid))
        h2dbUrl (str h2db "/" appId
                     ";MVCC=TRUE;AUTO_RECONNECT=TRUE")
        appDir (io/file hhhHome DN_BOXX appId)
        appDomainPath (.replace appDomain "." "/")
        cljd (mkcljd appDir appDomain) ]
    (Mkdirs (io/file h2db))
    (with-local-vars [fp nil ]
      (var-set fp (mkcljfp cljd "core.clj"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)
                        (.replace "@@USER@@" (GetUser))))

      (var-set fp (io/file appDir CFG_ENV_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@H2DBPATH@@" h2dbUrl)
                        (.replace "@@APPDOMAIN@@"
                                             appDomain)))

      (var-set fp (io/file appDir "build.gant"))
      (let [s (str "arg (value: \"" appDomain ".core\")")]
        (WriteOneFile @fp
                      (-> (ReadOneFile @fp)
                          (.replace "@@APPCLJFILES@@" s)
                          (.replace "@@APPDOMAIN@@" appDomain)
                          (.replace "@@APPID@@" appId)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-app-common ""

  [^File hhhHome ^String appId ^String appDomain ^String flavor]

  (let [appDir (Mkdirs (io/file hhhHome DN_BOXX appId))
        mfDir (io/file appDir META_INF)
        cfd (io/file appDir DN_CONF)
        appDomainPath (.replace appDomain "." "/") ]
    (with-local-vars [fp nil ]

      ;; make all the folders

      (doseq [^String s ["alchemy.dir/clojure.org" DN_CONF
                         "alchemy.dir/build" "docs" "i18n"
                         META_INF "modules" POD_INF "src"]]
        (Mkdirs (io/file appDir s)))
      (doseq [s ["classes" "patch" "lib"]]
        (Mkdirs (io/file  appDir POD_INF  s)))
      (doseq [s [ "java" (str "clojure/" appDomainPath) ]]
        (Mkdirs (io/file appDir "src" "main" s))
        (Mkdirs (io/file appDir "src" "test" s)))
      (Mkdirs (io/file appDir "src" "main" "resources"))

      ;;copy files

      (CopyFileToDir (io/file hhhHome
                              DN_CFGAPP "build.gant") appDir)
      (doseq [s ["build.xs" "ivy.config.xml"
                 "ivy.xml" "pom.xml"]]
        (CopyFileToDir (io/file hhhHome DN_CFGAPP s) appDir))
      (doseq [s ["RELEASE-NOTES.txt" "NOTES.txt"
                 "LICENSE.txt" "README.md"]]
        (FileUtils/touch (io/file mfDir s)))
      (doseq [s [APP_CF ENV_CF ]]
        (CopyFileToDir (io/file hhhHome DN_CFGAPP s) cfd))
      (CopyFileToDir (io/file hhhHome DN_CFGAPP DN_RCPROPS)
                     (io/file  appDir "i18n"))
      (CopyFileToDir (io/file hhhHome DN_CFGAPP MF_FP) mfDir)
      (CopyFileToDir (io/file hhhHome DN_CFGAPP "core.clj")
                     (mkcljd appDir appDomain))

      ;;modify files, replace placeholders

      (var-set fp (io/file cfd APP_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@USER@@" (GetUser))))

      (var-set fp (io/file mfDir MF_FP))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPKEY@@" (NewUUid))
                        (.replace "@@APPMAINCLASS@@"
                                  (str appDomain ".core.MyAppMain"))))

      (var-set fp (io/file appDir "pom.xml"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)
                        (.replace "@@APPID@@" appId)))

      (var-set fp (io/file appDir "ivy.xml"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)
                        (.replace "@@APPID@@" appId)))

      (var-set fp (io/file appDir "build.xs"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@WEBCSSLANG@@" ^String *SKARO-WEBCSSLANG*)
                        (.replace "@@WEBLANG@@" ^String *SKARO-WEBLANG*)
                        (.replace "@@APPTYPE@@" flavor)
                        (.replace "@@SKAROHOME@@" (NiceFPath hhhHome)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-web-common ""

  [^File hhhHome appId ^String appDomain]

  (let [wfc (io/file hhhHome DN_CFGAPP "weblibs.conf")
        appDir (io/file hhhHome DN_BOXX  appId)
        wlib (io/file appDir "public/vendors")
        wbs (ReadEdn wfc)
        csslg *SKARO-WEBCSSLANG*
        wlg *SKARO-WEBLANG*
        buf (StringBuilder.)
        appDomainPath (.replace appDomain "." "/") ]

    ;; make folders
    (doseq [s ["pages" "media" "scripts" "styles"]]
      (Mkdirs (io/file appDir "src" "web" "site" s))
      (Mkdirs (io/file appDir "public" s)))
    (Mkdirs wlib)

    ;; copy files
    (let [src (io/file hhhHome DN_CFG "netty")
          des (io/file appDir "src" "web" "site" "pages") ]
      (CopyFiles src des "ftl")
      (CopyFiles src des "html"))

    (CopyFileToDir (io/file hhhHome DN_CFG "netty" "core.clj")
                   (mkcljd appDir appDomain))

    (CopyFileToDir (io/file hhhHome DN_CFGWEB "main.scss")
                   (io/file appDir "src" "web" "site" "styles"))
    (CopyFileToDir (io/file hhhHome DN_CFGWEB "main.js")
                   (io/file appDir "src" "web" "site" "scripts"))

    (CopyFileToDir (io/file hhhHome DN_CFGWEB "favicon.png")
                   (io/file appDir "src" "web" "site" "media"))
    (CopyFileToDir (io/file hhhHome DN_CFGWEB "body.jpg")
                   (io/file appDir "src" "web" "site" "media"))

    (FileUtils/copyFile wfc (io/file wlib ".list"))
    (Mkdirs (io/file appDir "src" "test" "js"))

    (doseq [df (:libs wbs) ]
      (let [^String dn (:dir df)
            dd (io/file hhhHome DN_CFG "weblibs" dn)
            td (io/file wlib dn) ]
        (when (.isDirectory dd)
          (CopyToDir dd wlib)
          (when-not (:skip df)
            (doseq [^String f (:js df) ]
              (-> buf
                  (.append (ReadOneFile (io/file td f)))
                  (.append (str "\n\n/* @@@" f "@@@ */"))
                  (.append "\n\n")))))))

    (WriteOneFile (io/file appDir "public" "c" "webcommon.css") "")
    (WriteOneFile (io/file appDir "public" "c" "webcommon.js") buf)

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-mvc-web ""

  [^File hhhHome appId
   ^String appDomain ^String emType ]

  (let [appDir (io/file hhhHome DN_BOXX  appId)
        appDomainPath (.replace appDomain "." "/")
        cfd (io/file appDir DN_CONF) ]
    (with-local-vars [fp nil]
      (create-app-common hhhHome appId appDomain "web")
      (create-web-common hhhHome appId appDomain)
      ;; copy files
      (CopyFiles (io/file hhhHome DN_CFG "netty") cfd DN_CONF)
      (CopyFileToDir (io/file hhhHome
                              DN_CFG
                              "netty" "static-routes.conf") cfd)
      (CopyFileToDir (io/file hhhHome
                              DN_CFG "netty" "routes.conf") cfd)

      ;; modify files
      (var-set fp (io/file appDir DN_CONF "routes.conf"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)))

      (var-set fp (io/file cfd ENV_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@EMTYPE@@" emType)))

      (post-create-app hhhHome appId appDomain))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateBasic ""

  [^File hhhHome appId ^String appDomain]

  (create-app-common hhhHome appId appDomain "basic")
  (post-create-app hhhHome appId appDomain))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateJetty ""

  [^File hhhHome appId ^String appDomain]

  (create-mvc-web hhhHome appId appDomain "czc.tardis.io/JettyIO"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateNetty ""

  [^File hhhHome appId ^String appDomain]

  (create-mvc-web hhhHome appId appDomain "czc.tardis.io/NettyMVC"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cmd2-eof nil)

