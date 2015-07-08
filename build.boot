(set-env!
  :dependencies '[

    [bouncycastle/bcprov-jdk15on "152" ]
    [bouncycastle/bcmail-jdk15on "152" ]
    [bouncycastle/bcpkix-jdk15on "152" ]
    [org.jasypt/jasypt "1.9.2" ]
    ;;[org.mindrot/jbcrypt "0.3m" ]

    [org.slf4j/slf4j-api "1.7.12" ]
    [log4j/log4j "1.2.17" ]

    [ch.qos.logback/logback-classic "1.1.3" ]
    [ch.qos.logback/logback-core "1.1.3" ]

    [net.sourceforge.jregex/jregex "1.2_01" ]
    [net.sf.jopt-simple/jopt-simple "4.9" ]
    [com.google.guava/guava "18.0" ]
    [com.google.code.findbugs/jsr305 "3.0.0" ]
    [joda-time/joda-time "2.8.1" ]
    ;;[org.zeroturnaround/zt-exec "1.8" ]
    ;;[org.zeroturnaround/zt-zip "1.8" ]
    [org.apache.axis/axis "1.4" ]
    [org.apache.axis/axis-jaxrpc "1.4" ]
    ;;[org.jetlang/jetlang "0.2.12" ]

    [com.fasterxml.jackson.core/jackson-core "2.5.4" ]
    [com.fasterxml.jackson.core/jackson-databind "2.5.4" ]
    [com.fasterxml.jackson.core/jackson-annotations "2.5.4" ]
    [org.jdom/jdom2 "2.0.6" ]

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

    ;;[org.apache.ivy/ivy "2.4.0" ]
    [org.apache.ant/ant "1.9.5" ]
    [org.apache.ant/ant-launcher "1.9.5" ]
    [org.apache.ant/ant-junit4 "1.9.5" ]
    [org.apache.ant/ant-junit "1.9.5" ]
    [org.apache.ant/ant-apache-log4j "1.9.5" :exclusions [log4j]]

    [ant-contrib/ant-contrib "1.0b3" :exclusions [ant]]
    [org.codehaus.gant/gant_groovy2.4 "1.9.12" ]

    [com.jolbox/bonecp "0.8.0.RELEASE" ]

    [org.apache.httpcomponents/httpcore-nio "4.4.1" ]
    [org.apache.httpcomponents/httpcore "4.4.1" ]
    [org.apache.httpcomponents/httpclient "4.5" ]
    [io.netty/netty-all "4.0.29.Final" ]

    [com.corundumstudio.socketio/netty-socketio "1.7.7" :exclusions [io.netty]]

    [org.eclipse.jetty/jetty-xml "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-server "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-continuation "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-servlet "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-server "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-util "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-security "9.3.0.v20150612"  ]
    [org.eclipse.jetty/jetty-webapp "9.3.0.v20150612"  ]
    [org.eclipse.jetty.websocket/websocket-api "9.3.0.v20150612"  ]
    [org.eclipse.jetty.websocket/websocket-common "9.3.0.v20150612"  ]
    [org.eclipse.jetty.websocket/websocket-servlet "9.3.0.v20150612"  ]
    [org.eclipse.jetty.websocket/websocket-client "9.3.0.v20150612"  ]
    [org.eclipse.jetty.websocket/websocket-server "9.3.0.v20150612"  ]

    [org.codehaus.groovy/groovy-all "2.4.3" ]

    [com.sun.tools/tools "1.8.0"  ]
    [org.javassist/javassist "3.20.0-GA"  ]

    [com.github.spullara.mustache.java/compiler "0.9.0" ]

    [org.freemarker/freemarker "2.3.23" ]

    [com.yahoo.platform.yui/yuicompressor "2.4.8"  :exclusions [rhino]]

    [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1" ]
    [com.h2database/h2 "1.4.187" ]
    [org.postgresql/postgresql "9.4-1201-jdbc41" ]

    [org.clojure/math.numeric-tower "0.0.4" ]
    [org.clojure/math.combinatorics "0.1.1" ]
    [org.clojure/tools.logging "0.3.1" ]
    [org.clojure/tools.nrepl "0.2.10" ]
    [org.clojure/tools.reader "0.9.2" ]
    [org.clojure/data.codec "0.1.0" ]
    [org.clojure/data.csv "0.1.2" ]
    [org.clojure/java.jdbc "0.3.7" ]
    [org.clojure/java.data "0.1.1" ]
    [org.clojure/java.jmx "0.3.1" ]
    [org.clojure/data.json "0.2.6" ]
    [org.clojure/data.xml "0.0.8" ]
    [org.clojure/core.cache "0.6.4" ]
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
    [boot/aether "2.1.2" ]  ;; this is causing the RELEASE_6 warning

    [org.clojure/clojure "1.7.0" ]
    [org.clojure/clojurescript "0.0-3308" ]

    [org.apache.shiro/shiro-core "1.2.3" ]
    [org.mozilla/rhino "1.7.7" ]
    [jline/jline "1.0" ]

    [net.mikera/cljunit "0.3.1" ]
    [junit/junit "4.12"  ]
    [com.googlecode.jslint4java/jslint4java "2.0.5" ]

  ]

  :source-paths #{"src/main/clojure" "src/main/java"}
  :buildVersion "0.9.0-SNAPSHOT"
  :buildDebug true
  :bldDir "b.out"
  :PID "skaro"
  :basedir (System/getProperty "user.dir"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(require '[clojure.tools.logging :as log]
         '[clojure.java.io :as io]
         '[clojure.string :as cstr]
         '[czlabclj.tpcl.antlib :as ant]
         '[czlabclj.tpcl.boot :as b]
         '[boot.core :as bcore])

(import '[org.apache.tools.ant Project Target Task]
        '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro minitask ""
  [func & forms]
  `(do (println (str ~func ":")) ~@forms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private basedir (a! (get-env :basedir)))

(def ^:private bootBuildDir (a! (b/fp! @basedir
                                       (get-ent :bldDir))))

(def ^:private testDir (a! (b/fp! @basedir "src" "test")))
(def ^:private srcDir (a! (b/fp! @basedir "src" "main")))

(def ^:private distribDir (a! (b/fp! @bootBuildDir "distrib")))
(def ^:private buildDir (a! (b/fp! @bootBuildDir "classes")))

(def ^:private packDir (a! (b/fp! @bootBuildDir "pack")))
(def ^:private libDir (a! (b/fp! @bootBuildDir "lib")))
(def ^:private qaDir (a! (b/fp! @bootBuildDir "test")))

(def ^:private reportTestDir (a! (b/fp! @qaDir "reports")))
(def ^:private buildTestDir (a! (b/fp! @qaDir "classes")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

(def COMPILE_OPTS {:debug (get-env :buildDebug)
                   :includeantruntime false
                   :fork true})

(def CPATH [[:location @buildDir]
            [:fileset {:dir @libDir} [[:include "*.jar"]]]])

(def TPATH (->> CPATH
                (cons [:location @buildTestDir])
                (into [])))

(def JAVAC_OPTS (merge {:srcdir (b/fp! @srcDir "java")
                        :destdir @buildDir
                        :target "1.8"
                        :debugLevel "lines,vars,source"}
                        COMPILE_OPTS))

(def CJPATH (->> CPATH
                 (cons [:location (b/fp! @srcDir "clojure")])
                 (into [])))

(def TJPATH (->> CJPATH
                 (concat [[:location (b/fp! @testDir "clojure")]
                          [:location @buildTestDir]])
                 (into [])))

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
    "clean-build"
    (do
      (ant/CleanDir (io/file @basedir (get-env :target-path)))
      (ant/RunTasks*
        (ant/AntDelete
          {}
          [[:fileset {:dir @bootBuildDir}
                     [[:include "**/*"]
                      [:exclude "classes/clojure/**"]]]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanBoot ""
  [& args]
  (minitask
    "clean-boot"
    (ant/CleanDir (io/file @basedir
                           (get-env :target-path)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preBuild ""
  [& args]
  (do
    (doseq [s [(b/fp! @distribDir "boot")
               (b/fp! @distribDir "exec")
               @libDir
               @qaDir
               @buildDir]]
      (.mkdirs (io/file s)))
    ;; get rid of debug logging during build!
    (ant/RunTarget*
      "pre-build"
      (ant/AntCopy {:todir @buildDir
                    :file (b/fp! @basedir "artifacts" "log4j.properties")} )
      (ant/AntCopy {:todir @buildDir
                    :file (b/fp! @basedir "artifacts" "logback.xml")} ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCmd ""

  [cmd workDir args]

  (do
    (ant/RunTarget*
      cmd
      (ant/AntExec {:executable cmd
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
        bd (get-env :bldDir)
        des (-> (io/file out mid)
                (.getParentFile)) ]
    (cond
      postgen
      (let [bf (io/file dir bd mid)]
        (spit bf
              (-> (slurp bf)
                  (.replaceAll "\\/\\*@@" "")
                  (.replaceAll "@@\\*\\/" "")))
        (ant/MoveFile bf des))

      (.isDirectory f)
      (if (= bd (.getName f))
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
                "--module-ids" mid "--out-dir" bd] }))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildJSLib ""

  [& args]

  (let [root (io/file @srcDir "js")
        ljs (io/file root (get-env :bldDir)) ]
    (ant/CleanDir ljs)
    (try
      (b/BabelTree root #'babel->cb)
      (finally
        (ant/DeleteDir ljs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwk ""
  []
  (do
    (ant/RunTarget*
      "compile-frwk"
      (ant/AntJavac
        JAVAC_OPTS
        [[:include "com/zotohlab/frwk/**/*.java"]
         [:include "org/**/*.java"]
         [:classpath CPATH]
         [:compilerarg COMPILER_ARGS]])
      (ant/AntCopy
        {:todir (b/fp! @buildDir "com/zotohlab/frwk")}
        [[:fileset {:dir (b/fp! @srcDir "java/com/zotohlab/frwk")}
         [[:exclude "**/*.java"]]]])
      (ant/AntJar
        {:destFile (b/fp! @distribDir
                          (str "exec/frwk-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "com/zotohlab/frwk/**"]
                    [:exclude "**/log4j.properties"]
                    [:exclude "**/logback.xml"]
                    [:exclude "demo/**"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileWFlow ""
  []
  (do
    (ant/RunTarget*
      "compile-wflow"
      (ant/AntJavac
        JAVAC_OPTS
        [[:include "com/zotohlab/wflow/**/*.java"]
         [:include "com/zotohlab/server/**/*.java"]
         [:classpath CPATH]
         [:compilerarg COMPILER_ARGS]])
      (ant/AntCopy
        {:todir (b/fp! @buildDir "com/zotohlab/wflow")}
        [[:fileset {:dir (b/fp! @srcDir "java/com/zotohlab/server")}
                   [[:exclude "**/*.java"]]]
         [:fileset {:dir (b/fp! @srcDir "java/com/zotohlab/wflow")}
                   [[:exclude "**/*.java"]]]])
      (ant/AntJar
        {:destFile (b/fp! @distribDir
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
(defn- compileSkaro ""
  []
  (let [t1 (ant/AntJavac
             JAVAC_OPTS
             [[:include "com/zotohlab/skaro/**/*.java"]
              [:include "com/zotohlab/mock/**/*.java"]
              [:include "com/zotohlab/tpcl/**/*.java"]
              [:classpath CPATH]
              [:compilerarg COMPILER_ARGS]])
        m (map
            (fn [d]
              (ant/AntCopy
                {:todir (b/fp! @buildDir "com/zotohlab" d)}
                [[:fileset {:dir (b/fp! @srcDir "java/com/zotohlab" d)}
                           [[:exclude "**/*.java"]]]]))
            ["skaro" "mock" "tpcl"])
        t2 (ant/AntJar
             {:destFile (b/fp! @distribDir
                               (str "boot/loaders-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
                        [[:include "com/zotohlab/skaro/loaders/**"] ]]])
        t3 (ant/AntJar
             {:destFile (b/fp! @distribDir
                               (str "exec/skaroj-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
              [[:include "com/zotohlab/skaro/**"]
              [:include "com/zotohlab/mock/**"]
              [:include "com/zotohlab/tpcl/**"]
              [:exclude "**/log4j.properties"]
              [:exclude "**/logback.xml"]
              [:exclude "demo/**"]]]]) ]

    (ant/RunTarget
      "compile-skaro"
      (-> (vec m)
          (concat [t2 t3])
          (conj t1)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJavaDemo ""
  []
  (let [t1 (ant/AntJavac
             JAVAC_OPTS
             [[:include "demo/**/*.java"]
              [:classpath CPATH]
              [:compilerarg COMPILER_ARGS]])
        m (map
            (fn [d]
              (ant/AntCopy
                {:todir (b/fp! @buildDir "demo" d)}
                [[:fileset {:dir (b/fp! @srcDir "java/demo" d)}
                           [[:exclude "**/*.java"]]]]))
            ["splits" "flows" ]) ]
    (ant/RunTarget
      "compile-java-demo"
      (-> (vec m) (conj t1)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljJMX ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/xlib/jmx")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljCrypto ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/xlib/crypto")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDbio ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/xlib/dbio")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljNet ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/xlib/netty"
                                       "czlabclj/xlib/net")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljUtil ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/xlib/util"
                                       "czlabclj/xlib/i18n")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljXLib ""
  []
  (let [m (map
            #(apply % [])
            [ #'cljUtil #'cljCrypto #'cljDbio #'cljNet #'cljJMX ])
        t2 (ant/AntCopy
             {:todir (b/fp! @buildDir "czlabclj/xlib")}
             [[:fileset {:dir (b/fp! @srcDir "clojure/czlabclj/xlib")}
                        [[:exclude "**/*.clj"]]]])
        t3 (ant/AntJar
             {:destFile (b/fp! @distribDir
                               (str "exec/xlib-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
                        [[:include "czlabclj/xlib/**"]
                         [:exclude "**/log4j.properties"]
                         [:exclude "**/logback.xml"]
                         [:exclude "demo/**"]]]]) ]
    (ant/RunTarget
      "compile-xlib"
      (concat (vec m) [t2 t3]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljTpcl ""
  []
  (do
    (ant/RunTarget*
      "compile-tpcl"
      (ant/AntJava
        CLJC_OPTS
        (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                           "czlabclj/tpcl")]]
                CJNESTED))
      (ant/AntCopy
        {:todir (b/fp! @buildDir "czlabclj/tpcl")}
        [[:fileset {:dir (b/fp! @srcDir "clojure/czlabclj/tpcl")}
                   [[:exclude "**/*.clj"]]]])
      (ant/AntJar
        {:destFile (b/fp! @distribDir
                          (str "exec/tpcl-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "czlabclj/tpcl/**"]
                    [:exclude "**/log4j.properties"]
                    [:exclude "**/logback.xml"]
                    [:exclude "demo/**"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDemo ""
  [& args]
  (do
    (ant/RunTarget*
      "compile-cjdemo"
      (ant/AntJava
        CLJC_OPTS
        (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                           "demo/file" "demo/fork"
                                           "demo/http" "demo/jetty"
                                           "demo/jms" "demo/mvc"
                                           "demo/pop3" "demo/steps"
                                           "demo/tcpip" "demo/timer")]]
                CJNESTED))
      (ant/AntCopy
        {:todir (b/fp! @buildDir "demo")}
        [[:fileset {:dir (b/fp! @srcDir "clojure/demo")}
                   [[:exclude "**/*.clj"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMain ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/tardis/impl")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMvc ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/tardis/mvc")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAuth ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/tardis/auth")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisIO ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/tardis/io")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisEtc ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/tardis/etc")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisCore ""
  [& args]
  (ant/AntJava
    CLJC_OPTS
    (concat [[:argvalues (b/FmtCljNsps (b/fp! @srcDir "clojure")
                                       "czlabclj/tardis/core")]]
            CJNESTED)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAll ""
  []
  (let [m (map #(apply % [])
               [ #'tardisCore #'tardisEtc #'tardisAuth
                 #'tardisIO #'tardisMvc #'tardisMain ])
        t2 (ant/AntCopy
             {:todir (b/fp! @buildDir "czlabclj/tardis")}
             [[:fileset {:dir (b/fp! @srcDir "clojure/czlabclj/tardis")}
                        [[:exclude "**/*.meta"]
                         [:exclude "**/*.clj"]]]])
        t3 (ant/AntJar
             {:destFile (b/fp! @distribDir
                               (str "exec/tardis-" @buildVersion ".jar"))}
             [[:fileset {:dir @buildDir}
                        [[:include "czlabclj/tardis/**"] ]]]) ]
    (ant/RunTarget
      "compile-tardis"
      (concat (vec m) [t2 t3]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- distroInit ""
  []
  (let [root (io/file @packDir)]
    (ant/CleanDir root)
    (doseq [d ["conf" ["dist" "boot"] ["dist" "exec"] "bin"
               ["etc" "ems"] "lib" "logs"
               "docs" "pods" "tmp" "apps"]]
      (.mkdirs (if (vector? d)
                 (apply io/file root d)
                 (io/file root d))))
    (spit (io/file root "VERSION") @buildVersion)
    (ant/RunTarget*
      "distro-init"
      (ant/AntCopy
        {:todir (b/fp! @packDir "etc")}
        [[:fileset {:dir (b/fp! @basedir "etc")} ]])
      (ant/AntCopy
        {:todir (b/fp! @packDir "conf")}
        [[:fileset {:dir (b/fp! @basedir "etc/conf")} ]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyJsFiles ""

  [& args]

  (ant/AntCopy
    {:todir (b/fp! @packDir "public/vendors")}
    [[:fileset {:dir (b/fp! @buildDir "js")}
               [[:include "**/*.js"]]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""

  [& args]

  (let []
    (ant/RunTarget*
      "pack-res"
      (ant/AntCopy
        {:todir (b/fp! @packDir "etc/ems")
         :flatten true}
        [[:fileset {:dir (b/fp! @srcDir "clojure")}
                   [[:include "**/*.meta"]]]])
      (ant/AntCopy
        {:todir (b/fp! @packDir "etc")}
        [[:fileset {:dir (b/fp! @basedir "etc")} []]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDocs ""

  [& args]

  (ant/CleanDir (io/file @packDir "docs" "jsdoc"))
  (ant/CleanDir (io/file @packDir "docs" "api"))
  (do
    (ant/RunTarget*
      "pack-docs"
      (ant/AntCopy
        {:todir (b/fp! @packDir "docs")}
        [[:fileset {:dir (b/fp! @basedir "docs")}
                   [[:exclude "dummy.txt"]]]])
      (ant/AntJavadoc
        {:destdir (b/fp! @packDir "docs/api")
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
         [[:fileset {:dir (b/fp! @srcDir "java")}
                    [[:exclude "demo/**"]
                     [:include "**/*.java"]]]
          [:classpath CPATH]])

      (ant/AntJava
        CLJC_OPTS
        (concat [[:argvalues ["czlabclj.tpcl.codox"]]]
                CJNESTED))

      (ant/AntJava
        {:classname "czlabclj.tpcl.codox"
         :fork true
         :failonerror true}
        [[:argvalues [@basedir
                      (b/fp! @srcDir "clojure")
                      (b/fp! @packDir "docs/api")]]
         [:classpath CJPATH]])

      (copyJsFiles)

      (ant/AntExec
        {:executable "jsdoc"
         :dir @basedir
         :spawn true}
        [[:argvalues ["-c" "mvn/js/jsdoc-conf.json"
                      "-d" (b/fp! @packDir "docs/jsdoc") ]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packSrc ""

  [& args]

  (do
    (ant/RunTarget*
      "pack-src"
      (ant/AntCopy
        {:todir (b/fp! @packDir "src/main/clojure")}
        [[:fileset {:dir (b/fp! @srcDir "clojure")} ]])
      (ant/AntCopy
        {:todir (b/fp! @packDir "src/main/java")}
        [[:fileset {:dir (b/fp! @srcDir "java")} ]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLics ""

  [& args]

  (do
    (ant/RunTarget*
      "pack-lics"
      (ant/AntCopy
        {:todir (b/fp! @packDir "lics")}
        [[:fileset {:dir (b/fp! @basedir "lics") } ]])

      (ant/AntCopy
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

  (do
    (ant/RunTarget*
      "pack-dist"
      (ant/AntCopy
        {:todir (b/fp! @packDir "dist/exec")}
        [[:fileset {:dir (b/fp! @distribDir "exec")}
                   [[:include "*.jar"]]]])

      (ant/AntCopy
        {:todir (b/fp! @packDir "dist/boot")}
        [[:fileset {:dir (b/fp! @distribDir "boot")}
                   [[:include "*.jar"]]]])

      (ant/AntJar
        {:destFile (b/fp! @packDir
                          (str "dist/exec/clj-" @buildVersion ".jar"))}
        [[:fileset {:dir @buildDir}
                   [[:include "clojure/**"]]]])

      (copyJsFiles ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLibs ""

  [& args]

  (do
    (ant/RunTarget*
      "pack-libs"
      (ant/AntCopy
        {:todir (b/fp! @packDir "lib")}
        [[:fileset {:dir @libDir} ]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""

  [& args]

  (do
    (ant/RunTarget*
      "pack-bin"
      (ant/AntCopy
        {:todir (b/fp! @packDir "bin")}
        [[:fileset {:dir (b/fp! @basedir "bin")} ]])

      (ant/AntChmod
        {:dir (b/fp! @packDir "bin")
         :perm "755"
         :includes "*"} ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packAll ""

  [& args]

  (ant/CleanDir (io/file @packDir "tmp"))
  (do
    (ant/RunTarget*
      "pack-all"
      (ant/AntTar
        {:destFile (b/fp! @distribDir
                          (str (get-env :PID)
                               "-" @buildVersion ".tar.gz"))
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
      (ant/RunTarget
        "libjars"
        (for [j (seq jars)]
          (ant/AntCopy {:file (b/fp! (:dir j) (:path j))
                        :todir to})))
      (format "copied (%d) jar-files to %s" (count jars) to))
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preTest ""
  []
  (.mkdirs (io/file @reportTestDir))
  (.mkdirs (io/file @buildTestDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwkTest ""
  []
  (ant/RunTarget*
    "compile-java-test"
    (ant/AntJavac (merge JAVAC_OPTS
                         {:srcdir (b/fp! @testDir "java")
                          :destdir @buildTestDir})
                  [[:include "**/*.java"]
                   [:classpath TPATH]
                   [:compilerarg COMPILER_ARGS]])

    (ant/AntCopy {:todir @buildTestDir}
                 [[:fileset {:dir (b/fp! @testDir "java") }
                            [[:exclude "**/*.java"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileCljTest ""
  []
  (ant/RunTarget*
    "compile-clj-test"
    (ant/AntJava
      CLJC_OPTS
      [[:sysprops (assoc CLJC_SYSPROPS :clojure.compile.path @buildTestDir)]
       [:classpath TJPATH]
       [:argvalues (b/FmtCljNsps (b/fp! @testDir "clojure")
                                 "testcljc/util"
                                 "testcljc/net"
                                 "testcljc/i18n"
                                 "testcljc/crypto"
                                 "testcljc/dbio")]])
    (ant/AntCopy
      {:todir @buildTestDir}
      [[:fileset {:dir (b/fp! @testDir "clojure") }
                 [[:exclude "**/*.clj"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCljTest ""
  []
  (ant/RunTarget*
    "run-clj-test"
    (ant/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath TJPATH]
       [:formatter {:type "plain"
                    :useFile false}]
       [:test {:name "czlab.frwk.util.ClojureJUnit"
               :todir @reportTestDir}
              [[:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runJavaTest ""
  []
  (ant/RunTarget*
    "run-java-test"
    (ant/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath TPATH]
       [:formatter {:type "plain"
                    :useFile false}]
       [:batchtest {:todir @reportTestDir}
                   [[:fileset {:dir @buildTestDir}
                              [[:include "**/JUTest.*"]]]
                    [:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildCljTest ""
  []
  (minitask
    "preTest"
    (ant/DeleteDir (io/file (b/fp! @buildTestDir "czlab")))
    (preTest))
  (compileFrwkTest)
  (compileCljTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildJavaTest ""
  []
  (minitask
    "preTest"
    (ant/DeleteDir (io/file (b/fp! @buildTestDir "czlab")))
    (preTest))
  (compileFrwkTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testclj "test-clj-frwk"
  []
  (buildCljTest)
  (runCljTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testjava "test-java-frwk"
  []
  (buildJavaTest)
  (runJavaTest))

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
