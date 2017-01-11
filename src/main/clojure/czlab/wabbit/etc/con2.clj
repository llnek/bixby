;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.etc.con2

  (:require [czlab.xlib.format :refer [writeEdnStr readEdn]]
            [czlab.xlib.guids :refer [uuid<>]]
            [czlab.xlib.logging :as log]
            [czlab.antclj.antlib :as a]
            [clojure.string :as cs]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.etc.core]
        [czlab.wabbit.etc.svcs]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [org.apache.commons.io.filefilter FileFilterUtils]
           [org.apache.commons.io FileUtils]
           [java.util ResourceBundle UUID]
           [czlab.wabbit.etc CmdError]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

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
    ":auth \"czlab.wabbit.pugs.auth.core/pluginFactory<>\""
    ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postCopyPod
  ""
  [podDir podId podDomain kind]
  (let
    [h2db (str (if (isWindows?)
                 "/c:/temp/" "/tmp/") (juid))
     h2dbUrl (str h2db
                  "/"
                  podId
                  ";MVCC=TRUE;AUTO_RECONNECT=TRUE")
     p (fragPlugin kind)
     se (if (= :web kind)
          (fragSampleEmitter :czlab.wabbit.io.http/WebMVC)
          (fragSampleEmitter :czlab.wabbit.io.loops/OnceTimer))]
    (mkdirs h2db)
    (replaceFile!
      (io/file podDir cfg-pod-cf)
      #(-> (cs/replace % "@@SAMPLE-EMITTER@@" se)
           (cs/replace "@@AUTH-PLUGIN@@" p)
           (cs/replace "@@H2DBPATH@@" h2dbUrl)))))

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
      (io/file hhh dn-etc "app")
      podDir
      (FileFilterUtils/trueFileFilter))
    (when (= :soa kind)
      (doall
        (map #(->> (io/file podDir %)
                   (FileUtils/deleteDirectory ))
             ["src/web" "public" "ext"])))
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
      (io/file srcDir mjava domPath) true)
    (postCopyPod podDir podId podDomain kind)))

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
    (doseq [f (FileUtils/listFiles podDir nil true)]
      (replaceFile!
        f
        #(-> (cs/replace % "@@USER@@" (getUser))
             (cs/replace "@@APPDOMAIN@@" podDomain)
             (cs/replace "@@APPKEY@@" (uuid<>))
             (cs/replace "@@VER@@" verStr)
             (cs/replace "@@APPID@@" podId)
             (cs/replace "@@TYPE@@" (name kind)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new pod?
(defn createPod
  "Create a new pod"
  [option path]
  (let
    [rx #"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)*"
     path (strimAny path ".")
     t (re-matches rx path)
     cwd (getProcDir)
     kind
     (case option
       ("-w" "--web") :web
       ("-s" "--soa") :soa
       (trap! CmdError))
     ;; treat as domain e.g com.acme => pod = acme
     ;; regex gives ["com.acme" ".acme"]
     pod (when (some? t)
           (if-some [tkn (last t)]
             (triml tkn ".")
             (first t)))]
    (if (empty? pod) (trap! CmdError))
    (copyOnePod cwd pod path kind)
    (configOnePod cwd pod path kind)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneCljDemo
  ""
  [^File demo out]
  (let
    [top (io/file out (.getName demo))
     dn (.getName top)
     podDomain (mkDemoPath dn)
     domPath (cs/replace podDomain "." "/")
     kind (if (in? #{"mvc" "http"} dn) :web :soa)]
    (prn!! "Generating: %s..." podDomain)
    (copyOnePod out dn podDomain kind)
    (FileUtils/copyDirectory
      demo
      (io/file top dn-conf)
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


