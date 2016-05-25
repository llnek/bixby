(set-env!
  :dependencies '[

    [com.yahoo.platform.yui/yuicompressor "2.4.8"  :exclusions [rhino]]

    [org.apache.ant/ant-apache-log4j "1.9.7" :exclusions [log4j]]
    [ant-contrib/ant-contrib "1.0b3" :exclusions [ant]]
    ;;[org.apache.ivy/ivy "2.4.0" ]
    [org.apache.ant/ant "1.9.7" ]
    [org.apache.ant/ant-launcher "1.9.7" ]
    [org.apache.ant/ant-junit4 "1.9.7" ]
    [org.apache.ant/ant-junit "1.9.7" ]

    [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1" ]
    [com.google.code.findbugs/jsr305 "3.0.1" ]
    [org.javassist/javassist "3.20.0-GA"  ]

    [com.github.spullara.mustache.java/compiler "0.9.1" ]
    [org.freemarker/freemarker "2.3.23" ]

    [org.clojure/clojure "1.8.0" ]
    [org.clojure/clojurescript "1.8.51" ]

    [org.clojure/math.numeric-tower "0.0.4" ]
    [org.clojure/math.combinatorics "0.1.1" ]
    [org.clojure/tools.logging "0.3.1" ]
    [org.clojure/tools.nrepl "0.2.12" ]
    [org.clojure/tools.reader "0.10.0" ]
    [org.clojure/data.codec "0.1.0" ]
    [org.clojure/data.csv "0.1.3" ]
    [org.clojure/java.jdbc "0.5.8" ]
    [org.clojure/java.data "0.1.1" ]
    [org.clojure/java.jmx "0.3.1" ]
    [org.clojure/data.json "0.2.6" ]
    [org.clojure/data.xml "0.0.8" ]
    [org.clojure/core.cache "0.6.5" ]
    [org.clojure/core.match "0.2.2" ]
    [org.clojure/core.memoize "0.5.9" ]
    [org.clojure/tools.analyzer.jvm "0.6.9"]
    [org.clojure/tools.analyzer "0.6.7"]
    [org.clojure/tools.cli "0.3.3" ]
    [org.clojure/data.generators "0.1.2" ]
    [org.clojure/data.priority-map "0.0.7" ]
    [org.clojure/core.async "0.2.374" ]
    [org.clojure/core.logic "0.8.10" ]
    [org.clojure/algo.monads "0.1.5" ]
    [org.clojure/algo.generic "0.1.2" ]
    [org.clojure/core.memoize "0.5.9" ]
    [org.flatland/ordered "1.5.3"]
    [com.cemerick/pomegranate "0.3.1"]

    [org.apache.shiro/shiro-core "1.2.4" ]
    [org.mozilla/rhino "1.7.7.1" ]
    [jline/jline "2.14.1" ]
    [com.sun.tools/tools "1.8.0"  ]

    [czlab/czlab-webnet "1.0.0"]
    [czlab/czlab-wflow "1.0.0"]
    [czlab/czlab-dbio "1.0.0"]
  ]

  :source-paths #{"src/main/clojure" "src/main/java"}
  :test-runner "czlabtest.skaro.ClojureJUnit"
  :version "1.0.0"
  :debug true
  :project 'czlab/czlab-skaro)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(require '[czlab.tpcl.boot :as b :refer [fp! ge testjava testclj]]
         '[clojure.tools.logging :as log]
         '[clojure.java.io :as io]
         '[clojure.string :as cs]
         '[czlab.tpcl.antlib :as a]
         '[boot.core :as bc])

(import '[org.apache.tools.ant Project Target Task]
        '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/bootEnv!
  {:packDir #(set-env! %2 (ge :homedir ".skaro"))

   :CPATH [[:location (fp! (ge :basedir) "artifacts")]
           [:location (ge :jzzDir)]
           [:location (ge :czzDir)]
           [:fileset {:dir (ge :libDir)
                      :includes "*.jar"}]]
                  })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babel->cb ""

  [f & {:keys [postgen dir paths]
        :or {:postgen false
             :dir false
             :paths []}
        :as args }]

  (let [out (io/file (ge :wzzDir) "js")
        dir (io/file (ge :srcDir) "js")
        mid (cs/join "/" paths)
        bd (ge :bld)
        des (-> (io/file out mid)
                (.getParentFile)) ]
    (cond
      (true? postgen)
      (let [bf (io/file dir bd mid)]
        (b/ReplaceFile bf
                       #(-> (cs/replace % "/*@@" "")
                            (cs/replace "@@*/" "")))
        (a/MoveFile bf des))

      (.isDirectory f)
      (if (= bd (.getName f)) nil {})

      :else
      (if-not (.endsWith mid ".js")
        (do
          (a/CopyFile (io/file dir mid) des)
          nil)
        {:work-dir dir
         :args ["--modules" "amd"
                "--presets" "es2015"
                "--module-ids" mid "--out-dir" bd] }))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- babel! ""

  [& args]

  (let [root (io/file (ge :srcDir) "js")
        ljs (io/file root (ge :bld)) ]
    (a/CleanDir ljs)
    (try
      (b/BabelTree root babel->cb)
      (finally
        (a/DeleteDir ljs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileFrwk ""

  []

  (a/CleanDir (fp! (ge :jzzDir) "com/zotohlab/frwk"))
  (a/CleanDir (fp! (ge :jzzDir) "org"))
  (let [t1 (a/AntJavac
              (ge :JAVAC_OPTS)
              [[:include "com/zotohlab/frwk/**/*.java"]
               [:include "org/**/*.java"]
               [:classpath (ge :CPATH)]
               [:compilerarg (ge :COMPILER_ARGS) ]])
        t2 (a/AntCopy
              {:todir (fp! (ge :jzzDir) "com/zotohlab/frwk")}
              [[:fileset
                {:dir (fp! (ge :srcDir)
                           "java/com/zotohlab/frwk")
                 :excludes "**/*.java"}]])
        t3 (a/AntJar
              {:destFile (fp! (ge :distDir)
                              (str "frwk-"
                                   (ge :buildVersion) ".jar"))}
              [[:fileset
                {:dir (ge :jzzDir)
                 :includes "com/zotohlab/frwk/**"
                 :excludes (cs/join ","
                                    ["**/log4j.properties"
                                     "**/logback.xml"
                                     "demo/**"])}]]) ]
    (->> [t1 t2]
         (a/RunTarget "compile/frwk"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileWFlow ""

  []

  (a/CleanDir (fp! (ge :jzzDir) "com/zotohlab/server"))
  (a/CleanDir (fp! (ge :jzzDir) "com/zotohlab/wflow"))
  (let [t1 (a/AntJavac
              (ge :JAVAC_OPTS)
              [[:include "com/zotohlab/wflow/**/*.java"]
               [:include "com/zotohlab/server/**/*.java"]
               [:classpath (ge :CPATH) ]
               [:compilerarg (ge :COMPILER_ARGS) ]])
        t2 (a/AntCopy
              {:todir (fp! (ge :jzzDir) "com/zotohlab/wflow")}
              [[:fileset {:dir (fp! (ge :srcDir)
                                    "java/com/zotohlab/server")
                          :excludes "**/*.java"}]
               [:fileset {:dir (fp! (ge :srcDir)
                                    "java/com/zotohlab/wflow")
                          :excludes "**/*.java"}]])
        t3 (a/AntJar
              {:destFile (fp! (ge :distDir)
                              (str "wflow-"
                                   (ge :buildVersion) ".jar"))}
              [[:fileset
                {:dir (ge :jzzDir)
                 :includes (cs/join ","
                                    ["com/zotohlab/server/**"
                                     "com/zotohlab/wflow/**"])
                 :excludes (cs/join ","
                                    ["**/log4j.properties"
                                     "**/logback.xml"
                                     "demo/**"])}]]) ]
    (->> [t1 t2]
         (a/RunTarget "compile/wflow"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileSkaro ""

  []

  (a/CleanDir (fp! (ge :jzzDir) "com/zotohlab/skaro"))
  (a/CleanDir (fp! (ge :jzzDir) "com/zotohlab/mock"))
  (a/CleanDir (fp! (ge :jzzDir) "com/zotohlab/tpcl"))
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
                {:todir (fp! (ge :jzzDir) "com/zotohlab" d)}
                [[:fileset
                  {:dir (fp! (ge :srcDir) "java/com/zotohlab" d)
                   :excludes "**/*.java"}]]))
            ["skaro" "mock" "tpcl"])
        tr (a/AntCopy
              {:todir (ge :jzzDir)}
              [[:fileset {:dir (fp! (ge :srcDir) "resources")}]])
        ts (conj (into [] (cons t1 m)) tr)
        t2 (a/AntJar
             {:destFile (fp! (ge :distDir)
                             (str "skaro-ld-"
                                  (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :jzzDir)
                         :includes "com/zotohlab/skaro/loaders/**"}]])
        t3 (a/AntJar
             {:destFile (fp! (ge :distDir)
                             (str "skaro-rt-"
                                  (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :jzzDir)
                         :includes (str "com/zotohlab/skaro/**,"
                                        "com/zotohlab/mock/**,"
                                        "com/zotohlab/tpcl/**")
                         :excludes (str "**/log4j.properties,"
                                        "**/logback.xml,"
                                        "demo/**")}]]) ]
    (->> ts
         (a/RunTarget "compile/skaro"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- compileJavaDemo ""

  []

  (let [dirs ["splits" "flows"]]
    (doall (map #(a/CleanDir (fp! (ge :jzzDir) "demo" %)) dirs))
    (let [t1 (a/AntJavac
               (ge :JAVAC_OPTS)
               [[:include "demo/**/*.java"]
                [:classpath (ge :CPATH) ]
                [:compilerarg (ge :COMPILER_ARGS) ]])
          m (map
              (fn [d]
                (a/AntCopy
                  {:todir (fp! (ge :jzzDir) "demo" d)}
                  [[:fileset {:dir (fp! (ge :srcDir) "java/demo" d)
                              :excludes "**/*.java"}]]))
              dirs) ]
      (a/RunTarget "compile/demo" (cons t1 m)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljJMX ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/jmx"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/xlib/jmx")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljCrypto ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/crypto"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/xlib/crypto")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDbio ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/dbio"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/xlib/dbio")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljNet ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/netty"))
  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/net"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/xlib/netty"
                                       "czlab/xlib/net")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljUtil ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/util"))
  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib/i18n"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/xlib/util"
                                       "czlab/xlib/i18n")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljXLib ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/xlib"))
  (let [m (map
            #(apply % [])
            [ #'cljUtil #'cljCrypto #'cljDbio #'cljNet #'cljJMX ])
        t2 (a/AntCopy
             {:todir (fp! (ge :czzDir) "czlab/xlib")}
             [[:fileset {:dir (fp! (ge :srcDir) "clojure/czlab/xlib")
                         :excludes "**/*.clj"}]])
        t3 (a/AntJar
             {:destFile (fp! (ge :distDir)
                             (str "xlib-" (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :czzDir)
                         :includes "czlab/xlib/**"
                         :excludes (str "**/log4j.properties,"
                                        "**/logback.xml,"
                                        "demo/**")}]]) ]
    (->> (concat m [t2])
         (a/RunTarget "clj/xlib"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljTpcl ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/tpcl"))
  (let [t1 (a/AntJava
              (ge :CLJC_OPTS)
              (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                                 "czlab/tpcl")]]
                      (ge :CJNESTED_RAW)))
        t2 (a/AntCopy
              {:todir (fp! (ge :czzDir) "czlab/tpcl")}
              [[:fileset {:dir (fp! (ge :srcDir) "clojure/czlab/tpcl")
                          :excludes "**/*.clj"}]])
        t3 (a/AntJar
              {:destFile (fp! (ge :distDir)
                              (str "tpcl-" (ge :buildVersion) ".jar"))}
              [[:fileset {:dir (ge :czzDir)
                          :includes "czlab/tpcl/**"
                          :excludes (str "**/log4j.properties,"
                                         "**/logback.xml,"
                                         "demo/**")}]]) ]
    (->> [t1 t2]
         (a/RunTarget "clj/tpcl"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cljDemo ""

  []

  (let [dirs (b/CollectCljPaths (io/file (ge :srcDir) "clojure"))
        dirs (filter #(.startsWith % "demo/") dirs) ]
    (a/CleanDir (fp! (ge :czzDir) "demo"))
    (a/RunTarget* "clj/demo"
      (a/AntJava
        (ge :CLJC_OPTS)
        (concat [[:argvalues (apply b/FmtCljNsps
                                    (fp! (ge :srcDir) "clojure")
                                    dirs)]]
                (ge :CJNESTED)))
      (a/AntCopy
        {:todir (fp! (ge :czzDir) "demo")}
        [[:fileset {:dir (fp! (ge :srcDir) "clojure/demo")
                    :excludes "**/*.clj"}]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroMain ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/skaro/impl"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/skaro/impl")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroMvc ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/skaro/mvc"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/skaro/mvc")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroAuth ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/skaro/auth"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/skaro/auth")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroIO ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/skaro/io"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/skaro/io")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroEtc ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/skaro/etc"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/skaro/etc")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroCore ""

  []

  (a/CleanDir (fp! (ge :czzDir) "czlab/skaro/core"))
  (a/AntJava
    (ge :CLJC_OPTS)
    (concat [[:argvalues (b/FmtCljNsps (fp! (ge :srcDir) "clojure")
                                       "czlab/skaro/core")]]
            (ge :CJNESTED))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- skaroAll ""

  []

  (let [m (map #(apply % [])
                [#'skaroCore #'skaroIO
                 #'skaroEtc #'skaroAuth
                 #'skaroMvc #'skaroMain ])
        t2 (a/AntCopy
             {:todir (fp! (ge :czzDir) "czlab/skaro")}
             [[:fileset {:dir (fp! (ge :srcDir) "clojure/czlab/skaro")
                         :excludes "**/*.edn,**/*.clj"}]])
        ts (into [] (concat m [t2]))
        t3 (a/AntJar
             {:destFile (fp! (ge :distDir)
                             (str "skaro-" (ge :buildVersion) ".jar"))}
             [[:fileset {:dir (ge :czzDir)
                         :includes "czlab/skaro/**"}]]) ]
    (->> ts
         (a/RunTarget "clj/skaro"))))

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
        [[:fileset {:dir (fp! (ge :basedir) "etc/conf")} ]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copyJsFiles ""

  []

  (a/AntCopy
    {:todir (fp! (ge :packDir) "src/main/js")}
    [[:fileset {:dir (fp! (ge :wzzDir) "js")
                :includes "**/*.js"}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""

  []

  (a/RunTarget*
    "pack/res"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "etc/ems")
       :flatten true}
      [[:fileset {:dir (fp! (ge :srcDir) "clojure")
                  :includes "**/io.edn"}]])
    (a/AntCopy
      {:todir (fp! (ge :packDir) "etc")}
      [[:fileset {:dir (fp! (ge :basedir) "etc")} ]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDocs ""

  []

  (a/CleanDir (fp! (ge :packDir) "docs" "jsdoc"))
  (a/CleanDir (fp! (ge :packDir) "docs" "api"))

  (a/RunTarget*
    "pack/docs"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "docs")}
      [[:fileset {:dir (fp! (ge :basedir) "docs")
                  :excludes "dummy.txt"}]])
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
       [[:fileset {:dir (fp! (ge :srcDir) "java")
                   :excludes "demo/**"
                   :includes "**/*.java"}]
        [:classpath (ge :CPATH) ]])

    (a/AntJava
      (ge :CLJC_OPTS)
      (concat [[:argvalues ["czlab.tpcl.codox"]]]
              (ge :CJNESTED_RAW)))

    (a/AntJava
      {:classname "czlab.tpcl.codox"
       :fork true
       :failonerror true}
      [[:argvalues [(ge :basedir)
                    (fp! (ge :srcDir) "clojure")
                    (fp! (ge :packDir) "docs/api")]]
       [:classpath (ge :CJPATH) ]])

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
      [[:fileset {:dir (ge :basedir)
                  :includes "*.html,*.txt,LICENSE"}]])
    (a/AntCopy
      {:todir (ge :packDir) :flatten true}
      [[:fileset {:dir (ge :basedir)
                  :includes "README.md,pom.xml"}]]))
  (b/ReplaceFile (fp! (ge :packDir) "pom.xml")
                 #(cs/replace % "@@VERSION@@" (ge :buildVersion))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packDist ""

  []

  (a/RunTarget*
    "pack/dist"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "dist")}
      [[:fileset {:dir (ge :distDir)
                  :includes "**/skaro*.jar"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packLibs ""

  []

  (a/RunTarget*
    "pack/lib"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "lib")}
      [[:fileset {:dir (ge :libDir)} ]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""

  []

  (a/RunTarget*
    "pack/bin"
    (a/AntCopy
      {:todir (fp! (ge :packDir) "bin")}
      [[:fileset {:dir (fp! (ge :basedir) "bin")} ]])

    (a/AntChmod
      {:dir (fp! (ge :packDir) "bin")
       :perm "755"
       :includes "*"} )))

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
      [[:tarfileset {:dir (ge :packDir)
                     :excludes "apps/**,bin/**"}]
       [:tarfileset {:dir (ge :packDir)
                     :mode "755"
                     :includes "bin/**"}]])))

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
    ((comp b/PreBuild
           (fn [& args]
             (a/CleanDir (ge :packDir)))
           b/Clean4Build))
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask javacmp

  "compile java files"
  []

  (bc/with-pre-wrap fileset
    (b/CompileJava)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask cljcmp

  "compile clojure files"
  []

  (bc/with-pre-wrap fileset
    (cljTpcl)
    (cljXLib)
    (skaroAll)
    (cljDemo)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask babel

  "run babel on javascripts"
  []

  (bc/with-pre-wrap fileset
    (babel!)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask jar!

  "jar all classes"
  []

  (bc/with-pre-wrap fileset
    (b/ReplaceFile (fp! (ge :jzzDir) "com/zotohlab/skaro/version.properties")
                   #(cs/replace % "@@pom.version@@" (ge :buildVersion)))
    (b/JarFiles)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev
  "dev-mode"
  []

  (comp (prebuild)
        (b/libjars)
        (javacmp)
        (cljcmp)
        (babel)
        (jar!)))

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
    ;;(packDocs)
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
;;EOF
