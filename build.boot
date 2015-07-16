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
    [org.apache.a/ant "1.9.5" ]
    [org.apache.a/ant-launcher "1.9.5" ]
    [org.apache.a/ant-junit4 "1.9.5" ]
    [org.apache.a/ant-junit "1.9.5" ]
    [org.apache.a/ant-apache-log4j "1.9.5" :exclusions [log4j]]

    [ant-contrib/ant-contrib "1.0b3" :exclusions [ant]]

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

    ;;[org.codehaus.ga/gant_groovy2.4 "1.9.12" ]
    ;;[org.codehaus.groovy/groovy-all "2.4.3" ]

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
    ;; boot/clj stuff
    [boot/base "2.1.2"]
    [boot/core "2.1.2"]
    [boot/pod "2.1.2"]
    [boot/worker "2.1.2"]
    [boot/aether "2.1.2" ]  ;; this is causing the RELEASE_6 warning

    [org.clojure/clojure "1.7.0" ]
    [org.clojure/clojurescript "0.0-3308" ]

    [org.apache.shiro/shiro-core "1.2.3" ]
    [org.mozilla/rhino "1.7.7" ]
    [jline/jline "2.12.1" ]

    [net.mikera/cljunit "0.3.1" ]
    [junit/junit "4.12"  ]
    [com.googlecode.jslint4java/jslint4java "2.0.5" ]

  ]

  :source-paths #{"src/main/clojure" "src/main/java"}
  :buildVersion "0.9.0-SNAPSHOT"
  :buildDebug true
  :DOMAIN "com.zotohlab.skaro"
  :PID "skaro"
  :basedir (System/getProperty "user.dir"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

(import '[org.apache.tools.ant Project Target Task]
        '[java.io File])

(require '[clojure.tools.logging :as log]
         '[clojure.java.io :as io]
         '[clojure.string :as cs]
         '[czlabclj.tpcl.antlib :as a]
         '[czlabclj.tpcl.boot :as b :refer :all]
         '[boot.core :as bc])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:dynamic *genjars* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/BootEnvVars)

(set-env! :bootBuildDir (fp! (ge :basedir) (ge :bld)))
(set-env! :tstDir (fp! (ge :basedir) "src" "test"))
(set-env! :srcDir (fp! (ge :basedir) "src" "main"))
(set-env! :distDir (fp! (ge :bootBuildDir) "dist"))
(set-env! :buildDir (fp! (ge :bootBuildDir) "classes"))
(set-env! :packDir (fp! (ge :bootBuildDir) "pack"))
(set-env! :libDir (fp! (ge :basedir) (ge :target-path)))
(set-env! :qaDir (fp! (ge :bootBuildDir) "test"))
(set-env! :reportTestDir (fp! (ge :qaDir) "reports"))
(set-env! :buildTestDir (fp! (ge :qaDir) "classes"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(set-env! :COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

(set-env! :COMPILE_OPTS {:debug (ge :buildDebug)
                         :includeantruntime false
                         :fork true})

(set-env! :CPATH [[:location (ge :buildDir)]
                  [:fileset {:dir (ge :libDir)}
                            [[:include "*.jar"]]]])

(set-env! :TPATH (->> (ge :CPATH)
                      (cons [:location (ge :buildTestDir)])
                      (into [])))

(set-env! :JAVAC_OPTS (merge {:srcdir (fp! (ge :srcDir) "java")
                              :destdir (ge :buildDir)
                              :target "1.8"
                              :debugLevel "lines,vars,source"}
                              (ge :COMPILE_OPTS)))

(set-env! :CJPATH (->> (ge :CPATH)
                       (cons [:location (fp! (ge :srcDir) "clojure")])
                       (into [])))

(set-env! :TJPATH (->> (ge :CJPATH)
                       (concat [[:location (fp! (ge :tstDir) "clojure")]
                                [:location (ge :buildTestDir)]])
                       (into [])))

(set-env! :CLJC_OPTS {:classname "clojure.lang.Compile"
                      :fork true
                      :failonerror true
                      :maxmemory "2048m"})

(set-env! :CLJC_SYSPROPS {:clojure.compile.warn-on-reflection true
                          :clojure.compile.path (ge :buildDir) })

(set-env! :CJNESTED_RAW [[:sysprops (assoc (ge :CLJC_SYSPROPS)
                                           :clojure.compile.warn-on-reflection
                                           false)]
                         [:classpath (ge :CJPATH)]])

(set-env! :CJNESTED [[:sysprops (ge :CLJC_SYSPROPS)]
                     [:classpath (ge :CJPATH)]])

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
    "clean/build"
    (do
      (a/CleanDir (io/file (ge :basedir)
                             (ge :target-path)))
      (a/RunTasks*
        (a/AntDelete {}
          [[:fileset {:dir (ge :bootBuildDir)}
                     [[:include "**/*"]
                      [:exclude "pack/**"]
                      [:exclude "classes/clojure/**"]]]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cleanBoot ""
  [& args]
  (minitask
    "clean/boot"
    (a/CleanDir (io/file (ge :basedir)
                           (ge :target-path)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preBuild ""
  [& args]
  (do
    (doseq [s [(ge :distDir)
               (ge :libDir)
               (ge :qaDir)
               (ge :buildDir)]]
      (.mkdirs (io/file s)))
    ;; get rid of debug logging during build!
    (a/RunTarget* "pre-build"
      (a/AntCopy {:todir (ge :buildDir)}
                   [[:fileset {:dir (fp! (ge :basedir) "artifacts")}
                              [[:include "log4j.properties"]
                               [:include "logback.xml"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCmd ""

  [cmd workDir args]

  (a/RunTarget* cmd
    (a/AntExec {:executable cmd
                  :dir workDir
                  :spawn false}
                 [[:args (or args [])]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babel->cb ""

  [f & {:keys [postgen dir paths]
        :or {:postgen false
             :dir false
             :paths []}
        :as args }]

  (let [out (io/file (ge :buildDir) "js")
        dir (io/file (ge :srcDir) "js")
        mid (cs/join "/" paths)
        bd (ge :bld)
        des (-> (io/file out mid)
                (.getParentFile)) ]
    (cond
      postgen
      (let [bf (io/file dir bd mid)]
        (spit bf
              (-> (slurp bf)
                  (.replaceAll "\\/\\*@@" "")
                  (.replaceAll "@@\\*\\/" "")))
        (a/MoveFile bf des))

      (.isDirectory f)
      (if (= bd (.getName f))
        nil
        {})

      :else
      (if-not (.endsWith mid ".js")
        (do
          (a/CopyFile (io/file dir mid) des)
          nil)
        {:work-dir dir
         :args ["--modules"
                "amd"
                "--module-ids" mid "--out-dir" bd] }))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babel! ""

  [& args]

  (let [root (io/file (ge :srcDir) "js")
        ljs (io/file root (ge :bld)) ]
    (a/CleanDir ljs)
    (try
      (b/BabelTree root #'babel->cb)
      (finally
        (a/DeleteDir ljs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwk

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "com/zotohlab/frwk"))
  (a/CleanDir (io/file (ge :buildDir) "org"))
  (let [t1 (a/AntJavac
              (ge :JAVAC_OPTS)
              [[:include "com/zotohlab/frwk/**/*.java"]
               [:include "org/**/*.java"]
               [:classpath (ge :CPATH)]
               [:compilerarg (ge :COMPILER_ARGS) ]])
        t2 (a/AntCopy
              {:todir (fp! (ge :buildDir) "com/zotohlab/frwk")}
              [[:fileset {:dir (fp! (ge :srcDir) "java/com/zotohlab/frwk")}
               [[:exclude "**/*.java"]]]])
        t3 (a/AntJar
              {:destFile (fp! (ge :distDir)
                                (str "frwk-" (ge :buildVersion) ".jar"))}
              [[:fileset {:dir (ge :buildDir) }
                         [[:include "com/zotohlab/frwk/**"]
                          [:exclude "**/log4j.properties"]
                          [:exclude "**/logback.xml"]
                          [:exclude "demo/**"]]]]) ]
    (->> (if *genjars*
           [t1 t2 t3]
           [t1 t2])
         (a/RunTarget "compile/frwk"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileWFlow

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "com/zotohlab/server"))
  (a/CleanDir (io/file (ge :buildDir) "com/zotohlab/wflow"))
  (let [t1 (a/AntJavac
              (ge :JAVAC_OPTS)
              [[:include "com/zotohlab/wflow/**/*.java"]
               [:include "com/zotohlab/server/**/*.java"]
               [:classpath (ge :CPATH) ]
               [:compilerarg (ge :COMPILER_ARGS) ]])
        t2 (a/AntCopy
              {:todir (fp! (ge :buildDir) "com/zotohlab/wflow")}
              [[:fileset {:dir (fp! (ge :srcDir) "java/com/zotohlab/server")}
                         [[:exclude "**/*.java"]]]
               [:fileset {:dir (fp! (ge :srcDir) "java/com/zotohlab/wflow")}
                         [[:exclude "**/*.java"]]]])
        t3 (a/AntJar
              {:destFile (fp! (ge :distDir)
                              (str "wflow-" (ge :buildVersion) ".jar"))}
              [[:fileset {:dir (ge :buildDir)}
                         [[:include "com/zotohlab/server/**"]
                          [:include "com/zotohlab/wflow/**"]
                          [:exclude "**/log4j.properties"]
                          [:exclude "**/logback.xml"]
                          [:exclude "demo/**"]]]]) ]
    (->> (if *genjars*
           [t1 t2 t3]
           [t1 t2])
         (a/RunTarget "compile/wflow"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSkaro

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "com/zotohlab/skaro"))
  (a/CleanDir (io/file (ge :buildDir) "com/zotohlab/mock"))
  (a/CleanDir (io/file (ge :buildDir) "com/zotohlab/tpcl"))
  (let [t1 (a/AntJavac
             (ge :JAVAC_OPTS)
             [[:include "com/zotohlab/skaro/**/*.java"]
              [:include "com/zotohlab/mock/**/*.java"]
              [:include "com/zotohlab/tpcl/**/*.java"]
              [:classpath (ge :CPATH) ]
              [:compilerarg (ge :COMPILER_ARGS) ]])
        m (map
            (fn [d]
              (a/AntCopy
                {:todir (fp! (ge :buildDir) "com/zotohlab" d)}
                [[:fileset {:dir (fp! (ge :srcDir) "java/com/zotohlab" d)}
                           [[:exclude "**/*.java"]]]]))
            ["skaro" "mock" "tpcl"])
        ts (into [] (cons t1 m))
        t2 (a/AntJar
             {:destFile (fp! (ge :distDir)
                               (str "skaro-ld-" (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :buildDir) }
                        [[:include "com/zotohlab/skaro/loaders/**"] ]]])
        t3 (a/AntJar
             {:destFile (fp! (ge :distDir)
                               (str "skaro-rt-" (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :buildDir) }
              [[:include "com/zotohlab/skaro/**"]
              [:include "com/zotohlab/mock/**"]
              [:include "com/zotohlab/tpcl/**"]
              [:exclude "**/log4j.properties"]
              [:exclude "**/logback.xml"]
              [:exclude "demo/**"]]]]) ]

    (->> (if *genjars*
           (concat ts [t3])
           ts)
         (a/RunTarget "compile/skaro"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJavaDemo

  ""
  []

  (let [dirs ["splits" "flows"]]
    (doall (map #(a/CleanDir (fp! (ge :buildDir) "demo" %)) dirs))
    (let [t1 (a/AntJavac
               (ge :JAVAC_OPTS)
               [[:include "demo/**/*.java"]
                [:classpath (ge :CPATH) ]
                [:compilerarg (ge :COMPILER_ARGS) ]])
          m (map
              (fn [d]
                (a/AntCopy
                  {:todir (fp! (ge :buildDir) "demo" d)}
                  [[:fileset {:dir (fp! (ge :srcDir) "java/demo" d)}
                             [[:exclude "**/*.java"]]]]))
              dirs) ]
      (a/RunTarget
        "compile/demo"
        (cons t1 m)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljJMX

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/jmx"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/xlib/jmx")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljCrypto

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/crypto"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/xlib/crypto")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDbio

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/dbio"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/xlib/dbio")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljNet

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/netty"))
  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/net"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/xlib/netty"
                                       "czlabclj/xlib/net")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljUtil

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/util"))
  (a/CleanDir (io/file (ge :buildDir) "czlabclj/xlib/i18n"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/xlib/util"
                                       "czlabclj/xlib/i18n")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljXLib

  ""
  []

  (let [m (map
            #(apply % [])
            [ #'cljUtil #'cljCrypto #'cljDbio #'cljNet #'cljJMX ])
        t2 (a/AntCopy
             {:todir (fp! (ge :buildDir) "czlabclj/xlib")}
             [[:fileset {:dir (fp! (ge :srcDir) "clojure/czlabclj/xlib")}
                        [[:exclude "**/*.clj"]]]])
        t3 (a/AntJar
             {:destFile (fp! (ge :distDir)
                               (str "xlib-" (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :buildDir) }
                        [[:include "czlabclj/xlib/**"]
                         [:exclude "**/log4j.properties"]
                         [:exclude "**/logback.xml"]
                         [:exclude "demo/**"]]]]) ]
    (->> (if *genjars*
           (concat m [t2 t3])
           m)
         (a/RunTarget "clj/xlib"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljTpcl

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tpcl"))
  (let [t1 (a/AntJava
              (ge :CLJC_OPTS)
              (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                                 "czlabclj/tpcl")]]
                      (ge :CJNESTED_RAW)))
        t2 (a/AntCopy
              {:todir (fp! (ge :buildDir) "czlabclj/tpcl")}
              [[:fileset {:dir (fp! (ge :srcDir) "clojure/czlabclj/tpcl")}
                         [[:exclude "**/*.clj"]]]])
        t3 (a/AntJar
              {:destFile (fp! (ge :distDir)
                                (str "tpcl-" (ge :buildVersion) ".jar"))}
              [[:fileset {:dir (ge :buildDir) }
                         [[:include "czlabclj/tpcl/**"]
                          [:exclude "**/log4j.properties"]
                          [:exclude "**/logback.xml"]
                          [:exclude "demo/**"]]]]) ]
    (->> (if *genjars*
           [t1 t2 t3]
           [t1 t2])
         (a/RunTarget "clj/tpcl"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDemo

  ""
  []

  (let [dirs ["demo/file" "demo/fork"
              "demo/http" "demo/jetty"
              "demo/jms" "demo/mvc"
              "demo/pop3" "demo/steps"
              "demo/tcpip" "demo/timer"]]
    (doall (map #(a/CleanDir (fp! (ge :buildDir) %)) dirs))
    (a/RunTarget* "clj/demo"
      (a/AntJava
        (ge :CLJC_OPTS)
        (concat [[:argvalues (apply b/FmtCljNsps
                                    (fp! (ge :srcDir) "clojure")
                                    dirs)]]
                (ge :CJNESTED)))
      (a/AntCopy
        {:todir (fp! (ge :buildDir) "demo")}
        [[:fileset {:dir (fp! (ge :srcDir) "clojure/demo")}
                   [[:exclude "**/*.clj"]]]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMain

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tardis/impl"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/tardis/impl")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisMvc

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tardis/mvc"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/tardis/mvc")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAuth

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tardis/auth"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/tardis/auth")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisIO

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tardis/io"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/tardis/io")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisEtc

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tardis/etc"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/tardis/etc")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisCore

  ""
  []

  (a/CleanDir (io/file (ge :buildDir) "czlabclj/tardis/core"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlabclj/tardis/core")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- tardisAll

  ""
  []

  (let [m (map #(apply % [])
                [#'tardisCore #'tardisEtc #'tardisAuth
                 #'tardisIO #'tardisMvc #'tardisMain ])
        t2 (a/AntCopy
             {:todir (fp! (ge :buildDir) "czlabclj/tardis")}
             [[:fileset {:dir (fp! (ge :srcDir) "clojure/czlabclj/tardis")}
                        [[:exclude "**/*.meta"]
                         [:exclude "**/*.clj"]]]])
        ts (into [] (concat m [t2]))
        t3 (a/AntJar
             {:destFile (fp! (ge :distDir)
                               (str "tardis-" (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :buildDir) }
                        [[:include "czlabclj/tardis/**"] ]]]) ]
    (->> (if *genjars*
           (conj ts t3)
           ts)
         (a/RunTarget "clj/tardis"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- distroInit ""
  []
  (let [root (io/file (ge :packDir)) ]
    (a/CleanDir root)
    (doseq [d ["conf" "dist" "bin"
               ["etc" "ems"] "lib" "docs" ]]
      (.mkdirs (if (vector? d)
                 (apply io/file root d)
                 (io/file root d))))
    (a/RunTarget*
      "pack/init"
      (a/AntCopy
        {:todir (fp! (ge :packDir) "etc")}
        [[:fileset {:dir (fp! (ge :basedir) "etc")} ]])
      (a/AntCopy
        {:todir (fp! (ge :packDir) "conf")}
        [[:fileset {:dir (fp! (ge :basedir) "etc/conf")} ]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyJsFiles ""

  []

  (a/AntCopy
    {:todir (fp! (ge :packDir) "src/main/js")}
    [[:fileset {:dir (fp! (ge :buildDir) "js")}
               [[:include "**/*.js"]]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""

  []

  (a/RunTarget*
    "pack/res"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "etc/ems")
       :flatten true}
      [[:fileset {:dir (fp! (ge :srcDir) "clojure")}
                 [[:include "**/*.meta"]]]])
    (a/AntCopy
      {:todir (fp! (ge :packDir) "etc")}
      [[:fileset {:dir (fp! (ge :basedir) "etc")} []]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDocs ""

  []

  (a/CleanDir (io/file (ge :packDir) "docs" "jsdoc"))
  (a/CleanDir (io/file (ge :packDir) "docs" "api"))

  (a/RunTarget*
    "pack/docs"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "docs")}
      [[:fileset {:dir (fp! (ge :basedir) "docs")}
                 [[:exclude "dummy.txt"]]]])
    (a/AntJavadoc
      {:destdir (fp! (ge :packDir) "docs/api")
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
       [[:fileset {:dir (fp! (ge :srcDir) "java")}
                  [[:exclude "demo/**"]
                   [:include "**/*.java"]]]
        [:classpath (ge :CPATH) ]])

    (a/AntJava
      (ge :CLJC_OPTS)
      (concat [[:argvalues ["czlabclj.tpcl.codox"]]]
              (ge :CJNESTED_RAW)))

    (a/AntJava
      {:classname "czlabclj.tpcl.codox"
       :fork true
       :failonerror true}
      [[:argvalues [(ge :basedir)
                    (fp! (ge :srcDir) "clojure")
                    (fp! (ge :packDir) "docs/api")]]
       [:classpath (ge :CJPATH) ]])

    ;;(copyJsFiles)

    (a/AntExec
      {:executable "jsdoc"
       :dir (ge :basedir)
       :spawn true}
      [[:argvalues ["-c" "mvn/js/jsdoc-conf.json"
                    "-d" (fp! (ge :packDir) "docs/jsdoc") ]]])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packSrc ""

  []

  (a/RunTarget*
    "pack/src"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "src/main/clojure")}
      [[:fileset {:dir (fp! (ge :srcDir) "clojure")} ]])
    (a/AntCopy
      {:todir (fp! (ge :packDir) "src/main/java")}
      [[:fileset {:dir (fp! (ge :srcDir) "java")} ]])
    (copyJsFiles)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLics ""

  []

  (a/RunTarget*
    "pack/lics"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "lics")}
      [[:fileset {:dir (fp! (ge :basedir) "lics") } ]])
    (a/AntCopy
      {:todir (fp! (ge :packDir) "lics")}
      [[:fileset {:dir (ge :basedir)}
                 [[:include "*.html"]
                  [:include "LICENSE"]
                  [:include "*.txt"] ]]])
    (a/AntCopy
      {:todir (ge :packDir) :flatten true}
      [[:fileset {:dir (ge :basedir)}
                 [[:include "README.md"]
                  [:include "pom.xml"] ]]]))
  (b/ReplaceFile (io/file (ge :packDir) "pom.xml")
                 #(cs/replace % "@@VERSION@@" (ge :buildVersion))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDist ""

  []

  (a/RunTarget*
    "pack/dist"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "dist")}
      [[:fileset {:dir (ge :distDir) }
                 [[:include "**/skaro-ld*.jar"]]]])
    (a/AntCopy
      {:todir (fp! (ge :packDir) "dist")}
      [[:fileset {:dir (ge :distDir) }
                 [[:include "**/skaro-rt*.jar"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLibs ""

  []

  (do
    (a/RunTarget*
      "pack/lib"
      (a/AntCopy
        {:todir (fp! (ge :packDir) "lib")}
        [[:fileset {:dir (ge :libDir)} ]]))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""

  []

  (do
    (a/RunTarget*
      "pack/bin"
      (a/AntCopy
        {:todir (fp! (ge :packDir) "bin")}
        [[:fileset {:dir (fp! (ge :basedir) "bin")} ]])

      (a/AntChmod
        {:dir (fp! (ge :packDir) "bin")
         :perm "755"
         :includes "*"} ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packAll ""

  [& args]

  (a/RunTarget*
    "pack/all"
    (a/AntTar
      {:destFile (fp! (ge :distDir)
                        (str (ge :PID)
                             "-" (ge :buildVersion) ".tar.gz"))
       :compression "gzip"}
      [[:tarfileset {:dir (ge :packDir)}
                    [[:exclude "apps/**"]
                     [:exclude "bin/**"]]]
       [:tarfileset {:dir (ge :packDir) :mode "755"}
                    [[:include "bin/**"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- preTest ""
  []
  (.mkdirs (io/file (ge :reportTestDir)))
  (.mkdirs (io/file (ge :buildTestDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwkTest ""
  []
  (a/RunTarget*
    "compile/java#test"
    (a/AntJavac (merge (ge :JAVAC_OPTS)
                         {:srcdir (fp! (ge :tstDir) "java")
                          :destdir (ge :buildTestDir)})
                  [[:include "**/*.java"]
                   [:classpath (ge :TPATH)]
                   [:compilerarg (ge :COMPILER_ARGS)]])

    (a/AntCopy {:todir (ge :buildTestDir)}
                 [[:fileset {:dir (fp! (ge :tstDir) "java") }
                            [[:exclude "**/*.java"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileCljTest ""
  []
  (a/RunTarget*
    "compile/clj#test"
    (a/AntJava
      (ge :CLJC_OPTS)
      [[:sysprops (assoc (ge :CLJC_SYSPROPS)
                         :clojure.compile.path (ge :buildTestDir))]
       [:classpath (ge :TJPATH)]
       [:argvalues (b/FmtCljNsps (fp! (ge :tstDir) "clojure")
                                 "testcljc/util"
                                 "testcljc/net"
                                 "testcljc/i18n"
                                 "testcljc/crypto"
                                 "testcljc/dbio")]])
    (a/AntCopy
      {:todir (ge :buildTestDir)}
      [[:fileset {:dir (fp! (ge :tstDir) "clojure") }
                 [[:exclude "**/*.clj"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runCljTest ""
  []
  (a/RunTarget*
    "run/clj#test"
    (a/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TJPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:test {:name "czlab.frwk.util.ClojureJUnit"
               :todir (ge :reportTestDir)}
              [[:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runJavaTest ""
  []
  (a/RunTarget*
    "run/java#test"
    (a/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:batchtest {:todir (ge :reportTestDir)}
                   [[:fileset {:dir (ge :buildTestDir)}
                              [[:include "**/JUTest.*"]]]
                    [:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildCljTest ""
  []
  (minitask
    "preTest"
    (a/DeleteDir (io/file (fp! (ge :buildTestDir) "czlab")))
    (preTest))
  (compileFrwkTest)
  (compileCljTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- buildJavaTest ""
  []
  (minitask
    "preTest"
    (a/DeleteDir (io/file (fp! (ge :buildTestDir) "czlab")))
    (preTest))
  (compileFrwkTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- jar! ""
  []

  (a/RunTasks*
    (a/AntCopy {:todir (ge :buildDir)}
                   [[:fileset {:dir (fp! (ge :srcDir) "resources")}]]))

  (b/ReplaceFile (fp! (ge :buildDir) "com/zotohlab/skaro/version.properties")
                 #(cs/replace % "@@pom.version@@" (ge :buildVersion)))

  (a/RunTarget* "jar/dist"
    (a/AntJar
      {:destFile (fp! (ge :distDir)
                        (str "skaro-rt-" (ge :buildVersion) ".jar"))}
      [[:fileset {:dir (ge :buildDir) }
                 [[:exclude "**/log4j.properties"]
                  [:exclude "**/logback.xml"]
                  [:exclude "js/**"]
                  [:exclude "demo/**"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  task defs below !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask prebuild

  "prepare build environment"
  []

  (bc/with-pre-wrap fileset
    ((comp preBuild clean4Build))
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testclj

  "test clj frwk"
  []

  (bc/with-pre-wrap fileset
    (buildCljTest)
    (runCljTest)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testjava

  "test java frwk"
  []

  (bc/with-pre-wrap fileset
    (buildJavaTest)
    (runJavaTest)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juber

  "my own uber"
  []

  (bc/with-pre-wrap fileset

    (let [from (io/file (ge :basedir)
                        (ge :target-path))
          jars (output-files fileset)
          to (io/file (ge :libDir))]
      (a/RunTarget
        "libjars"
        (for [j (seq jars)]
          (a/AntCopy {:file (fp! (:dir j) (:path j))
                        :todir to})))
      (format "copied (%d) jars to %s" (count jars) to))

    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask libjars

  "copy all dependencies (jars) to libdir"
  []

  (comp (uber) (juber)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask javacmp

  "compile java files"
  []

  (bc/with-pre-wrap fileset
    (compileFrwk)
    (compileWFlow)
    (compileSkaro)
    (compileJavaDemo)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask cljcmp

  "compile clojure files"
  []

  (bc/with-pre-wrap fileset
    (cljTpcl)
    (cljXLib)
    (tardisAll)
    (cljDemo)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask babel

  "run babel on javascripts"
  []

  (bc/with-pre-wrap fileset
    (babel!)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask jarfiles

  "jar all classes"
  []

  (bc/with-pre-wrap fileset
    (jar!)
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev
  "dev-mode"
  []

  (comp (prebuild)
        (libjars)
        (javacmp)
        (cljcmp)
        (babel)
        (jarfiles)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask pack
  "bundle-project"
  []

  (bc/with-pre-wrap fileset
    (distroInit)
    (packRes)
    (packSrc)
    (packBin)
    (packDist)
    (packLibs)
    (packDocs)
    (packLics)
    (packAll)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask release
  ""
  []
  (comp (dev) (pack)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask poo
  ""
  []
  (a/RunTasks*
    (a/AntDelete
      {}
      [[:fileset {:dir "/tmp/public"
                  :includes "pages/**,ig/**"}]])))

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
