;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }
  comzotohlabscljc.tardis.etc.cli

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.data.json :as json])
  (:use [comzotohlabscljc.util.files :only [Unzip] ])
  (:use [comzotohlabscljc.util.core :only [juid IsWindows?] ])
  (:use [comzotohlabscljc.util.ini :only [ParseInifile] ])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.etc.task])

  (:import (org.apache.commons.io.filefilter FileFileFilter FileFilterUtils))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (org.apache.commons.io FilenameUtils FileUtils))
  (:import (java.util UUID))
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *SKARO-WEBCSSLANG* "scss")
(def ^:dynamic *SKARO-WEBLANG* "coffee")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copy-files ""

  [^File srcDir ^File destDir ext]

  (FileUtils/copyDirectory
    srcDir
    destDir
    (FileFilterUtils/andFileFilter FileFileFilter/FILE
                                   (FileFilterUtils/suffixFileFilter (str "." ext)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain ""

  [appDomain]

  (-> appDomain
    (StringUtils/stripStart ".")
    (StringUtils/stripEnd ".")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunAppBg ""

  [^File hhhHome bg]

  (let [ prog2 (.getCanonicalPath (File. hhhHome "bin/skaro.bat"))
         prog (.getCanonicalPath (File. hhhHome "bin/skaro"))
         pj (if (IsWindows?)
              (MakeExecTask "cmd.exe" hhhHome
                             [ "/C" "start" "/B" "/MIN" prog2 "start" ])
              (MakeExecTask prog hhhHome [ "start" "bg" ])) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BundleApp ""

  [^File hhhHome appId]

  (let [ srcDir (File. hhhHome (str "apps/" appId))
         pod (File. hhhHome (str "pods/" appId ".pod"))
         pj (MakeZipTask srcDir pod [] [ "build.output.folder/**" ]) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntBuildApp ""

  [^File hhhHome appId antTarget]

  (let [ pj (MakeAntTask hhhHome appId antTarget) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanAppClasses ""

  [^File webzDir ^File czDir]

  (FileUtils/cleanDirectory webzDir)
  (FileUtils/cleanDirectory czDir)
  (.mkdirs czDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateDemo "Unzip the demo pod."

  [^File hhhHome demoId]

  (let [ fp (File. hhhHome (str "docs/samples/" demoId ".pod"))
         dest (File. hhhHome (str "apps/demo-" demoId)) ]
    (log/debug "Unzipping demo pod: " demoId)
    (when (.exists fp)
      (.mkdirs dest)
      (unzip fp dest))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateSamples "Unzip all samples."

  [^File hhhHome]

  (let [ top (File. hhhHome (str "docs/samples"))
         fs (.listFiles top) ]
    (log/debug "Unzipping all samples.")
    (doseq [ ^File f (seq fs) ]
      (when (and (.isFile f) (.endsWith (.getName f) ".pod"))
        (CreateDemo hhhHome (FilenameUtils/getBaseName (.toString f)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- post-create-app ""

  [^File hhhHome appId ^String appDomain]

  (let [ appDir (File. hhhHome (str "apps/" appId))
         h2db (str (if (IsWindows?) "/c:/temp/" "/tmp/") (juid))
         appDomainPath (.replace appDomain "." "/") ]
    (-> (File. h2db) (.mkdirs))
    (with-local-vars [ fp nil ]
      (var-set fp (File. appDir (str "src/main/clojure/" appDomainPath "/core.clj")))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@APPDOMAIN@@" appDomain)) "utf-8")

      (var-set fp (File. appDir (str "src/main/clojure/" appDomainPath "/pipe.clj")))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@APPDOMAIN@@" appDomain)) "utf-8")

      (var-set fp (File. appDir "conf/env.conf"))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@H2DBPATH@@" (str h2db "/" appId))
            (StringUtils/replace "@@APPDOMAIN@@" appDomain)) "utf-8")

      (var-set fp (File. appDir "build.xml"))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@APPCLJFILES@@"
                               (str "<arg value=\""
                                    appDomain
                                    ".core\"/>"
                                    "<arg value=\""
                                    appDomain
                                    ".pipe\"/>" ))
            (StringUtils/replace "@@APPDOMAIN@@" appDomain)
            (StringUtils/replace "@@APPID@@" appId)) "utf-8")
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-app-common ""

  [^File hhhHome appId ^String appDomain flavor]

  (let [ appDir (doto (File. hhhHome (str "apps/" appId)) (.mkdirs))
         mfDir (doto (File. appDir "META-INF")(.mkdirs))
         appDomainPath (.replace appDomain "." "/") ]
    (with-local-vars [ fp nil ]
      (doseq [ s ["classes" "patch" "lib"]]
        (-> (File. appDir (str "POD-INF/" s)) (.mkdirs)))

      (doseq [ s ["RELEASE-NOTES.txt" "NOTES.txt" "LICENSE.txt" "README.md"]]
        (FileUtils/touch (File. mfDir ^String s)))

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/build.xml")
                                     (File. hhhHome (str "apps/" appId)))

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/MANIFEST.MF")
                                     mfDir)

      (-> (File. appDir "modules")(.mkdirs))
      (-> (File. appDir "conf")(.mkdirs))
      (-> (File. appDir "docs")(.mkdirs))

      (doseq [ s ["app.conf" "env.conf" "shiro.ini"]]
        (FileUtils/copyFileToDirectory (File. hhhHome (str "etc/app/" s))
                                       (File. appDir "conf")))

      (var-set fp (File. appDir "conf/app.conf"))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@USER@@"
                               (System/getProperty "user.name")))
        "utf-8")

      (doseq [ s [ "java" (str "clojure/" appDomainPath) ]]
        (-> (File. appDir (str "src/main/" s)) (.mkdirs))
        (-> (File. appDir (str "src/test/" s)) (.mkdirs)))

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/core.clj")
                                     (File. appDir (str "src/main/clojure/" appDomainPath)))
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/pipe.clj")
                                     (File. appDir (str "src/main/clojure/" appDomainPath)))

      (-> (File. appDir "src/main/resources") (.mkdirs))

      (doseq [ s ["build.xs" "ivy.config.xml" "ivy.xml" "pom.xml"]]
        (FileUtils/copyFileToDirectory (File. hhhHome (str "etc/app/" s))
                                       appDir))

      (var-set fp (File. mfDir "MANIFEST.MF"))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@APPKEY@@" (.toString (UUID/randomUUID)))
            (StringUtils/replace "@@APPMAINCLASS@@"  (str appDomain ".core.MyAppMain")))
                                   "utf-8")

      (var-set fp (File. appDir "pom.xml"))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@APPDOMAIN@@" appDomain)
            (StringUtils/replace "@@APPID@@" appId)) "utf-8")

      (var-set fp (File. appDir "ivy.xml"))
      (FileUtils/writeStringToFile  ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@APPDOMAIN@@" appDomain)
            (StringUtils/replace "@@APPID@@" appId)) "utf-8")

      (var-set fp (File. appDir "build.xs"))
      (FileUtils/writeStringToFile  ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
            (StringUtils/replace "@@WEBCSSLANG@@" *SKARO-WEBCSSLANG*)
            (StringUtils/replace "@@WEBLANG@@" *SKARO-WEBLANG*)
            (StringUtils/replace "@@APPTYPE@@" flavor)
            (StringUtils/replace "@@SKAROHOME@@" (.getCanonicalPath hhhHome))) "utf-8")

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createBasic ""

  [^File hhhHome appId ^String appDomain]

  (create-app-common hhhHome appId appDomain "basic")
  (post-create-app hhhHome appId appDomain))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-web-common ""

  [^File hhhHome appId ^String appDomain]

  (let [ wfc (File. hhhHome (str DN_CFG "/app/weblibs.conf" ) )
         wbs (json/read-str (FileUtils/readFileToString wfc "utf-8")
                            :key-fn keyword)
         appDir (File. hhhHome (str "apps/" appId))
         wlib (doto (File. appDir "public/vendors") (.mkdirs))
         csslg *SKARO-WEBCSSLANG*
         wlg *SKARO-WEBLANG*
         buf (StringBuilder.)
         appDomainPath (.replace appDomain "." "/") ]

    (doseq [ s ["pages" "media" "scripts" "styles"]]
      (-> (File. appDir (str "src/web/main/" s)) (.mkdirs)))

    (doseq [ s ["pages" "media" "scripts" "styles"]]
      (-> (File. appDir (str "public/" s)) (.mkdirs)))

    (FileUtils/copyFileToDirectory (File. hhhHome "etc/web/cljsc.clj")
                                   (File. appDir "conf"))
    (FileUtils/copyFileToDirectory (File. hhhHome "etc/web/favicon.png")
                                   (File. appDir "src/web/main/media"))
    (FileUtils/copyFileToDirectory (File. hhhHome "etc/web/pipe.clj")
                                   (File. appDir (str "src/main/clojure/" appDomainPath)))

    (-> (File. appDir "src/test/js") (.mkdirs))

    (FileUtils/copyFile wfc (File. wlib ".list"))
    (doseq [ df (:libs wbs) ]
      (let [ dn (:dir df)
             dd (File. hhhHome (str "etc/weblibs/" dn))
             td (File. wlib ^String dn) ]
        (when (.isDirectory dd)
          (FileUtils/copyDirectoryToDirectory dd wlib)
          (when-not (:skip df)
            (doseq [ f (:js df) ]
              (-> buf (.append (FileUtils/readFileToString (File. td ^String f) "utf-8"))
                  (.append (str "\n\n/* @@@" f "@@@ */"))
                  (.append "\n\n")))))))

    (FileUtils/writeStringToFile (File. appDir "public/c/webcommon.js")
                                 (.toString buf)
                                 "utf-8")

    (FileUtils/writeStringToFile (File. appDir "public/c/webcommon.css")
                                 ""
                                 "utf-8")

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateJetty ""

  [^File hhhHome appId ^String appDomain]

  (let [ appDir (File. hhhHome (str "apps/" appId)) ]
    (create-app-common hhhHome appId appDomain "web")
    (create-web-common hhhHome appId appDomain)
    (doseq [ s [ "classes" "lib" ]]
      (-> (File. appDir (str "WEB-INF/" s)) (.mkdirs)))
    (FileUtils/copyFile (File. hhhHome "etc/jetty/jetty.conf")
                        (File. appDir "conf/env.conf"))
    (FileUtils/copyFileToDirectory (File. hhhHome "etc/jetty/web.xml")
                                   (File. appDir "WEB-INF"))
    (post-create-app hhhHome appId appDomain)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateWeb ""

  [^File hhhHome appId ^String appDomain]

  (let [ appDir (File. hhhHome (str "apps/" appId))
         appDomainPath (.replace appDomain "." "/") ]
    (with-local-vars [fp nil]
      (create-app-common hhhHome appId appDomain "web")
      (create-web-common hhhHome appId appDomain)
      (copy-files (File. hhhHome "etc/netty") (File. appDir "conf") "conf")
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/static-routes.conf")
                                     (File. appDir "conf"))
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/routes.conf")
                                     (File. appDir "conf"))

      (doseq [ s ["errors" "htmls"]]
        (-> (File. appDir (str "pages/" s)) (.mkdirs)))

      (copy-files (File. hhhHome "etc/netty") (File. appDir "pages/errors") ".err")
      (copy-files (File. hhhHome "etc/netty") (File. appDir "pages/htmls") "ftl")

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/index.html")
                                     (File. appDir "src/web/main/pages"))

      (var-set fp (File. appDir "conf/routes.conf"))
      (FileUtils/writeStringToFile ^File @fp
        (-> (FileUtils/readFileToString ^File @fp "utf-8")
          (StringUtils/replace "@@APPDOMAIN@@" appDomain)) "utf-8")

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/pipe.clj")
                                   (File. appDir (str "src/main/clojure/" appDomainPath)))

      (post-create-app hhhHome appId appDomain)
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cli-eof nil)

