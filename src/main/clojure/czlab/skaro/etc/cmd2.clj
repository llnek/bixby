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
    [czlab.xlib.format :refer [writeEdnString readEdn]]
    [czlab.xlib.guids :refer [uuid<>]]
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
             spitUTF8
             writeFile
             dirRead?
             touch!
             mkdirs]])

  (:use [czlab.skaro.sys.core]
        [czlab.skaro.etc.svcs])

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
(defmacro cpfs
  ""
  ^{:private true}
  [s d u]

  `(FileUtils/copyDirectory
     (io/file ~s)
     (io/file ~d)
     (FileFilterUtils/suffixFileFilter (str ~u))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro cpf2d
  ""
  ^{:private true}
  [f d]

  `(FileUtils/copyFileToDirectory (io/file ~f) (io/file ~d)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro cpf2f
  ""
  ^{:private true}
  [f f2]

  `(FileUtils/copyFile (io/file ~f) (io/file ~f2)))

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
(defn- onSvc

  ""
  [id hint & [svc]]

  (let
    [fp (io/file (getCwd) CFG_APP_CF)
     cf (readEdn fp)
     root (:services cf)
     nw
     (if (< hint 0)
       (dissoc root id)
       (when-some
         [gist (:conf (*emitter-defs* svc))]
         (when (contains? root id) (trap! CmdHelpError))
         (assoc root id (assoc gist :service svc))))]
    (when (some? nw)
      (->> (assoc cf :services nw)
           (writeEdnString)
           (spitUTF8 fp)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onService

  ""
  [args]

  (when (< (count args) 2) (trap! CmdHelpError))
  (let [cmd (args 0)
        id (keyword (args 1))
        [hint svc]
        (case (keyword cmd)
          :remove [-1 "?"]
          :add (if (< (count args) 3)
                 (trap! CmdHelpError)
                 [1 (args 2)])
          (trap! CmdHelpError))
        t (case (keyword svc)
            :repeat :czlab.skaro.io.loops/RepeatingTimer
            :once :czlab.skaro.io.loops/OnceTimer
            :files :czlab.skaro.io.files/FilePicker
            :http :czlab.skaro.io.netty/NettyMVC
            :pop3 :czlab.skaro.io.mails/POP3
            :imap :czlab.skaro.io.mails/IMAP
            :tcp :czlab.skaro.io.socket/Socket
            :jms :czlab.skaro.io.jms/JMS
            :? nil
            (trap! CmdHelpError))]
    (onSvc id hint t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain

  ""
  ^String
  [appDomain]

  (strimAny appDomain "."))

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
        domPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        cljd (mkcljd appDir appDomain)]
    (mkdirs h2db)
    (replaceFile!
      (mkcljfp cljd "core.clj")
      #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
           (cs/replace "@@USER@@" (getUser))))
    (replaceFile!
      (io/file appDir CFG_APP_CF)
      #(-> (cs/replace % "@@H2DBPATH@@" h2dbUrl)
           (cs/replace "@@APPDOMAIN@@" appDomain)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createAppCommon

  ""
  [^File appDir ^String appId ^String appDomain]

  (let [domPath (cs/replace appDomain "." "/")
        cfd (io/file appDir DN_CONF)
        etc (io/file appDir DN_ETC)
        hhh (getHomeDir)]
    ;; make all the folders
    (doseq [s [;; "patch" "build" "target" "logs"
               "ext" "i18n" DN_CONF DN_ETC "src"]]
      (mkdirs (io/file appDir s)))
    (doseq [s ["clojure" "java"]]
      (map #(mkdirs %)
           (map #(io/file appDir "src" % s domPath))))
    (mkdirs (io/file appDir "src/main/resources"))
    ;;copy files
    (cpf2d
      (io/file hhh DN_CFGAPP "shiro.ini")
      (io/file appDir "ext"))
    (cpf2f
      (io/file hhh DN_ETC "log/log4jbuild.properties")
      (io/file appDir DN_ETC "log4j.properties"))
    (cpf2d
      (io/file hhh DN_CFGAPP "test.clj")
      (io/file appDir "src/test/clojure" domPath))
    (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
      (cpf2d
        (io/file hhh DN_CFGAPP s)
        (io/file appDir "src/test/java" domPath)))
    (cpf2d (io/file hhh DN_CFGAPP "build.boot") appDir)
    (cpf2d (io/file hhh DN_CFGAPP "pom.xml")
           (io/file appDir DN_ETC))
    (cpf2f
      (io/file hhh DN_ETC "lics/LICENSE")
      (io/file appDir DN_ETC "LICENSE.TXT"))
    (touch! (io/file appDir DN_ETC "README.md"))
    (cpf2d (io/file hhh DN_CFGAPP APP_CF) cfd)
    (cpf2d
      (io/file hhh DN_CFGAPP DN_RCPROPS)
      (io/file  appDir "i18n"))
    (cpf2d
      (io/file hhh DN_CFGAPP "core.clj")
      (mkcljd appDir appDomain))
    ;;modify files, replace placeholders
    (replaceFile!
      (io/file appDir
               "src/test/clojure" domPath "test.clj")
      #(cs/replace % "@@APPDOMAIN@@" appDomain))
    (doseq [s ["ClojureJUnit.java" "JUnit.java"]]
      (replaceFile!
        (io/file appDir "src/test/java" domPath s)
        #(cs/replace % "@@APPDOMAIN@@" appDomain)))
    (replaceFile!
      (io/file cfd APP_CF)
      #(-> (cs/replace % "@@USER@@" (getUser))
           (cs/replace "@@APPKEY@@" (uuid<>))
           (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
           (cs/replace "@@APPDOMAIN@@" appDomain)))
    (replaceFile!
      (io/file appDir DN_ETC "pom.xml")
      #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
           (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
           (cs/replace "@@APPID@@" appId)))
    (replaceFile!
      (io/file appDir "build.boot")
      #(-> (cs/replace % "@@APPDOMAIN@@" appDomain)
           (cs/replace "@@TYPE@@" "web")
           (cs/replace "@@VER@@" "0.1.0-SNAPSHOT")
           (cs/replace "@@APPID@@" appId)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createWebCommon

  ""
  [^File appDir appId ^String appDomain]

  (let [domPath (cs/replace appDomain "." "/")
        hhh (getHomeDir)
        web (io/file hhh DN_CFGWEB)]
    ;; make folders
    (doseq [s ["pages" "media" "styles" "scripts"]]
      (mkdirs (io/file appDir "src/web/main" s))
      (mkdirs (io/file appDir "public" s)))
    ;; copy files
    (let [des (io/file appDir "src/web/main/pages")
          src (io/file hhh DN_ETC "netty")]
      (cpfs src des ".ftl")
      (cpfs src des ".html"))

    (touch!
      (io/file appDir "src/web/main/styles/main.scss"))
    (touch!
      (io/file appDir "src/web/main/scripts/main.js"))
    (cpf2d
      (io/file web "favicon.png")
      (io/file appDir "src/web/main/media"))
    (cpf2d
      (io/file hhh DN_ETC "netty/core.clj")
      (mkcljd appDir appDomain))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- createOneApp

  ""
  [^File out appId ^String appDomain]

  (let [appDir (mkdirs (io/file out appId))
        hhh (getHomeDir)
        cfd (io/file appDir DN_CONF)]
    (createAppCommon appDir appId appDomain)
    (createWebCommon appDir appId appDomain)
    ;; copy files
    (cpfs (io/file hhh DN_ETC "netty") cfd ".conf")
    ;; modify files
    (replaceFile!
      (io/file cfd "routes.conf")
      #(cs/replace % "@@APPDOMAIN@@" appDomain))
    (postCreateApp appDir appId appDomain)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn createApp

  "Create a new app"
  [path]

  (let
    [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
     path (sanitizeAppDomain path)
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF





