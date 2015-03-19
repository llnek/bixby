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

  czlabclj.tardis.etc.cli

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
         [ReadOneFile WriteOneFile CopyFileToDir
          CopyFile CopyDir
          Unzip Mkdirs ReadEdn]]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.core.sys]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copy-files ""

  [^File srcDir ^File destDir ext]

  (FileUtils/copyDirectory srcDir
                           destDir
                           (FileFilterUtils/andFileFilter FileFileFilter/FILE
                                                          (FileFilterUtils/suffixFileFilter (str "." ext)))
  ))

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

  (let [pod (File. hhhHome (str "pods/" appId ".pod"))
        srcDir (File. hhhHome (str "apps/" appId))
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
(defn CreateDemo "Unzip the demo pod."

  [^File hhhHome demoId]

  (let [fp (File. hhhHome (str "docs/samples/" demoId ".pod"))
        dest (File. hhhHome (str "apps/demo-" demoId)) ]
    (log/debug "Unzipping demo pod: " demoId)
    (when (.exists fp)
      (Mkdirs dest)
      (Unzip fp dest))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateSamples "Unzip all samples."

  [^File hhhHome]

  (let [top (File. hhhHome (str "docs/samples"))
        fs (.listFiles top) ]
    (log/debug "Unzipping all samples.")
    (doseq [^File f (seq fs) ]
      (when (and (.isFile f)
                 (.endsWith (.getName f) ".pod"))
        (CreateDemo hhhHome (FilenameUtils/getBaseName (nsb f)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
        appDir (File. hhhHome (str "apps/" appId))
        appDomainPath (.replace appDomain "." "/")
        cljd (mkcljd appDir appDomain) ]
    (Mkdirs (File. h2db))
    (with-local-vars [fp nil ]
      (var-set fp (mkcljfp cljd "core.clj"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@" appDomain)))

      (var-set fp (mkcljfp cljd "pipe.clj"))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@APPDOMAIN@@"
                                             appDomain)))

      (var-set fp (File. appDir CFG_ENV_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@H2DBPATH@@"
                                             (str h2db "/" appId))
                        (.replace "@@APPDOMAIN@@"
                                             appDomain)))

      (var-set fp (File. appDir "build.gant"))
      (let [s (str "arg (value: \"" appDomain ".core\")"
                   "\n"
                   "arg (value: \"" appDomain ".pipe\")" ) ]
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

  (let [appDir (Mkdirs (File. hhhHome (str "apps/" appId)))
        cfd (File. appDir DN_CONF)
        mfDir (Mkdirs (File. appDir "META-INF"))
        appDomainPath (.replace appDomain "." "/") ]
    (with-local-vars [fp nil ]

      (doseq [s ["classes" "patch" "lib"]]
        (Mkdirs (File. appDir (str "POD-INF/" s))))

      (doseq [s ["RELEASE-NOTES.txt" "NOTES.txt"
                 "LICENSE.txt" "README.md"]]
        (FileUtils/touch (File. mfDir ^String s)))

      (CopyFileToDir (File. hhhHome "etc/app/MANIFEST.MF") mfDir)
      (CopyFileToDir (File. hhhHome "etc/app/build.gant") appDir)

      (Mkdirs (File. appDir "modules"))
      (Mkdirs cfd)
      (Mkdirs (File. appDir "docs"))

      ;;(doseq [s [APP_CF ENV_CF "shiro.ini"]]
      (doseq [s [APP_CF ENV_CF ]]
        (CopyFileToDir (File. hhhHome (str "etc/app/" s)) cfd))

      (var-set fp (File. cfd APP_CF))
      (WriteOneFile @fp
                    (-> (ReadOneFile @fp)
                        (.replace "@@USER@@" (GetUser))))

      (doseq [s [ "java" (str "clojure/" appDomainPath) ]]
        (Mkdirs (File. appDir (str "src/main/" s)))
        (Mkdirs (File. appDir (str "src/test/" s))))

      (CopyFileToDir (File. hhhHome "etc/app/core.clj")
                     (mkcljd appDir appDomain))
      (CopyFileToDir (File. hhhHome "etc/app/pipe.clj")
                     (mkcljd appDir appDomain))

      (Mkdirs (File. appDir "src/main/resources"))

      (doseq [s ["build.xs" "ivy.config.xml" "ivy.xml" "pom.xml"]]
        (CopyFileToDir (File. hhhHome (str "etc/app/" s)) appDir))

      (var-set fp (File. mfDir "MANIFEST.MF"))
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
(defn CreateBasic ""

  [^File hhhHome appId ^String appDomain]

  (create-app-common hhhHome appId appDomain "basic")
  (post-create-app hhhHome appId appDomain))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-web-common ""

  [^File hhhHome appId ^String appDomain]

  (let [wfc (File. hhhHome (str DN_CFG "/app/weblibs.conf" ))
        appDir (File. hhhHome (str "apps/" appId))
        wlib (Mkdirs (File. appDir "public/vendors"))
        wbs (ReadEdn wfc)
        csslg *SKARO-WEBCSSLANG*
        wlg *SKARO-WEBLANG*
        buf (StringBuilder.)
        appDomainPath (.replace appDomain "." "/") ]

    (doseq [s ["pages" "media" "scripts" "styles"]]
      (Mkdirs (File. appDir (str "src/web/site/" s)))
      (Mkdirs (File. appDir (str "public/" s))))

    (let [src (File. hhhHome "etc/netty")
          des (File. appDir (str "src/web/site/pages")) ]
      (copy-files src des "ftl")
      (copy-files src des "html"))

    ;;(CopyFileToDir (File. hhhHome "etc/web/pipe.clj")
                   ;;(mkcljd appDir appDomain))
    (CopyFileToDir (File. hhhHome "etc/netty/pipe.clj")
                   (mkcljd appDir appDomain))

    (CopyFileToDir (File. hhhHome "etc/web/main.scss")
                   (File. appDir (str "src/web/site/styles")))
    (CopyFileToDir (File. hhhHome "etc/web/main.js")
                   (File. appDir (str "src/web/site/scripts")))

    (CopyFileToDir (File. hhhHome "etc/web/favicon.png")
                   (File. appDir "src/web/site/media"))
    (CopyFileToDir (File. hhhHome "etc/web/body.jpg")
                   (File. appDir "src/web/site/media"))

    (FileUtils/copyFile wfc (File. wlib ".list"))
    (Mkdirs (File. appDir "src/test/js"))

    (doseq [df (:libs wbs) ]
      (let [^String dn (:dir df)
            dd (File. hhhHome
                      (str "etc/weblibs/" dn))
            td (File. wlib dn) ]
        (when (.isDirectory dd)
          (CopyDir dd wlib)
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

  [^File hhhHome
   appId
   ^String appDomain
   ^String emType
   ]

  (let [appDir (File. hhhHome (str "apps/" appId))
        appDomainPath (.replace appDomain "." "/")
        cfd (File. appDir DN_CONF) ]
    (with-local-vars [fp nil]
      (create-app-common hhhHome appId appDomain "web")
      (create-web-common hhhHome appId appDomain)
      (copy-files (File. hhhHome "etc/netty") cfd DN_CONF)

      (CopyFileToDir (File. hhhHome "etc/netty/static-routes.conf")
                     cfd)
      (CopyFileToDir (File. hhhHome "etc/netty/routes.conf")
                     cfd)

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
(def ^:private cli-eof nil)

