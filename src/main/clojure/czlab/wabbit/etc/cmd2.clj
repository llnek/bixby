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

  (:require [czlab.xlib.format :refer [writeEdnStr readEdn]]
            [czlab.xlib.guids :refer [uuid<>]]
            [czlab.xlib.logging :as log]
            [czlab.xlib.antlib :as a]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.sys.core]
        [czlab.wabbit.etc.svcs]
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
;;
(defn getHomeDir
  ""
  ^File [] (io/file (sysProp "wabbit.home.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runPodBg
  "Run the application in the background"
  [homeDir podDir]
  (let
    [progW (io/file homeDir "bin/wabbit.bat")
     prog (io/file homeDir "bin/wabbit")
     tk (if (isWindows?)
          (a/antExec
            {:executable "cmd.exe"
             :dir podDir}
            [[:argvalues ["/C" "start" "/B"
                          "/MIN"
                          (fpath progW) "run"]]])
          (a/antExec
            {:executable (fpath prog)
             :dir podDir}
            [[:argvalues ["run" "bg"]]]))]
    (a/runTasks* tk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bundlePod
  "Bundle an app"
  [homeDir podDir outDir]
  (let [dir (mkdirs (io/file outDir))
        a (io/file podDir)]
    (->>
      (a/antZip
        {:destFile (io/file dir (str (.getName a) ".zip"))
         :basedir a
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
  ([podDir podDomain] (mkcljd podDir podDomain nil))
  ([podDir podDomain dir]
   (io/file podDir
            "src/main"
            (stror dir "clojure")
            (cs/replace podDomain "." "/"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fragSampleEmitter
  ""
  ^String
  [etype]
  (let [c (get-in *emitter-defs* [etype :conf])
        c (assoc c
                 :service etype
                 :handler "@@APPDOMAIN@@.core/dftHandler")]
    (-> (writeEdnStr c)
        (cs/replace "\n" "\n    "))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fragPlugin
  ""
  ^String
  [kind]
  (if (= :web kind)
    ":auth \"czlab.skaro.auth.plugin/AuthPluginFactory\""
    ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postConfigPod
  ""
  [podDir podId podDomain kind]
  (let
    [h2db (str (if (isWindows?)
                 "/c:/temp/" "/tmp/") (juid))
     h2dbUrl (str h2db
                  "/"
                  podId
                  ";MVCC=TRUE;AUTO_RECONNECT=TRUE")
     domPath (cs/replace podDomain "." "/")
     hhh (getHomeDir)
     cljd (mkcljd podDir podDomain)
     p (fragPlugin kind)
     se (if (= :web kind)
          (fragSampleEmitter :czlab.wabbit.io.http/WebMVC)
          (fragSampleEmitter :czlab.wabbit.io.loops/OnceTimer))]
    (mkdirs h2db)
    (replaceFile!
      (io/file podDir CFG_POD_CF)
      #(-> (cs/replace % "@@SAMPLE-EMITTER@@" se)
           (cs/replace "@@AUTH-PLUGIN@@" p)
           (cs/replace "@@H2DBPATH@@" h2dbUrl)
           (cs/replace "@@APPDOMAIN@@" podDomain)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyOnePod
  ""
  [outDir podId podDomain kind]
  (let
    [domPath (cs/replace podDomain "." "/")
     podDir (mkdirs (io/file outDir podId))
     other (if (= :soa kind) :web :soa)
     srcDir (io/file podDir "src")
     mcloj "main/clojure"
     mjava "main/java"
     hhh (getHomeDir)]
    (FileUtils/copyDirectory
      (io/file hhh DN_ETC "app")
      podDir
      (FileFilterUtils/trueFileFilter))
    (when (= :soa kind)
      (doall
        (map #(->> (io/file podDir %)
                   (FileUtils/deleteDirectory ))
             ["src/web" "public"])))
    (doall
      (map #(mkdirs (io/file podDir
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
(defn- configOnePod
  ""
  [outDir podId podDomain kind]
  (let
    [domPath (cs/replace podDomain "." "/")
     podDir (io/file outDir podId)
     srcDir (io/file podDir "src")
     verStr "0.1.0-SNAPSHOT"
     hhh (getHomeDir)]
    (doseq [f (FileUtils/listFiles srcDir nil true)]
      (replaceFile!
        f
        #(-> (cs/replace % "@@USER@@" (getUser))
             (cs/replace "@@APPDOMAIN@@" podDomain))))
    (replaceFile!
      (io/file podDir CFG_POD_CF)
      #(-> (cs/replace % "@@USER@@" (getUser))
           (cs/replace "@@APPKEY@@" (uuid<>))
           (cs/replace "@@VER@@" verStr)
           (cs/replace "@@APPDOMAIN@@" podDomain)))
    (replaceFile!
      (io/file srcDir "main/resources/pom.xml")
      #(-> (cs/replace % "@@APPDOMAIN@@" podDomain)
           (cs/replace "@@VER@@" verStr)
           (cs/replace "@@APPID@@" podId)))
    (replaceFile!
      (io/file podDir "build.boot")
      #(-> (cs/replace % "@@APPDOMAIN@@" podDomain)
           (cs/replace "@@TYPE@@" (name kind))
           (cs/replace "@@VER@@" verStr)
           (cs/replace "@@APPID@@" podId)))
    (postConfigPod podDir podId podDomain kind)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn createPod
  "Create a new app"
  [option path]
  (let
    [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
     path (strimAny path ".")
     t (re-matches rx path)
     cwd (getCwd)
     kind
     (case option
       ("-w" "--web") :web
       ("-s" "--soa") :soa
       (trap! CmdHelpError))
     ;; treat as domain e.g com.acme => pod = acme
     ;; regex gives ["com.acme" ".acme"]
     pod (when (some? t)
           (if-some [tkn (last t)]
             (triml tkn ".")
             (first t)))]
    (if (empty? pod) (trap! CmdHelpError))
    (copyOnePod cwd pod path kind)
    (configOnePod cwd pod path kind)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo
  ""
  [^File demo ^File out]
  (let [top (io/file out (.getName demo))
        dn (.getName top)
        podDomain (mkDemoPath dn)
        domPath (cs/replace podDomain "." "/")
        kind (if (in? #{"mvc" "http"} dn) :web :soa)]
    (prn!! "Generating: %s..." podDomain)
    (copyOnePod out dn podDomain kind)
    (FileUtils/copyDirectory
      demo
      (io/file top DN_CONF)
      (FileFilterUtils/suffixFileFilter ".conf"))
    (FileUtils/copyDirectory
      demo
      (io/file top "src/main/clojure" domPath)
      (FileFilterUtils/suffixFileFilter ".clj"))
    (configOnePod out
                  dn
                  podDomain kind)))

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


