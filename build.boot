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

    [org.clojure/clojure "1.7.0" ]
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
         '[czlabclj.tpcl.antlib :as ant]
         '[boot.core :as bcore])

(import [org.apache.commons.exec CommandLine DefaultExecutor]
        [java.util Map HashMap Stack]
        [java.io File]
        [org.apache.tools.ant Project Target Task])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private buildVersion (atom (get-env :buildVersion)))
(def ^:private basedir (atom (get-env :basedir)))
(def ^:private PID (atom "skaro"))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

(def COMPILE_OPTS {:includeantruntime false
                   :debug @buildDebug
                   :fork true})

(def CPATH [[:location @buildDir]
            [:fileset {:dir @libDir} [[:include "*.jar"]]]])

(def JAVAC_OPTS (merge {:srcdir (str @srcDir "/java")
                        :destdir @buildDir
                        :target "1.8"
                        :debugLevel "lines,vars,source"}
                        COMPILE_OPTS))

(def CJPATH (-> CPATH
                (conj [:location @cljBuildDir])
                (conj [:location (str @srcDir "/clojure")])))

(def CLJC_OPTS {:classname "clojure.lang.Compile"
                :fork true
                :failonerror true
                :maxmemory "2048m"})

(def CLJC_SYSPROPS {:clojure.compile.warn-on-reflection true
                    :clojure.compile.path @buildDir})

(def CJNESTED [[:sysprops CLJC_SYSPROPS]
               [:classpath CJPATH]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro minitask ""
  [func & forms]
  `(do
     (println (str ~func ":"))
     ~@forms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanDir "" [^File dir]
  (let [pj (ant/AntProject)]
    (if (.exists dir)
      (-> (ant/ProjAntTasks pj
                            ""
                            (->> [[:fileset {:dir (.getCanonicalPath dir)}
                                  [[:include "**/*"]]]]
                                 (ant/AntDelete pj {:includeEmptyDirs true})))
          (ant/ExecTarget))
      (.mkdirs dir))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- deleteDir "" [^File dir]
  (when (.exists dir)
    (let [pj (ant/AntProject)]
      (-> (ant/ProjAntTasks pj
                            ""
                            (->> [[:fileset {:dir (.getCanonicalPath dir)} []]]
                                 (ant/AntDelete pj {:includeEmptyDirs true})))
          (ant/ExecTarget)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- clean4Build ""
  [& args]
  (minitask
    "clean-build"
    (cleanDir (io/file @basedir (get-env :target-path)))
    (cleanDir (io/file @basedir @gantBuildDir))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanBoot ""
  [& args]
  (minitask
    "clean-boot"
    (cleanDir (io/file @basedir
                       (get-env :target-path)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preBuild ""
  [& args]
  (let [pj (ant/AntProject)]
    (doseq [s [(str @distribDir "/boot")
               (str @distribDir "/exec")
               (str @libDir)
               @qaDir
               @buildDir]]
      (.mkdirs (io/file s)))
    ;; get rid of debug logging during build!
    (-> (ant/ProjAntTasks pj
                          "pre-build"
                          (ant/AntCopy pj {:todir @buildDir
                                           :file (str @basedir "/log4j.properties")} [])
                          (ant/AntCopy pj {:todir @buildDir
                                           :file (str @basedir "/logback.xml")} []))
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCmd ""

  [cmd workDir args]

  (let [pj (ant/AntProject)]
    (-> (ant/ProjAntTasks pj
                          (str "" cmd)
                          (->> [[:args (or args [])]]
                               (ant/AntExec pj {:executable cmd
                                                :dir (.getCanonicalPath workDir)
                                                :spawn false})))
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babelFile ""
  [mid]
  (let [out (io/file @buildDir "js")
        dir (io/file @srcDir "js")
        pj (ant/AntProject)
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
        (-> (ant/ProjAntTasks pj
                              ""
                              (ant/AntCopy pj {:todir (.getCanonicalPath (.getParentFile des))
                                               :file (.getCanonicalPath (io/file dir mid))} []))
            (ant/ExecTarget))))

    (-> (ant/ProjAntTasks pj
                          ""
                          (ant/AntMove pj {:todir (.getCanonicalPath (doto (-> (io/file out mid)
                                                                               (.getParentFile))
                                                                           (.mkdirs)))
                                           :file (.getCanonicalPath fp)} []))
        (ant/ExecTarget))
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
(task-options!
  uber {:as-jars true}
  aot {:all true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juber
  "my own uber"
  []
  (bcore/with-pre-wrap fileset
    (let [from (io/file @basedir (get-env :target-path))
          jars (output-files fileset)
          to (io/file @basedir @libDir)]
      (doseq [j (seq jars)]
        (let [pj (ant/AntProject)]
          (-> (ant/ProjAntTasks pj
                                ""
                                (ant/AntCopy pj
                                             {:file (str (:dir j) "/" (:path j))
                                              :todir (.getCanonicalPath to)} []))
              (ant/ExecTarget))))
      (println "copied (" (count jars) ") jar-files to " to))
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
  (let [pj (ant/AntProject)
        t1 (->> [[:files [[:include "com/zotohlab/frwk/**/*.java"]]]
                 [:classpath CPATH]
                 [:compilerarg COMPILER_ARGS]]
                (ant/AntJavac pj JAVAC_OPTS))
        t2 (->> [[:fileset {:dir (str @srcDir "/java/com/zotohlab/frwk")}
                           [[:exclude "**/*.java"]]]]
                (ant/AntCopy pj {:todir (str @buildDir "/com/zotohlab/frwk")}))
        t3 (->> [[:fileset {:dir @buildDir}
                           [[:include "com/zotohlab/frwk/**"]
                            [:exclude "**/log4j.properties"]
                            [:exclude "**/logback.xml"]
                            [:exclude "demo/**"]]]]
                (ant/AntJar pj {:destFile (str @distribDir
                                               "/exec/frwk-"
                                               @buildVersion ".jar")})) ]
    (-> (ant/ProjAntTasks pj "compile-frwk" t1 t2 t3)
        (ant/ExecTarget))
  ))

(defn- ExecProj [& args])
(defn- AntJavac [& args])
(defn- AntJar [& args])
(defn- AntCopy [& args])
(defn- AntJava [& args])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileWFlow
  ""
  []
  (let [pj (ant/AntProject)
        t1 (->> [[:files [[:include "com/zotohlab/wflow/**/*.java"]
                          [:include "com/zotohlab/server/**/*.java"]]]
                 [:classpath CPATH]
                 [:compilerarg COMPILER_ARGS]]
                (ant/AntJavac pj JAVAC_OPTS))
        t2 (->> [[:fileset {:dir (str @srcDir "/java/com/zotohlab/server")}
                           [[:exclude "**/*.java"]]]
                 [:fileset {:dir (str @srcDir "/java/com/zotohlab/wflow")}
                           [[:exclude "**/*.java"]]]]
                (ant/AntCopy pj {:todir (str @buildDir "/com/zotohlab/wflow")}))
        t3 (->> [[:fileset {:dir @buildDir}
                           [[:include "com/zotohlab/server/**"]
                            [:include "com/zotohlab/wflow/**"]
                            [:exclude "**/log4j.properties"]
                            [:exclude "**/logback.xml"]
                            [:exclude "demo/**"]]]]
                (ant/AntJar pj {:destFile (str @distribDir
                                               "/exec/wflow-" @buildVersion ".jar")})) ]
    (-> (ant/ProjAntTasks pj "compile-wflow" t1 t2 t3)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSkaro
  ""
  []
  (let [pj (ant/AntProject)
        t1 (->> [[:files [[:include "com/zotohlab/skaro/**/*.java"]
                          [:include "com/zotohlab/mock/**/*.java"]
                          [:include "com/zotohlab/tpcl/**/*.java"]]]
                 [:classpath CPATH]
                 [:compilerarg COMPILER_ARGS]]
                (ant/AntJavac pj JAVAC_OPTS))

        m (map
            (fn [d]
              (->> [[:fileset {:dir (str @srcDir "/java/com/zotohlab/" d)}
                              [[:exclude "**/*.java"]]]]
                   (ant/AntCopy pj {:todir (str @buildDir "/com/zotohlab/" d) })))
            ["skaro" "mock" "tpcl"])

        t2 (->> [[:fileset {:dir @buildDir}
                           [[:include "com/zotohlab/skaro/loaders/**"] ]]]
                (ant/AntJar pj {:destFile (str @distribDir
                                               "/boot/loaders-" @buildVersion ".jar")}))
        t3 (->> [[:fileset {:dir @buildDir}
                           [[:include "com/zotohlab/skaro/**"]
                            [:include "com/zotohlab/mock/**"]
                            [:include "com/zotohlab/tpcl/**"]
                            [:exclude "**/log4j.properties"]
                            [:exclude "**/logback.xml"]
                            [:exclude "demo/**"]]]]
                (ant/AntJar pj {:destFile (str @distribDir
                                               "/exec/skaroj-" @buildVersion ".jar")})) ]
    (-> (apply ant/ProjAntTasks
               pj
               "compile-skaro"
               (-> (vec m)
                   (concat [t2 t3])
                   (conj t1)))
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJavaDemo
  ""
  []
  (let [pj (ant/AntProject)
        t1 (->> [[:files [[:include "demo/**/*.java"]]]
                 [:classpath CPATH]
                 [:compilerarg COMPILER_ARGS]]
                (ant/AntJavac pj JAVAC_OPTS))
        m (map
            (fn [d]
              (->> [[:fileset {:dir (str @srcDir "/java/demo/" d)}
                              [[:exclude "**/*.java"]]]]
                   (ant/AntCopy pj {:todir (str @buildDir "/demo/" d)})))
            ["splits" "flows" ]) ]
    (-> (apply ant/ProjAntTasks
               pj
               "compile-java-demo"
               (-> (vec m) (conj t1)))
        (ant/ExecTarget))
  ))

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
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/xlib/jmx")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljCrypto
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/xlib/crypto")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDbio
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/xlib/dbio")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljNet
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/xlib/netty"
                                   "czlabclj/xlib/net")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljUtil
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/xlib/util"
                                   "czlabclj/xlib/i18n")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljXLib
  ""
  []

  (let [pj (ant/AntProject)
        m (map
            #(apply % [pj])
            [ #'cljUtil #'cljCrypto #'cljDbio #'cljNet #'cljJMX ])
        t2 (->> [[:fileset {:dir (str @srcDir "/clojure/czlabclj/xlib")}
                           [[:exclude "**/*.clj"]]]]
                (ant/AntCopy pj {:todir (str @buildDir "/czlabclj/xlib")}))
        t3 (->> [[:fileset {:dir @buildDir}
                           [[:include "czlabclj/xlib/**"]
                            [:exclude "**/log4j.properties"]
                            [:exclude "**/logback.xml"]
                            [:exclude "demo/**"]]]]
                (ant/AntJar pj {:destFile (str @distribDir
                                               "/exec/xlib-" @buildVersion ".jar")}))]
    (-> (apply ant/ProjAntTasks
               pj
               "compile-xlib"
               (concat (vec m) [t2 t3]))
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDemo
  ""
  [& args]
  (let [pj (ant/AntProject)
        t1 (->> (concat [[:args (fmtCljNsps "demo/file" "demo/fork" "demo/http"
                                            "demo/jetty" "demo/jms" "demo/mvc"
                                            "demo/pop3" "demo/steps"
                                            "demo/tcpip" "demo/timer")]]
                        CJNESTED)
                (ant/AntJava pj CLJC_OPTS))
        t2 (->> [[:fileset {:dir (str @srcDir "/clojure/demo")}
                           [[:exclude "**/*.clj"]]]]
                (ant/AntCopy pj {:todir (str @buildDir "/demo")})) ]

    (-> (ant/ProjAntTasks pj "compile-cjdemo" t1 t2)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMain
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/tardis/impl")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMvc
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/tardis/mvc")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAuth
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/tardis/auth")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisIO
  ""
  [^Project pj & args]
  (->> (concat [[:args (fmtCljNsps "czlabclj/tardis/io")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisEtc
  ""
  [^Project pj & args]

  (->> (concat [[:args (fmtCljNsps "czlabclj/tardis/etc")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisCore
  ""
  [^Project pj & args]
  (->> (concat [[:args (fmtCljNsps "czlabclj/tardis/core")]]
               CJNESTED)
       (ant/AntJava pj CLJC_OPTS)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAll
  ""
  []

  (let [pj (ant/AntProject)
        m (map #(apply % [pj])
               [ #'tardisCore #'tardisEtc #'tardisAuth
                 #'tardisIO #'tardisMvc #'tardisMain ])
        t2 (->> [[:fileset {:dir (str @srcDir "/clojure/czlabclj/tardis")}
                            [[:exclude "**/*.meta"]
                             [:exclude "**/*.clj"]]]]
                (ant/AntCopy pj {:todir (str @buildDir "/czlabclj/tardis")}))
        t3 (->> [[:fileset {:dir @buildDir}
                           [[:include "czlabclj/tardis/**"] ]]]
                (ant/AntJar pj {:destFile (str @distribDir
                                               "/exec/tardis-" @buildVersion ".jar")})) ]

    (-> (apply ant/ProjAntTasks pj
                                "compile-tardis"
                                (concat (vec m) [t2 t3]))
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- distroInit ""
  []
  (let [root (io/file @basedir @packDir)
        pj (ant/AntProject)]
    (cleanDir root)
    (doseq [d ["conf" ["dist" "boot"] ["dist" "exec"] "bin"
               ["etc" "ems"] "lib" "logs" "docs" "pods" "tmp" "apps"]]
      (.mkdirs (if (vector? d) (apply io/file root d) (io/file root d))))
    (spit (io/file root "VERSION") @buildVersion)
    (let [t1 (->> [[:fileset {:dir (str @basedir "/etc")} []]]
                  (ant/AntCopy pj {:todir (str @packDir "/etc")}))
          t2 (->> [[:fileset {:dir (str @basedir "/etc/conf")} []]]
                  (ant/AntCopy pj {:todir (str @packDir "/conf")})) ]
      (-> (ant/ProjAntTasks pj "distro-init" t1 t2)
          (ant/ExecTarget)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyJsFiles ""

  ^Task
  [^Project pj & args]

  (->> [[:fileset {:dir (str @buildDir "/js")}
                  [[:include "**/*.js"]]]]
       (ant/AntCopy pj {:todir (str @packDir "/public/vendors")})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""

  [& args]

  (let [pj (ant/AntProject)
        t1  (->> [[:fileset {:dir (str @srcDir "/clojure")}
                            [[:include "**/*.meta"]]]]
                 (ant/AntCopy pj {:todir (str @packDir "/etc/ems")
                                :flatten true}))
        t2  (->> [[:fileset {:dir (str @basedir "/etc")} []]]
                 (ant/AntCopy pj {:todir (str @packDir "/etc")}))]

    (-> (ant/ProjAntTasks pj "pack-res" t1 t2)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDocs ""

  [& args]

  (cleanDir (io/file @packDir "docs" "jsdoc"))
  (cleanDir (io/file @packDir "docs" "api"))
  (let [pj (ant/AntProject)

        t1  (->> [[:fileset {:dir (str @basedir "/docs")
                             :errorOnMissingDir false}
                            [[:exclude "dummy.txt"]]]]
               (ant/AntCopy pj {:todir (str @packDir "/docs")}))

        t2 (copyJsFiles pj)

        t3  (->> [[:args ["-c" "mvn/js/jsdoc-conf.json"
                          "-d" (str @packDir "/docs/jsdoc") ]]]
                 (ant/AntExec pj {:executable "jsdoc"
                                  :dir @basedir
                                  :spawn true}))

        t4  (->> [[:fileset {:dir (str @srcDir "/java")}
                            [[:exclude "demo/**"]
                             [:include "**/*.java"]]]
                  [:classpath CPATH]]
                 (ant/AntJavadoc pj
                                 {:destdir (str @packDir "/docs/api")
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
                                  :version true}))

        t5  (->> [[:arg ["czlabclj.tpcl.codox"]]
                  [:sysprops CLJC_SYSPROPS]
                  [:classpath CJPATH]]
                 (ant/AntJava pj {:classname "clojure.lang.Compile"
                                  :fork true
                                  :failonerror true
                                  :maxmemory "2048m"}))

        t6  (->> [[:args [@basedir
                          (str @srcDir "/clojure")
                          (str @packDir "/docs/api")]]
                  [:classpath CJPATH]]
                 (ant/AntJava pj {:classname "czlabclj.xlib.util.codox"
                                  :fork true
                                  :failonerror true})) ]
    (-> (ant/ProjAntTasks pj
                          "pack-docs"
                          t1 t2 t3 t4 t5 t6)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packSrc ""

  [& args]

  (let [pj (ant/AntProject)
        t1  (->> [[:fileset {:dir (str @srcDir "/clojure")} []]]
                 (ant/AntCopy pj {:todir (str @packDir "/src/main/clojure")}))
        t2  (->> [[:fileset {:dir (str @srcDir "/java")} []]]
                 (ant/AntCopy pj {:todir (str @packDir "/src/main/java")}))]
    (-> (ant/ProjAntTasks pj "pack-src" t1 t2)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLics ""

  [& args]

  (let [pj (ant/AntProject)
        t1  (->> [[:fileset {:dir (str @basedir "/lics")
                             :errorOnMissingDir false} []]]
                 (ant/AntCopy pj {:todir (str @packDir "/lics")}))

        t2  (->> [[:fileset {:dir @basedir}
                            [[:include "*.html"]
                             [:include "*.txt"]
                             [:include "*.md"]]]]
                 (ant/AntCopy pj {:todir @packDir
                                  :flatten true})) ]
    (-> (ant/ProjAntTasks pj "pack-lics" t1 t2)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDist ""

  [& args]

  (let [pj (ant/AntProject)
        t1  (->> [[:fileset {:dir (str @distribDir "/exec")}
                            [[:include "*.jar"]]]]
                 (ant/AntCopy pj {:todir (str @packDir "/dist/exec")}))

        t2  (->> [[:fileset {:dir (str @distribDir "/boot") }
                            [[:include "*.jar"]]]]
                 (ant/AntCopy pj {:todir (str @packDir "/dist/boot")}))

        t3  (->> [[:fileset {:dir @cljBuildDir}
                            [[:include "clojure/**"]]]
                  [:fileset {:dir @buildDir}
                            [[:include "clojure/**"]]]]
                 (ant/AntJar pj {:destFile (str @packDir
                                                "/dist/exec/clj-"
                                                @buildVersion
                                                ".jar")}))
        t4  (copyJsFiles pj) ]
    (-> (ant/ProjAntTasks pj "pack-dist" t1 t2 t3)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLibs ""

  [& args]

  (let [pj (ant/AntProject)
        t1  (->> [[:fileset {:dir (str @libDir "/libjar")} []]]
                 (ant/AntCopy pj {:todir (str @packDir "/lib")})) ]
    (-> (ant/ProjAntTasks pj "pack-libs" t1)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""

  [& args]

  (let [pj (ant/AntProject)
        t1  (->> [[:fileset {:dir (str @basedir "/bin")
                             :errorOnMissingDir false} []]]
                 (ant/AntCopy pj {:todir (str @packDir "/bin")}))

        t2  (ant/AntChmod pj {:dir (str @packDir "/bin")
                              :perm "755"
                              :includes "*"} []) ]
    (-> (ant/ProjAntTasks pj "pack-bin" t1 t2)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packAll ""

  [& args]

  (cleanDir (io/file @packDir "/tmp"))
  (let [pj (ant/AntProject)
        t1  (->> [[:tarfileset {:dir @packDir}
                               [[:exclude "apps/**"]
                                [:exclude "bin/**"]]]
                  [:tarfileset {:dir @packDir
                                :filemode "755"}
                               [[:include "bin/**"]]]]
                 (ant/AntTar pj {:destFile (str @distribDir
                                                "/"
                                                @PID
                                                "-"
                                                @buildVersion
                                                ".tar.gz")
                                 :compression "gzip"})) ]
    (-> (ant/ProjAntTasks pj "pack-all" t1)
        (ant/ExecTarget))
  ))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask pack
  "bundle-project"
  []
  (distroInit)
  (packRes)
  (packDocs)
  (packSrc)
  (packLics)
  (packBin)
  (packDist)
  (packLibs)
  (packAll)
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
