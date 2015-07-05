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
    [boot/base "2.1.2"]
    [boot/core "2.1.2"]
    [boot/pod "2.1.2"]
    [boot/worker "2.1.2"]
    [boot/aether "2.1.2"]

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
         '[czlabclj.tpcl.boot :as bt]
         '[boot.core :as bcore])

(import '[org.apache.tools.ant Project Target Task]
        '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(defmacro ^:private fp! "" [& args] `(cstr/join "/" '~args))
(defn- fp! "" [& args] (cstr/join "/" args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro minitask ""
  [func & forms]
  `(do
     (println (str ~func ":"))
     ~@forms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private buildVersion (atom (get-env :buildVersion)))
(def ^:private basedir (atom (get-env :basedir)))
(def ^:private PID (atom "skaro"))
(def ^:private bldDir (atom "b.out"))

(def ^:private buildDebug (atom true))
(def ^:private prj (atom "0"))

(def ^:private gantBuildDir (atom (fp! @basedir @bldDir @prj)))

(def ^:private distribDir (atom (fp! @gantBuildDir "distrib")))
(def ^:private buildDir (atom (fp! @gantBuildDir "build")))

(def ^:private libDir (atom (fp! @gantBuildDir "lib")))
(def ^:private qaDir (atom (fp! @gantBuildDir "test")))

(def ^:private testDir (atom (fp! @basedir "src" "test")))
(def ^:private srcDir (atom (fp! @basedir "src" "main")))
(def ^:private packDir (atom (fp! @gantBuildDir "pack")))

(def ^:private reportTestDir (atom (fp! @qaDir "reports")))
(def ^:private buildTestDir (atom (fp! @qaDir "classes")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

(def COMPILE_OPTS {:includeantruntime false
                   :debug @buildDebug
                   :fork true})

(def CPATH [[:location @buildDir]
            [:fileset {:dir @libDir} [[:include "*.jar"]]]])

(def JAVAC_OPTS (merge {:srcdir (fp! @srcDir "java")
                        :destdir @buildDir
                        :target "1.8"
                        :debugLevel "lines,vars,source"}
                        COMPILE_OPTS))

(def CJPATH (-> CPATH
                (conj [:location (fp! @srcDir "clojure")])))

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
(task-options!
  uber {:as-jars true}
  aot {:all true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- clean4Build ""
  [& args]
  (minitask
    "clean-build!"
    (let [pj (ant/AntProject)]
      (ant/CleanDir (io/file @basedir (get-env :target-path)))
      (ant/RunAntTasks*
        pj
        ""
        (ant/AntDelete
          pj
          {}
          [[:fileset {:dir @gantBuildDir}
                     [[:include "**/*"]
                      [:exclude "build/clojure/**"]]]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanBoot ""
  [& args]
  (minitask
    "clean-boot!"
    (ant/CleanDir (io/file @basedir
                           (get-env :target-path)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preBuild ""
  [& args]
  (let [pj (ant/AntProject)]
    (doseq [s [(fp! @distribDir "boot")
               (fp! @distribDir "exec")
               @libDir
               @qaDir
               @buildDir]]
      (.mkdirs (io/file s)))
    ;; get rid of debug logging during build!
    (ant/RunAntTasks*
      pj
      "pre-build"
      (ant/AntCopy pj {:todir @buildDir
                       :file (fp! @basedir "log4j.properties")} [])
      (ant/AntCopy pj {:todir @buildDir
                       :file (fp! @basedir "logback.xml")} []))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCmd ""

  [cmd workDir args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      cmd
      (ant/AntExec pj
                   {:executable cmd
                    :dir workDir
                    :spawn false}
                   [[:args (or args [])]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babel->cb ""

  [f & {:keys [postgen dir paths]
        :or {:postgen false
             :dir false
             :paths []}
        :as args }]

  (let [out (io/file @buildDir "js")
        dir (io/file @srcDir "js")
        mid (cstr/join "/" paths)
        des (-> (io/file out mid)
                (.getParentFile)) ]
    (cond
      postgen
      (let [bf (io/file dir @bldDir mid)]
        (spit bf
              (-> (slurp bf)
                  (.replaceAll "\\/\\*@@" "")
                  (.replaceAll "@@\\*\\/" "")))
        (ant/MoveFile bf des))

      (.isDirectory f)
      (if (= @bldDir (.getName f))
        nil
        {})

      :else
      (if-not (.endsWith mid ".js")
        (do
          (ant/CopyFile (io/file dir mid) des)
          nil)
        {:work-dir dir
         :args ["--modules"
                "amd"
                "--module-ids"
                mid
                "--out-dir"
                @bldDir] }))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildJSLib ""

  [& args]

  (let [ljs (io/file @srcDir "js" @bldDir)
        root (io/file @srcDir "js")]
    (ant/CleanDir ljs)
    (try
      (bt/BabelTree root #'babel->cb)
      (finally
        (ant/DeleteDir ljs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwk
  ""
  []
  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "compile-frwk"
      (ant/AntJavac
        pj
        JAVAC_OPTS
        [[:include "com/zotohlab/frwk/**/*.java"]
         [:classpath CPATH]
         [:compilerarg COMPILER_ARGS]])
      (ant/AntCopy
        pj
        {:todir (fp! @buildDir "com/zotohlab/frwk")}
        [[:fileset {:dir (fp! @srcDir "java/com/zotohlab/frwk")}
         [[:exclude "**/*.java"]]]])
      (ant/AntJar
        pj
        {:destFile (fp! @distribDir
                        (str "exec/frwk-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "com/zotohlab/frwk/**"]
                    [:exclude "**/log4j.properties"]
                    [:exclude "**/logback.xml"]
                    [:exclude "demo/**"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileWFlow
  ""
  []
  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "compile-wflow"
      (ant/AntJavac
        pj
        JAVAC_OPTS
        [[:include "com/zotohlab/wflow/**/*.java"]
         [:include "com/zotohlab/server/**/*.java"]
         [:classpath CPATH]
         [:compilerarg COMPILER_ARGS]])
      (ant/AntCopy
        pj
        {:todir (fp! @buildDir "com/zotohlab/wflow")}
        [[:fileset {:dir (fp! @srcDir "java/com/zotohlab/server")}
                   [[:exclude "**/*.java"]]]
         [:fileset {:dir (fp! @srcDir "java/com/zotohlab/wflow")}
                   [[:exclude "**/*.java"]]]])
      (ant/AntJar
        pj
        {:destFile (fp! @distribDir
                        (str "exec/wflow-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "com/zotohlab/server/**"]
                    [:include "com/zotohlab/wflow/**"]
                    [:exclude "**/log4j.properties"]
                    [:exclude "**/logback.xml"]
                    [:exclude "demo/**"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSkaro
  ""
  []
  (let [pj (ant/AntProject)
        t1 (ant/AntJavac
             pj
             JAVAC_OPTS
             [[:include "com/zotohlab/skaro/**/*.java"]
              [:include "com/zotohlab/mock/**/*.java"]
              [:include "com/zotohlab/tpcl/**/*.java"]
              [:classpath CPATH]
              [:compilerarg COMPILER_ARGS]])
        m (map
            (fn [d]
              (ant/AntCopy
                pj
                {:todir (fp! @buildDir "com/zotohlab" d)}
                [[:fileset {:dir (fp! @srcDir "java/com/zotohlab" d)}
                           [[:exclude "**/*.java"]]]]))
            ["skaro" "mock" "tpcl"])
        t2 (ant/AntJar
             pj
             {:destFile (fp! @distribDir
                             (str "boot/loaders-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
                        [[:include "com/zotohlab/skaro/loaders/**"] ]]])
        t3 (ant/AntJar
             pj
             {:destFile (fp! @distribDir
                             (str "exec/skaroj-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
              [[:include "com/zotohlab/skaro/**"]
              [:include "com/zotohlab/mock/**"]
              [:include "com/zotohlab/tpcl/**"]
              [:exclude "**/log4j.properties"]
              [:exclude "**/logback.xml"]
              [:exclude "demo/**"]]]]) ]

    (ant/RunAntTasks
      pj
      "compile-skaro"
      (-> (vec m)
          (concat [t2 t3])
          (conj t1)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJavaDemo
  ""
  []
  (let [pj (ant/AntProject)
        t1 (ant/AntJavac
             pj
             JAVAC_OPTS
             [[:include "demo/**/*.java"]
              [:classpath CPATH]
              [:compilerarg COMPILER_ARGS]])
        m (map
            (fn [d]
              (ant/AntCopy
                pj
                {:todir (fp! @buildDir "demo" d)}
                [[:fileset {:dir (fp! @srcDir "java/demo" d)}
                           [[:exclude "**/*.java"]]]]))
            ["splits" "flows" ]) ]
    (ant/RunAntTasks
      pj
      "compile-java-demo"
      (-> (vec m) (conj t1)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljJMX
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/xlib/jmx")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljCrypto
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/xlib/crypto")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDbio
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/xlib/dbio")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljNet
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/xlib/netty"
                                        "czlabclj/xlib/net")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljUtil
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/xlib/util"
                                        "czlabclj/xlib/i18n")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljXLib
  ""
  []

  (let [pj (ant/AntProject)
        m (map
            #(apply % [pj])
            [ #'cljUtil #'cljCrypto #'cljDbio #'cljNet #'cljJMX ])
        t2 (ant/AntCopy
             pj
             {:todir (fp! @buildDir "czlabclj/xlib")}
             [[:fileset {:dir (fp! @srcDir "clojure/czlabclj/xlib")}
                        [[:exclude "**/*.clj"]]]])
        t3 (ant/AntJar
             pj
             {:destFile (fp! @distribDir
                             (str "exec/xlib-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
                        [[:include "czlabclj/xlib/**"]
                         [:exclude "**/log4j.properties"]
                         [:exclude "**/logback.xml"]
                         [:exclude "demo/**"]]]]) ]
    (ant/RunAntTasks
      pj
      "compile-xlib"
      (concat (vec m) [t2 t3]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljTpcl
  ""
  []

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "compile-tpcl"
      (ant/AntJava
        pj
        CLJC_OPTS
        (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                            "czlabclj/tpcl")]]
                CJNESTED))
      (ant/AntCopy
        pj
        {:todir (fp! @buildDir "czlabclj/tpcl")}
        [[:fileset {:dir (fp! @srcDir "clojure/czlabclj/tpcl")}
                   [[:exclude "**/*.clj"]]]])
      (ant/AntJar
        pj
        {:destFile (fp! @distribDir
                        (str "exec/tpcl-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "czlabclj/tpcl/**"]
                    [:exclude "**/log4j.properties"]
                    [:exclude "**/logback.xml"]
                    [:exclude "demo/**"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDemo
  ""
  [& args]
  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "compile-cjdemo"
      (ant/AntJava
        pj
        CLJC_OPTS
        (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                            "demo/file" "demo/fork"
                                            "demo/http" "demo/jetty"
                                            "demo/jms" "demo/mvc"
                                            "demo/pop3" "demo/steps"
                                            "demo/tcpip" "demo/timer")]]
                CJNESTED))
      (ant/AntCopy
        pj
        {:todir (fp! @buildDir "demo")}
        [[:fileset {:dir (fp! @srcDir "clojure/demo")}
                   [[:exclude "**/*.clj"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMain
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/tardis/impl")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMvc
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/tardis/mvc")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAuth
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/tardis/auth")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisIO
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/tardis/io")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisEtc
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/tardis/etc")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisCore
  ""
  [^Project pj & args]

  (ant/AntJava
    pj
    CLJC_OPTS
    (concat [[:argvalues (bt/FmtCljNsps (fp! @srcDir "clojure")
                                        "czlabclj/tardis/core")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAll
  ""
  []

  (let [pj (ant/AntProject)
        m (map #(apply % [pj])
               [ #'tardisCore #'tardisEtc #'tardisAuth
                 #'tardisIO #'tardisMvc #'tardisMain ])
        t2 (ant/AntCopy
             pj
             {:todir (fp! @buildDir "czlabclj/tardis")}
             [[:fileset {:dir (fp! @srcDir "clojure/czlabclj/tardis")}
                        [[:exclude "**/*.meta"]
                         [:exclude "**/*.clj"]]]])
        t3 (ant/AntJar
             pj
             {:destFile (fp! @distribDir
                             (str "exec/tardis-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
                        [[:include "czlabclj/tardis/**"] ]]]) ]
    (ant/RunAntTasks
      pj
      "compile-tardis"
      (concat (vec m) [t2 t3]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- distroInit ""
  []
  (let [root (io/file @packDir)
        pj (ant/AntProject)]
    (ant/CleanDir root)
    (doseq [d ["conf" ["dist" "boot"] ["dist" "exec"] "bin"
               ["etc" "ems"] "lib" "logs"
               "docs" "pods" "tmp" "apps"]]
      (.mkdirs (if (vector? d)
                 (apply io/file root d)
                 (io/file root d))))
    (spit (io/file root "VERSION") @buildVersion)
    (ant/RunAntTasks*
      pj
      "distro-init"
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "etc")}
        [[:fileset {:dir (fp! @basedir "etc")} []]])
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "conf")}
        [[:fileset {:dir (fp! @basedir "etc/conf")} []]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyJsFiles ""

  ^Task
  [^Project pj & args]

  (ant/AntCopy
    pj
    {:todir (fp! @packDir "public/vendors")}
    [[:fileset {:dir (fp! @buildDir "js")}
               [[:include "**/*.js"]]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""

  [& args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-res"
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "etc/ems")
         :flatten true}
        [[:fileset {:dir (fp! @srcDir "clojure")}
                   [[:include "**/*.meta"]]]])
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "etc")}
        [[:fileset {:dir (fp! @basedir "etc")} []]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDocs ""

  [& args]

  (ant/CleanDir (io/file @packDir "docs" "jsdoc"))
  (ant/CleanDir (io/file @packDir "docs" "api"))
  (let [pj (ant/AntProject)]

    (ant/RunAntTasks*
      pj
      "pack-docs"
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "docs")}
        [[:fileset {:dir (fp! @basedir "docs")}
                   [[:exclude "dummy.txt"]]]])
      (ant/AntJavadoc
        pj
        {:destdir (fp! @packDir "docs/api")
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
         :version true}
         [[:fileset {:dir (fp! @srcDir "java")}
                    [[:exclude "demo/**"]
                     [:include "**/*.java"]]]
          [:classpath CPATH]])

      (ant/AntJava
        pj
        CLJC_OPTS
        (concat [[:argvalues ["czlabclj.tpcl.codox"]]]
                CJNESTED))

      (ant/AntJava
        pj
        {:classname "czlabclj.tpcl.codox"
         :fork true
         :failonerror true}
        [[:argvalues [@basedir
                      (fp! @srcDir "clojure")
                      (fp! @packDir "docs/api")]]
         [:classpath CJPATH]])

      (copyJsFiles pj)

      (ant/AntExec
        pj
        {:executable "jsdoc"
         :dir @basedir
         :spawn true}
        [[:argvalues ["-c" "mvn/js/jsdoc-conf.json"
                      "-d" (fp! @packDir "docs/jsdoc") ]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packSrc ""

  [& args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-src"
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "src/main/clojure")}
        [[:fileset {:dir (fp! @srcDir "clojure")} []]])
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "src/main/java")}
        [[:fileset {:dir (fp! @srcDir "java")} []]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLics ""

  [& args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-lics"
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "lics")}
        [[:fileset {:dir (fp! @basedir "lics") } []]])

      (ant/AntCopy
        pj
        {:todir @packDir :flatten true}
        [[:fileset {:dir @basedir}
                   [[:include "*.html"]
                    [:include "*.txt"]
                    [:include "*.md"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDist ""

  [& args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-dist"

      (ant/AntCopy
        pj
        {:todir (fp! @packDir "dist/exec")}
        [[:fileset {:dir (fp! @distribDir "exec")}
                   [[:include "*.jar"]]]])

      (ant/AntCopy
        pj
        {:todir (fp! @packDir "dist/boot")}
        [[:fileset {:dir (fp! @distribDir "boot")}
                   [[:include "*.jar"]]]])

      (ant/AntJar
        pj
        {:destFile (fp! @packDir
                        (str "dist/exec/clj-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "clojure/**"]]]])

      (copyJsFiles pj))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLibs ""

  [& args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-libs"
      (ant/AntCopy
        pj
        {:todir (fp! @packDir "lib")}
        [[:fileset {:dir @libDir} []]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""

  [& args]

  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-bin"

      (ant/AntCopy
        pj
        {:todir (fp! @packDir "bin")}
        [[:fileset {:dir (fp! @basedir "bin")} []]])

      (ant/AntChmod
        pj
        {:dir (fp! @packDir "bin")
         :perm "755"
         :includes "*"} []))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packAll ""

  [& args]

  (ant/CleanDir (io/file @packDir "tmp"))
  (let [pj (ant/AntProject)]
    (ant/RunAntTasks*
      pj
      "pack-all"

      (ant/AntTar
        pj
        {:destFile (fp! @distribDir
                        (str @PID "-" @buildVersion ".tar.gz"))
         :compression "gzip"}
        [[:tarfileset {:dir @packDir}
                      [[:exclude "apps/**"]
                       [:exclude "bin/**"]]]
         [:tarfileset {:dir @packDir :mode "755"}
                      [[:include "bin/**"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juber
  "my own uber"
  []
  (bcore/with-pre-wrap fileset
    (let [from (io/file @basedir (get-env :target-path))
          jars (output-files fileset)
          to (io/file @libDir)]
      (doseq [j (seq jars)]
        (let [pj (ant/AntProject)]
          (ant/RunAntTasks*
            pj
            ""
            (ant/AntCopy pj
                         {:file (fp! (:dir j) (:path j))
                          :todir to}
                         []))))
      (format "copied (%d) jar-files to %s" (count jars) to))
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
  (cljTpcl)
  (buildJSLib))

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
  (packAll))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask babeljs
  ""
  []
  (buildJSLib))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask poo
  ""
  []
  (clean4Build))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask deps
  "test only"
  []
  (fn [nextguy]
    (fn [files]
      (nextguy files)
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask play
  "test only"
  []
  (println (get-env)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask hi
  "test only"
  []
  (println "bonjour!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
