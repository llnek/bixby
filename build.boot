(set-env!
  :dependencies '[

    [bouncycastle/bcprov-jdk15on "152" ]
    [bouncycastle/bcmail-jdk15on "152" ]
    [bouncycastle/bcpkix-jdk15on "152" ]
    [org.jasypt/jasypt "1.9.2" ]
    [org.mindrot/jbcrypt "0.3m" ]

    [org.slf4j/slf4j-api "1.7.10" ]
    [log4j/log4j "1.2.17" ]

    [ch.qos.logback/logback-classic "1.1.3" ]
    [ch.qos.logback/logback-core "1.1.3" ]

    [net.sourceforge.jregex/jregex "1.2_01" ]
    [net.sf.jopt-simple/jopt-simple "4.6" ]
    [com.google.guava/guava "18.0" ]
    [com.google.code.findbugs/jsr305 "2.0.3" ]
    [joda-time/joda-time "2.7" ]
    [org.zeroturnaround/zt-exec "1.6" ]
    [org.zeroturnaround/zt-zip "1.7" ]
    [org.apache.axis/axis "1.4" ]
    [org.apache.axis/axis-jaxrpc "1.4" ]
    [org.jetlang/jetlang "0.2.12" ]

    [org.jdom/jdom2 "2.0.6" ]

    [com.fasterxml.jackson.core/jackson-core "2.4.4" ]
    [com.fasterxml.jackson.core/jackson-databind "2.4.4" ]
    [com.fasterxml.jackson.core/jackson-annotations "2.4.4" ]

    [com.google.code.gson/gson "2.3.1" ]

    [org.apache.commons/commons-compress "1.9" ]
    [org.apache.commons/commons-lang3 "3.4" ]
    [org.apache.commons/commons-exec "1.3" ]
    [commons-net/commons-net "3.3" ]
    [commons-io/commons-io "2.4" ]

    [commons-logging/commons-logging "1.2" ]
    [org.apache.commons/commons-email "1.4" ]
    [commons-codec/commons-codec "1.10" ]
    [commons-fileupload/commons-fileupload "1.3.1" ]
    [commons-dbutils/commons-dbutils "1.6" ]
    [com.sun.mail/javax.mail "1.5.3" ]

    [org.apache.ivy/ivy "2.4.0" ]
    [org.apache.ant/ant "1.9.5" ]
    [org.apache.ant/ant-launcher "1.9.5" ]
    [org.apache.ant/ant-junit4 "1.9.5" ]
    [org.apache.ant/ant-junit "1.9.5" ]
    [org.apache.ant/ant-apache-log4j "1.9.5" :exclusions [log4j]]

    [ant-contrib/ant-contrib "1.0b3" :exclusions [ant]]
    [org.codehaus.gant/gant_groovy2.4 "1.9.12" ]

    [com.jolbox/bonecp "0.8.0.RELEASE" ]

    [org.apache.httpcomponents/httpcore-nio "4.4" ]
    [org.apache.httpcomponents/httpcore "4.4" ]
    [org.apache.httpcomponents/httpclient "4.4" ]
    [io.netty/netty-all "4.0.29.Final" ]

    [com.corundumstudio.socketio/netty-socketio "1.7.7" :exclusions [io.netty]]

    [org.eclipse.jetty/jetty-xml "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-server "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-continuation "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-servlet "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-server "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-util "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-security "9.2.11.v20150529"  ]
    [org.eclipse.jetty/jetty-webapp "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-api "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-common "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-servlet "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-client "9.2.11.v20150529"  ]
    [org.eclipse.jetty.websocket/websocket-server "9.2.11.v20150529"  ]

    [org.codehaus.groovy/groovy-all "2.4.3" ]

    [com.sun.tools/tools "1.8.0"  ]
    [org.javassist/javassist "3.19.0-GA"  ]

    [com.github.spullara.mustache.java/compiler "0.9.0" ]

    [org.freemarker/freemarker "2.3.22" ]

    [com.yahoo.platform.yui/yuicompressor "2.4.8"  :exclusions [rhino]]

    [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1" ]
    [com.h2database/h2 "1.4.187" ]
    [org.postgresql/postgresql "9.4-1201-jdbc41" ]

    [org.clojure/math.numeric-tower "0.0.4" ]
    [org.clojure/math.combinatorics "0.0.8" ]
    [org.clojure/tools.logging "0.3.1" ]
    [org.clojure/tools.nrepl "0.2.8" ]
    [org.clojure/tools.reader "0.8.16" ]
    [org.clojure/data.codec "0.1.0" ]
    [org.clojure/data.csv "0.1.2" ]
    [org.clojure/java.jdbc "0.3.6" ]
    [org.clojure/java.data "0.1.1" ]
    [org.clojure/java.jmx "0.3.0" ]
    [org.clojure/data.json "0.2.6" ]
    [org.clojure/data.xml "0.0.8" ]
    [org.clojure/core.cache "0.6.3" ]
    [org.clojure/core.match "0.2.2" ]
    [org.clojure/tools.cli "0.3.1" ]
    [org.clojure/data.generators "0.1.2" ]
    [org.clojure/core.async "0.1.346.0-17112a-alpha" ]
    [org.clojure/core.logic "0.8.10" ]
    [org.clojure/algo.monads "0.1.5" ]
    [org.clojure/algo.generic "0.1.2" ]
    [org.clojure/core.memoize "0.5.7" ]
    [codox/codox.core "0.8.12" ]

    [org.clojure/clojure "1.6.0" ]
    [org.clojure/clojurescript "0.0-3058" ]

    [org.apache.shiro/shiro-core "1.2.3" ]
    [org.mozilla/rhino "1.7.6" ]
    [jline/jline "1.0" ]

    [net.mikera/cljunit "0.3.1" ]
    [junit/junit "4.12"  ]
    [com.googlecode.jslint4java/jslint4java "2.0.5" ]

  ]

  :source-paths #{"src/main/java" "src/main/clojure"}
  :buildVersion "0.9.0-SNAPSHOT"
  :buildDebug true
  :basedir (System/getProperty "user.dir"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(require '[clojure.tools.logging :as log]
         '[clojure.java.io :as io]
         '[clojure.string :as cstr]
         '[boot.core :as bcore])

(import '[org.apache.commons.exec CommandLine DefaultExecutor]
        '[org.apache.commons.io FileUtils]
        '[java.util Map HashMap Stack]
        '[java.io File]
        '[org.apache.tools.ant.taskdefs Java Copy Jar Zip ExecTask Javac]
        '[org.apache.tools.ant.listener TimestampedLogger]
        '[org.apache.tools.ant.types Reference
          Commandline$Argument
          PatternSet$NameEntry
          Environment$Variable FileSet Path DirSet]
        '[org.apache.tools.ant Project Target Task]
        '[org.apache.tools.ant.taskdefs Javac$ImplementationSpecificArgument])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private buildVersion (atom (get-env :buildVersion)))
(def ^:private basedir (atom (get-env :basedir)))
(def ^:private bldDir (atom "b.out"))

(def ^:private buildDebug (atom true))
(def ^:private prj (atom "0"))

(def ^:private cljBuildDir  (atom (str "./" @bldDir "/clojure.org")))
(def ^:private gantBuildDir (atom (str "./" @bldDir "/" @prj)))

(def ^:private distribDir (atom (str @gantBuildDir "/distrib")))
(def ^:private buildDir (atom (str @gantBuildDir "/build")))

(def ^:private libDir (atom (str @gantBuildDir "/lib")))
(def ^:private qaDir (atom (str @gantBuildDir "/test")))

(def ^:private testDir (atom (str @basedir "/src/test")))
(def ^:private srcDir (atom (str @basedir "/src/main")))
(def ^:private packDir (atom (str @gantBuildDir "/pack")))

(def ^:private reportTestDir (atom (str @qaDir "/reports")))
(def ^:private buildTestDir (atom (str @qaDir "/classes")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- boolify "" [expr dft] (if expr true false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileSet ""

  [args]

  (let [fs (doto (FileSet.)
                 (.setDir (io/file (first args)))) ]
    (doseq [n (last args)]
      (case (first n)
        :include (-> (.createInclude fs)
                     (.setName (last n)))
        :exclude (-> (.createExclude fs)
                     (.setName (last n)))
        nil))
    fs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ExecProj ""

  [^Project pj]

  (.executeTarget pj "mi6"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProjAntTask ""

  ^Project
  [^Task taskObj]

  (let [lg (doto (TimestampedLogger.)
             (.setOutputPrintStream System/out)
             (.setErrorPrintStream System/err)
             (.setMessageOutputLevel Project/MSG_INFO))
        pj (doto (Project.)
             (.setName "boot-clj")
             (.init))
        tg (doto (Target.)
             (.setName "mi6")) ]
    (doto pj
      (.addTarget tg)
      (.addBuildListener lg))
    (doto taskObj
      (.setProject pj)
      (.setOwningTarget tg))
    (.addTask tg taskObj)
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def COMPILE_OPTS {:debug @buildDebug
                   :fork true
                   :cp [[:location @buildDir]
                        [:fileset @libDir [[:include "*.jar"]]] ] })

(def JAVAC_OPTS (merge {:srcdir (str @srcDir "/java")
                        :destdir @buildDir
                        :target "1.8"
                        :debuglevel "lines,vars,source"
                        :compilerarg {:line "-Xlint:deprecation -Xlint:unchecked"}}
                        COMPILE_OPTS))

(def CLJC_OPTS (update-in COMPILE_OPTS [:cp]
                          (fn [cps]
                            (-> cps
                                (conj [:location @cljBuildDir])
                                (conj [:location (str @srcDir "/clojure")])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- setClassPath ""

  [^Path root paths]

  (doseq [p paths]
    (case (first p)
      :location
      (doto (.createPath root)
        (.setLocation (io/file (last p))))
      :refid
      (doto (.createPath root)
        (.setRefid (last p)))
      :fileset
      (let [fs (doto (FileSet.) (.setDir (io/file (nth p 1))))]
        (doseq [n (last p)]
          (case (first n)
            :include (-> (.createInclude fs)
                         (.setName (last n)))
            :exclude (-> (.createExclude fs)
                         (.setName (last n)))
            nil))
        (.addFileset root fs))
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntJavac ""

  [options]

  (let [ct (Javac.)
        pj (ProjAntTask ct) ]
    (doto ct
      (.setDebugLevel (:debuglevel options))
      (.setFork (true? (:fork options)))
      (.setDebug (:debug options))
      (.setTarget (:target options))
      (.setIncludeantruntime false)
      (.setTaskName "javac")
      (.setSrcdir (Path. pj (:srcdir options)))
      (.setDestdir (io/file (:destdir options))))
    (-> (.createCompilerArg ct)
        (.setLine (:line (:compilerarg options))))
    (-> (.createClasspath ct)
        (setClassPath (:cp options)))
    (doseq [p (:files options)]
      (cond
        (= :include (first p))
        (-> (.createInclude ct)
            (.setName (last p)))
        (= :exclude (first p))
        (-> (.createExclude ct)
            (.setName (last p)))
        :else nil))
    pj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntCopy ""

  [toDir res]

  (let [tk (Copy.)
        pj (ProjAntTask tk) ]
    (doto tk
      (.setTaskName "copy-task")
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

  []

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
