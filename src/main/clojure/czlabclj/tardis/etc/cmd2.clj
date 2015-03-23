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

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.ini :only [ParseInifile]]
        [czlabclj.xlib.util.str :only [strim nsb]]
        [czlabclj.xlib.util.guids :only [NewUUid]]
        [czlabclj.xlib.util.core
         :only
         [GetUser juid IsWindows? NiceFPath]]
        [czlabclj.xlib.util.files
         :only
         [ReadOneFile WriteOneFile CopyFileToDir DeleteDir
          CopyFile CopyToDir CopyFiles
          Unzip Mkdirs ReadEdn]]
        [czlabclj.tardis.core.constants]
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
;;(def ^:dynamic *SKARO-WEBLANG* "coffee")
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
(defn RunAppBg ""

  [^File hhhHome bg]

  (let [progW (NiceFPath (File. hhhHome "bin/skaro.bat"))
        prog (NiceFPath (File. hhhHome "bin/skaro"))
        pj (if (IsWindows?)
             (MakeExecTask "cmd.exe"
                           hhhHome
                           [ "/C" "start" "/B" "/MIN" progW "start" ])
             (MakeExecTask prog hhhHome [ "start" "bg" ])) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BundleApp ""

  [^File hhhHome appId]

  (let [pod (File. hhhHome (str DN_PODS "/" appId ".pod"))
        srcDir (File. hhhHome (str DN_BOXX "/" appId))
        pj (MakeZipTask srcDir pod [] [ "build.output.folder/**" ]) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntBuildApp ""

  [^File hhhHome appId antTarget]

  (ExecProj (MakeAntTask hhhHome appId antTarget)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanAppClasses ""

  [^File webzDir ^File czDir]

  (FileUtils/cleanDirectory webzDir)
  (FileUtils/cleanDirectory czDir)
  (Mkdirs czDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PublishDemo "Unzip the demo pod."

  [^File hhhHome demoId]

  (let [fp (File. hhhHome (str "docs/samples/" demoId ".pod"))
        dest (File. hhhHome (str DN_BOXX "/" demoId)) ]
    (log/debug "Unzipping demo pod: " demoId)
    (when (.exists fp)
      (Mkdirs dest)
      (Unzip fp dest))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn XXXPublishSamples "Unzip all samples."

  [^File hhhHome]

  (let [top (File. hhhHome (str "docs/samples"))
        fs (.listFiles top) ]
    (log/debug "Unzipping all samples.")
    (doseq [^File f (seq fs) ]
      (when (and (.isFile f)
                 (.endsWith (.getName f) ".pod"))
        (PublishDemo hhhHome (FilenameUtils/getBaseName (nsb f)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneJavaDemo ""

  [^File hhh ^File demo]

  (let [dname (.getName demo)
        dom (str "demo." dname) ]
    (CreateBasic hhh dname dom)
    (let [top (File. hhh (str DN_BOXX "/" dname))
          src (File. top (str "src/main/java/demo/" dname))]
      (CopyFiles demo (File. top DN_CONF) "conf")
      (CopyFiles demo src "java")
      (DeleteDir (File. top (str "src/main/clojure")))
      (DeleteDir (File. top (str "src/test/clojure"))))
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
    (let [top (File. hhh (str DN_BOXX "/" dname))
          src (File. top (str "src/main/clojure/demo/" dname))]
      (CopyFiles demo (File. top DN_CONF) "conf")
      (CopyFiles demo src "clj")
      )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genJavaDemos ""

  [^File hhh]

  (let [top (File. hhh "src/main/java/demo")
        dss (.listFiles top)]
    (doseq [^File d (seq dss)]
      (when (.isDirectory d)
        (genOneJavaDemo hhh d)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genCljDemos ""

  [^File hhh]

  (let [top (File. hhh "src/main/clojure/demo")
        dss (.listFiles top)]
    (doseq [^File d (seq dss)]
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
  [^File cljd  ^String file]

  (File. cljd file))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd ""

  ^File
  [^File appDir ^String appDomain]

  (File. appDir (str "src/main/clojure/" (.replace appDomain "." "/"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- post-create-app ""

  [^File hhhHome ^String appId ^String appDomain]

  (let [h2db (str (if (IsWindows?) "/c:/temp/" "/tmp/") (juid))
        appDir (File. hhhHome (str DN_BOXX "/" appId))
        appDomainPath (.replace appDomain "." "/")
        cljd (mkcljd appDir appDomain) ]
    (Mkdirs (File. h2db))
    (with-local-vars [fp nil ]
      (var-set fp (mkcljfp cljd "core.clj"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)
                        (.replace "@@USER@@" (GetUser))))

      (var-set fp (File. appDir CFG_ENV_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@H2DBPATH@@"
                                             (str h2db "/" appId))
                        (.replace "@@APPDOMAIN@@"
                                             appDomain)))

      (var-set fp (File. appDir "build.gant"))
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

  (let [appDir (Mkdirs (File. hhhHome (str DN_BOXX "/" appId)))
        cfd (File. appDir DN_CONF)
        mfDir (File. appDir META_INF)
        appDomainPath (.replace appDomain "." "/") ]
    (with-local-vars [fp nil ]

      ;; make all the folders

      (doseq [^String s [DN_CONF "docs" "i18n"
                         META_INF "modules" POD_INF "src"]]
        (Mkdirs (File. appDir s)))
      (doseq [s ["classes" "patch" "lib"]]
        (Mkdirs (File. appDir (str POD_INF "/" s))))
      (doseq [s [ "java" (str "clojure/" appDomainPath) ]]
        (Mkdirs (File. appDir (str "src/main/" s)))
        (Mkdirs (File. appDir (str "src/test/" s))))
      (Mkdirs (File. appDir "src/main/resources"))

      ;;copy files

      (CopyFileToDir (File. hhhHome
                            (str DN_CFGAPP "/" "build.gant")) appDir)
      (doseq [s ["build.xs" "ivy.config.xml" "ivy.xml" "pom.xml"]]
        (CopyFileToDir (File. hhhHome (str DN_CFGAPP "/" s)) appDir))
      (doseq [s ["RELEASE-NOTES.txt" "NOTES.txt"
                 "LICENSE.txt" "README.md"]]
        (FileUtils/touch (File. mfDir ^String s)))
      (doseq [s [APP_CF ENV_CF ]]
        (CopyFileToDir (File. hhhHome (str DN_CFGAPP "/" s)) cfd))
      (CopyFileToDir (File. hhhHome (str DN_CFGAPP "/" DN_RCPROPS))
                     (File.  appDir "i18n"))
      (CopyFileToDir (File. hhhHome (str DN_CFGAPP "/" MF_FP)) mfDir)
      (CopyFileToDir (File. hhhHome (str DN_CFGAPP "/" "core.clj"))
                     (mkcljd appDir appDomain))

      ;;modify files, replace placeholders

      (var-set fp (File. cfd APP_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@USER@@" (GetUser))))

      (var-set fp (File. mfDir MF_FP))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPKEY@@" (NewUUid))
                        (.replace "@@APPMAINCLASS@@"
                                  (str appDomain ".core.MyAppMain"))))

      (var-set fp (File. appDir "pom.xml"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)
                        (.replace "@@APPID@@" appId)))

      (var-set fp (File. appDir "ivy.xml"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)
                        (.replace "@@APPID@@" appId)))

      (var-set fp (File. appDir "build.xs"))
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

  (let [wfc (File. hhhHome (str DN_CFGAPP "/" "weblibs.conf" ))
        appDir (File. hhhHome (str DN_BOXX "/" appId))
        wlib (File. appDir "public/vendors")
        wbs (ReadEdn wfc)
        csslg *SKARO-WEBCSSLANG*
        wlg *SKARO-WEBLANG*
        buf (StringBuilder.)
        appDomainPath (.replace appDomain "." "/") ]

    ;; make folders
    (doseq [s ["pages" "media" "scripts" "styles"]]
      (Mkdirs (File. appDir (str "src/web/site/" s)))
      (Mkdirs (File. appDir (str "public/" s))))
    (Mkdirs wlib)

    ;; copy files
    (let [src (File. hhhHome (str DN_CFG "/" "netty"))
          des (File. appDir (str "src/web/site/pages")) ]
      (CopyFiles src des "ftl")
      (CopyFiles src des "html"))

    (CopyFileToDir (File. hhhHome
                          (str DN_CFG "/" "netty/core.clj"))
                   (mkcljd appDir appDomain))

    (CopyFileToDir (File. hhhHome (str DN_CFGWEB "/" "main.scss"))
                   (File. appDir (str "src/web/site/styles")))
    (CopyFileToDir (File. hhhHome (str DN_CFGWEB "/" "main.js"))
                   (File. appDir (str "src/web/site/scripts")))

    (CopyFileToDir (File. hhhHome (str DN_CFGWEB "/" "favicon.png"))
                   (File. appDir "src/web/site/media"))
    (CopyFileToDir (File. hhhHome (str DN_CFGWEB "/" "body.jpg"))
                   (File. appDir "src/web/site/media"))

    (FileUtils/copyFile wfc (File. wlib ".list"))
    (Mkdirs (File. appDir "src/test/js"))

    (doseq [df (:libs wbs) ]
      (let [^String dn (:dir df)
            dd (File. hhhHome
                      (str DN_CFG "/weblibs/" dn))
            td (File. wlib dn) ]
        (when (.isDirectory dd)
          (CopyToDir dd wlib)
          (when-not (:skip df)
            (doseq [^String f (:js df) ]
              (-> buf
                  (.append (ReadOneFile (File. td f)))
                  (.append (str "\n\n/* @@@" f "@@@ */"))
                  (.append "\n\n")))))))

    (WriteOneFile (File. appDir "public/c/webcommon.css") "")
    (WriteOneFile (File. appDir "public/c/webcommon.js") buf)

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-mvc-web ""

  [^File hhhHome appId
   ^String appDomain ^String emType ]

  (let [appDir (File. hhhHome (str DN_BOXX "/" appId))
        appDomainPath (.replace appDomain "." "/")
        cfd (File. appDir DN_CONF) ]
    (with-local-vars [fp nil]
      (create-app-common hhhHome appId appDomain "web")
      (create-web-common hhhHome appId appDomain)
      ;; copy files
      (CopyFiles (File. hhhHome (str DN_CFG "/netty")) cfd DN_CONF)
      (CopyFileToDir (File. hhhHome
                            (str DN_CFG
                                 "/netty/static-routes.conf")) cfd)
      (CopyFileToDir (File. hhhHome
                            (str DN_CFG "/netty/routes.conf")) cfd)

      ;; modify files
      (var-set fp (File. appDir (str DN_CONF "/" "routes.conf")))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)))

      (var-set fp (File. cfd ENV_CF))
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

