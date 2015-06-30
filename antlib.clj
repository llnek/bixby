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

  antlib.tasks

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr]
            [boot.core :as bcore])

  (:import [org.apache.commons.exec CommandLine DefaultExecutor]
           [org.apache.commons.io FileUtils]
           [java.util Map HashMap Stack]
           [java.io File]
           [org.apache.tools.ant.taskdefs Javadoc Java Copy Jar Zip ExecTask Javac]
           [org.apache.tools.ant.listener TimestampedLogger]
           [org.apache.tools.ant.types Reference
            Commandline$Argument
            PatternSet$NameEntry
            Environment$Variable FileSet Path DirSet]
           [org.apache.tools.ant Project Target Task]
           [org.apache.tools.ant.taskdefs Javadoc$AccessType
            Javac$ImplementationSpecificArgument]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntFileSet "Create a FileSet Object."

  ^FileSet
  [options nested]

  (let [fs (FileSet.)]
    (doseq [[k v] options]
      (case k
        :erroronmissingdir (.setsetErrorOnMissingDir fs v)
        :dir (.setDir fs (io/file v))
        nil))
    (doseq [p nested]
      (case (first p)
        :include (-> ^PatternSet$NameEntry
                     (.createInclude fs)
                     (.setName (str (last p))))
        :exclude (-> ^PatternSet$NameEntry
                     (.createExclude fs)
                     (.setName (str (last p))))
        nil))
    fs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecTarget "Run and execute a target."

  [^Target target]

  (.executeTarget (.getProject target) target))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTasks "Bootstrap ant tasks with a target & project."

  ^Target
  [^String target & tasks]

  (let [lg (doto (TimestampedLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO))
        pj (doto (Project.)
             (.setName "proj-anonymous")
             (.init))
        tg (doto (Target.)
             (.setName (or target "mi6"))) ]
    (doto pj
      (.addTarget tg)
      (.addBuildListener lg))
    (doseq [t tasks]
      (doto t
        (.setProject pj)
        (.setOwningTarget tg))
      (.addTask tg t))
    tg
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetClassPath "Build a nested Path structure for classpath."

  [^Path root paths]

  (doseq [p paths]
    (case (first p)
      :location
      (doto (.createPath root)
        (.setLocation (io/file (last p))))
      :refid
      (throw (Exception. "path:refid not supported."))
      ;;(doto (.createPath root) (.setRefid (last p)))
      :fileset
      (->> (AntFileSet (nth p 1) (nth p 2))
           (.addFileset root))
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJavac "Ant javac task."

  ^Task
  [^Project pj options nested]

  (let [tk (doto (Javac.)
                 (.setTaskName "javac"))]
    (doto tk
      (.setIncludeantruntime (true? :includeantruntime options))
      (.setDebugLevel (:debuglevel options))
      (.setFork (true? (:fork options)))
      (.setDebug (:debug options))
      (.setTarget (:target options))
      (.setSrcdir (Path. pj (:srcdir options)))
      (.setDestdir (io/file (:destdir options))))

    (doseq [p nested]
      (case (first p)
        :compilerarg
        (when-let [line (:line (last p))]
          (-> (.createCompilerArg tk)
              (.setLine line)))
        :classpath
        (-> (.createClasspath tk)
            (SetClassPath (last p)))
        :files
        (doseq [n (last p)]
          (case (first n)
            :include
            (-> (.createInclude tk)
                (.setName (last n)))
            :exclude
            (-> (.createExclude tk)
                (.setName (last n)))
            nil))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJavaDoc "Ant javadoc task."

  ^Task
  [^Project options nested]

  (let [tk (doto (Javadoc.)
                 (.setTaskName "javadoc"))]
    (doto tk
      (.setDestdir (io/file (:destdir options)))
      (.setAccess (doto (Javadoc$AccessType.)
                        (.setValue (:access options))))
      (.setAuthor (true? (:author options)))
      (.setNodeprecated (true? (:nodeprecated options)))
      (.setNodeprecatedlist (true? (:nodeprecatedlist options)))
      (.setNoindex (true? (:noindex options)))
      (.setNonavbar (true? (:nonavbar options)))
      (.setNotree (true? (:notree options)))
      (.setSource (or (:source options) "1.8"))
      (.setSplitindex (true? (:splitindex options)))
      (.setUse (true? (:use options)))
      (.setVersion (true? (:version options))))

    (doseq [p nested]
      (case (first p)
        :classpath
        (-> (.createClasspath tk)
            (SetClassPath (last p)))
        :fileset
        (->> (AntFileSet (nth p 1) (nth p 2))
           (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntDelete "Ant delete task."

  [^Project pj options nested]

  (let [tk (doto (Delete.)
                 (.setTaskName "delete"))]
    (when-let [[k v] (find options :file)]
      (.setFile tk (io/file v)))
    (when-let [[k v] (find options :dir)]
      (.setDir tk (io/file v)))
    (doto tk
      (.setQuiet (not (false? (:quiet options)))))
    (doseq [p nested]
      (case (first p)
        :fileset
        (->> (AntFileSet (nth p 1) (nth p 2))
           (.addFileset tk))
        nil))
    tk
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntCopy "Ant copy task."

  [^Project pj options nested]

  (let [tk (doto (Copy.)
                 (.setTaskName "copy"))]
    (doto tk
      (.setTodir (io/file toDir)))
    (doseq [r res]
      (case (first r)
        :fileset
        (->> (fileSet (rest r))
             (.addFileset tk))
        nil))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJar ""

  [jarFile res]

  (let [tk (Jar.)
        pj (ProjAntTask tk) ]
    (doto tk
      (.setTaskName "jar-task")
      (.setDestFile (io/file jarFile)))
    (doseq [r res]
      (case (first r)
        :fileset
        (->> (fileSet (rest r))
             (.addFileset tk))
        nil))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJava ""

  [clazz options]

  ;;(println "java options:\n" options)
  (let [tk (Java.)
        pj (ProjAntTask tk) ]
    (doto tk
      (.setFailonerror (true? (:failonerror options)))
      (.setMaxmemory (get options :maxmemory "1024m"))
      (.setTaskName "java")
      (.setFork (true? (:fork options)))
      (.setClassname clazz))
    (doseq [[k v] (:sysprops options)]
      (->> (doto (Environment$Variable.)
                 (.setKey (name k))
                 (.setValue (str v)))
           (.addSysproperty tk)))
    (-> (.createClasspath tk)
        (setClassPath (:cp options)))
    (doseq [a (:args options)]
      (-> (.createArg tk)
          (.setValue (str a))))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro minitask
  [func & forms]
  `(do
     (println (str ~func ":"))
     ~@forms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanDir "" [^File dir]
  (if (.exists dir)
    (FileUtils/cleanDirectory dir)
    (.mkdirs dir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- deleteDir "" [^File dir]
  (when (.exists dir)
    (FileUtils/deleteDirectory dir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- clean4Build ""
  [& args]
  (minitask
    "clean4Build"
    (cleanDir (io/file @basedir (get-env :target-path)))
    (cleanDir (io/file @basedir @gantBuildDir))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanBoot ""
  [& args]
  (minitask
    "cleanBoot"
    (cleanDir (io/file @basedir
                       (get-env :target-path)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preBuild ""
  [& args]
  (minitask
    "preBuild"
    (let []
      (doseq [s [(str @distribDir "/boot")
                 (str @distribDir "/exec")
                 (str @libDir)
                 @qaDir
                 @buildDir]]
        (.mkdirs (io/file s)))
      ;; get rid of debug logging during build!
      (FileUtils/copyFileToDirectory (io/file @basedir "log4j.properties")
                                     (io/file @buildDir))
      (FileUtils/copyFileToDirectory (io/file @basedir "logback.xml")
                                     (io/file @buildDir)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCmd ""

  [cmd workDir args]

  (let [xtor (DefaultExecutor.)
        cli (CommandLine. cmd)]
    (.setWorkingDirectory xtor (io/file workDir))
    (doseq [a (or args [])]
      (.addArgument cli a))
    (.execute xtor cli)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babelFile "" [mid]
  (let [out (io/file @buildDir "js")
        dir (io/file @srcDir "js")
        fp (io/file dir @bldDir mid)]
    (if (.endsWith mid ".js")
      (do
        (runCmd "babel"
                dir
                ["--modules" "amd" "--module-ids"
                 mid "--out-dir" @bldDir])
        (spit fp
              (-> (slurp (io/file dir @bldDir mid))
                  (.replaceAll "\\/\\*@@" "")
                  (.replaceAll "@@\\*\\/" ""))))
      (let [des (io/file dir @bldDir mid)]
        (FileUtils/copyFileToDirectory (io/file dir mid)
                                       (.getParentFile des))))
    (FileUtils/moveFileToDirectory fp
                                   (doto (-> (io/file out mid)
                                             (.getParentFile))
                                       (.mkdirs))
                                   true)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jsWalkTree ""

  [^Stack stk seed]

  (let [top (if-not (nil? seed) seed (.peek stk))
        skip @bldDir]
    (doseq [f (.listFiles top)]
      (cond
        (= skip (.getName f))
        nil
        (.isDirectory f)
        (do
          (.push stk f)
          (jsWalkTree stk nil))
        :else
        (let [path (if (.empty stk)
                     ""
                     (cstr/join "/" (for [x (.toArray stk)] (.getName x))))
              fid (.getName f)]
          (-> (if (> (.length path) 0)
                (str path "/" fid)
                fid)
              (babelFile )))))
    (when-not (.empty stk)
      (.pop stk))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildJSLib ""

  [& args]

  (let [ljs (io/file @srcDir "js" @bldDir)
        root (io/file @srcDir "js")]
    (cleanDir ljs)
    (try
      (jsWalkTree (Stack.) root)
      (finally
        (deleteDir ljs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileAndJar ""

  [options]

  (let [options {:srcdir (str @srcDir "/java")
                 :destdir @buildDir
                 :target "1.8"
                 :debug @buildDebug
                 :fork true
                 :debuglevel "lines,vars,source"
                 :cp [[:location (str @srcDir "/clojure")]
                      [:location @cljBuildDir]
                      [:location @buildDir]
                      [:fileset @libDir [[:include "*.jar"]]] ]
                 :compilerarg {:line "-Xlint:deprecation -Xlint:unchecked"}
                 :files [[:include "com/zotohlab/frwk/**/*.java"]]
                 }]
    (-> (AntJavac options)
        (ExecProj))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(task-options!
  uber {:as-jars true}
  aot {:all true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juberXX
  "my own uber"
  []
  (bcore/with-pre-wrap fileset
    (let [odir (io/file @libDir)]
      (println "copying jars to " @libDir)
      (doseq [f (seq (output-files fileset))]
        (FileUtils/copyFileToDirectory
          (io/file (:dir f) (:path f))
          odir)))
    fileset
  ))

(deftask juber
  "my own uber"
  []
  (bcore/with-pre-wrap fileset
    (let [from (io/file @basedir (get-env :target-path))
          jars (output-files fileset)
          to (io/file @basedir @libDir)]
      (println "copying (" (count jars) ") jar-files to " to)
      (doseq [j (seq jars)]
        (FileUtils/copyFileToDirectory (io/file (:dir j) (:path j))
                                       to)))
    fileset
  ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask resolve-jars
  ""
  []
  (comp (uber) (juber)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwk
  ""
  []

  (->> {:srcdir (str @srcDir "/java")
        :files [[:include "com/zotohlab/frwk/**/*.java"]]}
       (merge JAVAC_OPTS)
       (AntJavac)
       (ExecProj))

  (->> [[:fileset (str @srcDir "/java/com/zotohlab/frwk")
         [[:exclude "**/*.java"]]]]
       (AntCopy (str @buildDir "/com/zotohlab/frwk"))
       (ExecProj))

  (->> [[:fileset @buildDir
         [[:include "com/zotohlab/frwk/**"]
          [:exclude "**/log4j.properties"]
          [:exclude "**/logback.xml"]
          [:exclude "demo/**"]]]]
       (AntJar (str @distribDir "/exec/frwk-" @buildVersion ".jar"))
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileWFlow
  ""
  []

  (->> {:srcdir (str @srcDir "/java")
        :files [[:include "com/zotohlab/wflow/**/*.java"]
                [:include "com/zotohlab/server/**/*.java"]]}
       (merge JAVAC_OPTS)
       (AntJavac)
       (ExecProj))

  (->> [[:fileset (str @srcDir "/java/com/zotohlab/server")
         [[:exclude "**/*.java"]]]
        [:fileset (str @srcDir "/java/com/zotohlab/wflow")
         [[:exclude "**/*.java"]]]]
       (AntCopy (str @buildDir "/com/zotohlab/wflow"))
       (ExecProj))

  (->> [[:fileset @buildDir
         [[:include "com/zotohlab/server/**"]
          [:include "com/zotohlab/wflow/**"]
          [:exclude "**/log4j.properties"]
          [:exclude "**/logback.xml"]
          [:exclude "demo/**"]]]]
       (AntJar (str @distribDir "/exec/wflow-" @buildVersion ".jar"))
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSkaro
  ""
  []

  (->> {:srcdir (str @srcDir "/java")
        :files [[:include "com/zotohlab/skaro/**/*.java"]
                [:include "com/zotohlab/mock/**/*.java"]
                [:include "com/zotohlab/tpcl/**/*.java"]]}
       (merge JAVAC_OPTS)
       (AntJavac)
       (ExecProj))

  (doseq [d ["skaro" "mock" "tpcl"]]
    (->> [[:fileset (str @srcDir "/java/com/zotohlab/" d)
           [[:exclude "**/*.java"]]]]
         (AntCopy (str @buildDir "/com/zotohlab/" d))
         (ExecProj)))

  (->> [[:fileset @buildDir
         [[:include "com/zotohlab/skaro/loaders/**"] ]]]
       (AntJar (str @distribDir "/boot/cls-" @buildVersion ".jar"))
       (ExecProj))

  (->> [[:fileset @buildDir
         [[:include "com/zotohlab/skaro/**"]
          [:include "com/zotohlab/mock/**"]
          [:include "com/zotohlab/tpcl/**"]
          [:exclude "**/log4j.properties"]
          [:exclude "**/logback.xml"]
          [:exclude "demo/**"]]]]
       (AntJar (str @distribDir "/exec/gfy-" @buildVersion ".jar"))
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJavaDemo
  ""
  []

  (->> {:srcdir (str @srcDir "/java")
        :files [[:include "demo/**/*.java"]]}
       (merge JAVAC_OPTS)
       (AntJavac)
       (ExecProj))

  (doseq [d ["splits" "flows" ]]
    (->> [[:fileset (str @srcDir "/java/demo/" d)
           [[:exclude "**/*.java"]]]]
         (AntCopy (str @buildDir "/demo/" d))
         (ExecProj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtCljNsps
  ""
  [& dirs]
  (reduce
    (fn [memo dir]
      (let [nsp (cstr/replace dir #"\/" ".")]
        (concat
          memo
          (map #(str nsp "." (cstr/replace (.getName %) #"\.[^\.]+$" ""))
               (filter #(and (.isFile %)(.endsWith (.getName %) ".clj"))
                       (.listFiles (io/file @srcDir "clojure" dir)))))))
    []
    dirs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljJMX
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/xlib/jmx")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljCrypto
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/xlib/crypto")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDbio
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/xlib/dbio")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljNet
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/xlib/netty"
                          "czlabclj/xlib/net")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljUtil
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/xlib/util"
                          "czlabclj/xlib/i18n")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljXLib
  ""
  []

  ((comp cljJMX cljNet cljDbio cljCrypto cljUtil))

  (->> [[:fileset (str @srcDir "/clojure/czlabclj/xlib")
         [[:exclude "**/*.clj"]]]]
       (AntCopy (str @buildDir "/czlabclj/xlib"))
       (ExecProj))

  (->> [[:fileset @buildDir
         [[:include "czlabclj/xlib/**"]
          [:exclude "**/log4j.properties"]
          [:exclude "**/logback.xml"]
          [:exclude "demo/**"]]]]
       (AntJar (str @distribDir "/exec/xlib-" @buildVersion ".jar"))
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDemo
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "demo.file"
                          "demo.fork"
                          "demo.http"
                          "demo.jetty"
                          "demo.jms"
                          "demo.mvc"
                          "demo.pop3"
                          "demo.steps"
                          "demo.tcpip"
                          "demo.timer")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj))
  (->> [[:fileset (str @srcDir "/clojure/demo")
         [[:exclude "**/*.clj"]]]]
       (AntCopy (str @buildDir "/demo"))
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMain
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/tardis/impl")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMvc
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/tardis/mvc")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAuth
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/tardis/auth")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisIO
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/tardis/io")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisEtc
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/tardis/etc")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisCore
  ""
  [& args]
  (->> {:sysprops {:clojure.compile.warn-on-reflection true
                   :clojure.compile.path @buildDir}
        :args (fmtCljNsps "czlabclj/tardis/core")
        :maxmemory "2048m" }
       (merge CLJC_OPTS )
       (AntJava "clojure.lang.Compile")
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAll
  ""
  []

  ((comp tardisMain tardisMvc tardisIO tardisAuth tardisEtc tardisCore))

  (->> [[:fileset (str @srcDir "/clojure/czlabclj/tardis")
         [[:exclude "**/*.meta"]
          [:exclude "**/*.clj"]]]]
       (AntCopy (str @buildDir "/czlabclj/tardis"))
       (ExecProj))

  (->> [[:fileset @buildDir
         [[:include "czlabclj/tardis/**"] ]]]
       (AntJar (str @distribDir "/exec/tardis-" @buildVersion ".jar"))
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- distroInit ""
  []
  (let [root (io/file @basedir @packDir)]
    (cleanDir root)
    (doseq [d ["conf" ["dist" "boot"] ["dist" "exec"] "bin"
               ["etc" "ems"] "lib" "logs" "docs" "pods" "tmp" "apps"]]
      (.mkdirs (if (vector? d) (apply io/file root d) (io/file root d))))
    (spit (io/file root "VERSION") @buildVersion)
    (->> [[:fileset (str @basedir "/etc") []]]
         (AntCopy (str @packDir "/etc"))
         (ExecProj))
    (->> [[:fileset (str @basedir "/etc/conf") []]]
         (AntCopy (str @packDir "/conf"))
         (ExecProj))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""
  []
  (->> [[:fileset (str @srcDir "/clojure")
         [[:include "**/*.meta"] ]]]
       (AntCopy {:todir (str @packDir "/etc/ems") :flatten true} )
       (ExecProj))
  (->> [[:fileset (str @basedir "/etc") []]]
       (AntCopy {:todir (str @packDir "/etc") } )
       (ExecProj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDocs ""
  []
  (AntDelete {:dir (str @packDir "/docs/jsdoc") :quiet true} )
  (.mkdirs (io/file @packDir "docs" "jsdoc"))
  (AntDelete {:dir (str @packDir "/docs/api")  :quiet true} )
  (.mkdirs (io/file @packDir "docs" "api"))

  (->> [[:fileset {:dir (str @basedir "/docs")
                   :erroronmissingdir false }
         [[:exclude "dummy.txt"] ]]]
       (AntCopy {:todir (str @packDir "/docs") }))

  (copyJsFiles)

  (AntExec {:executable "jsdoc"
            :dir @basedir
            :spawn true
            :args ["-c"
                   "mvn/js/jsdoc-conf.json"
                   "-d"
                   (str @packDir "/docs/jsdoc")]})

  (->> [[:fileset {:dir (str @srcDir "/java") }
         [[:include "**/*.java"]
          [:exclude "demo/**"]]]]
    (AntJavaDoc {:destdir (str @packDir "/docs/api")
                 :access "protected"
                 :author true
                 :nodeprecated false
                 :nodeprecatedlist false
                 :noindex false
                 :nonavbar false
                 :notree false
                 :source "1.8"
                 :splitindex true
                 :use true
                 :version true
                 :cp [] }))

  (AntJava {:classname  "clojure.lang.Compile"
            :fork  true
            :failonerror true
            :maxmemory "2048m"
            :cp []
            :sysprops {:clojure.compile.warn-on-reflection true
                       :clojure.compile.path @buildDir}
            :args ["czlabclj.xlib.util.codox"] })

  (AntJava {:classname "czlabclj.xlib.util.codox"
            :fork true
            :failonerror true
            :classpath []
            :args [@basedir
                   (str @srcDir "/clojure")
                   (str @packDir "/docs/api")] }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packSrc ""
  []

  (->> [[:fileset {:dir (str @srcDir "/clojure") } []]]
       (AntCopy {:todir (str @packDir "/src/main/clojure") } ))

  (->> [[:fileset {:dir (str @srcDir "/java") } []]]
       (AntCopy {:todir (str @packDir "/src/main/java") } )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLics ""
  []

  (->> [[:fileset {:dir (str @basedir "/lics")
                   :erroronmissingdir false}
         []]]
       (AntCopy {:todir (str @packDir "/lics") } ))

  (->> [[:fileset {:dir @basedir }
         [[:include "*.html"]
          [:include "*.txt"]
          [:include "*.md"]]]]
       (AntCopy {:todir @packDir :flatten true} )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDist ""
  []

  (->> [[:fileset {:dir (str @distribDir "/exec") }
         [[:include "*.jar"]]]]
       (AntCopy {:todir (str @packDir "/dist/exec") } ))

  (->> [[:fileset {:dir (str @distribDir "/boot") }
         [[:include "*.jar"]]]]
       (AntCopy {:todir (str @packDir "/dist/boot") } ))

  (->> [[:fileset {:dir @cljBuildDir}
         [[:include "clojure/**"]]]
        [:fileset {:dir @buildDir}
         [[:include "clojure/**"]]]]
       (AntJar {:destfile (str @packDir "/dist/exec/clj-" @buildVersion ".jar") }))

  (copyJsFiles))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLibs ""
  []

  (->> [[:fileset {:dir (str @libDir "/libjar") } []]]
    (AntCopy {:todir (str @packDir "/lib") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""
  []

  (->> [[:fileset {:dir (str @basedir "/bin")
                   :erroronmissingdir false}
         [[:exclude ".svn"]]]]
       (AntCopy {:todir (str @packDir "/bin") }))

  (AntChmod {:dir (str @packDir "/bin")
             :perm "755"
             :includes "*"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packAll ""
  []
  (AntDelete {:dir (str @packDir "/tmp") })
  (.mkdirs (io/file @packDir "tmp"))

  (->> [[:tarfileset {:dir @packDir}
         [[:exclude "apps/**"]
          [:exclude "bin/**"]]]
        [:tarfileset {:dir @packDir :filemode "755"}
         [[:include "bin/**"]]]]
       (AntTar {:destfile (str @distribDir
                               @PID "-"
                               @buildVersion ".tar.gz")
                :compression "gzip"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask pack
  ""
  []
  (distroInit)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev
  "dev-mode"
  []
  ((comp preBuild clean4Build))
  (boot (resolve-jars))
  (cleanBoot)
  (compileFrwk)
  (compileWFlow)
  (compileSkaro)
  (compileJavaDemo)
  (cljXLib)
  (tardisAll)
  (cljDemo)
  (buildJSLib)
  )




(deftask babeljs
  ""
  []
  (buildJSLib))

(deftask deps
  "test only"
  []
  (fn [nextguy]
    (fn [files]
      (nextguy files)
      )))

(deftask play
  "test only"
  []
  (println (get-env)))

(deftask hi
  "test only"
  []
  (println "bonjour!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
