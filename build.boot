(set-env!
  :dependencies '[

    [com.yahoo.platform.yui/yuicompressor "2.4.8"  :exclusions [rhino]]
    [org.apache.commons/commons-lang3 "3.5"]
    [commons-io/commons-io "2.5"]
    [org.slf4j/slf4j-api "1.7.21" ]

    [czlab/czlab-twisty "1.0.0"]
    [czlab/czlab-convoy "1.0.0"]
    [czlab/czlab-horde "1.0.0"]
    [czlab/czlab-flux "1.0.0"]
    [czlab/czlab-xlib "1.0.0"]

    ;;[org.apache.ant/ant-apache-log4j "1.9.7" :exclusions [log4j]]
    [ant-contrib/ant-contrib "1.0b3" :exclusions [ant]]
    ;;[org.apache.ivy/ivy "2.4.0" ]
    [org.apache.ant/ant "1.9.7" ]
    [org.apache.ant/ant-launcher "1.9.7" ]
    [org.apache.ant/ant-junit4 "1.9.7" ]
    [org.apache.ant/ant-junit "1.9.7" ]

    [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1" ]
    [com.google.code.findbugs/jsr305 "3.0.1" ]
    [org.javassist/javassist "3.21.0-GA"  ]

    [com.github.spullara.mustache.java/compiler "0.9.4" ]
    [org.freemarker/freemarker "2.3.25-incubating" ]

    [org.clojure/clojure "1.8.0" ]
    [org.clojure/clojurescript "1.9.293" ]

    [org.clojure/math.numeric-tower "0.0.4" ]
    [org.clojure/math.combinatorics "0.1.3" ]
    [org.clojure/tools.logging "0.3.1" ]
    [org.clojure/tools.nrepl "0.2.12" ]
    [org.clojure/tools.reader "0.10.0" ]
    [org.clojure/data.codec "0.1.0" ]
    [org.clojure/data.csv "0.1.3" ]
    [org.clojure/java.jdbc "0.6.1" ]
    [org.clojure/java.data "0.1.1" ]
    [org.clojure/java.jmx "0.3.3" ]
    [org.clojure/data.json "0.2.6" ]
    [org.clojure/data.xml "0.0.8" ]
    [org.clojure/core.cache "0.6.5" ]
    [org.clojure/core.match "0.2.2" ]
    [org.clojure/core.memoize "0.5.9" ]
    [org.clojure/tools.analyzer.jvm "0.6.10"]
    [org.clojure/tools.analyzer "0.6.9"]
    [org.clojure/tools.cli "0.3.5" ]
    [org.clojure/data.generators "0.1.2" ]
    [org.clojure/data.priority-map "0.0.7" ]
    [org.clojure/core.async "0.2.395" ]
    [org.clojure/core.logic "0.8.11" ]
    [org.clojure/algo.monads "0.1.6" ]
    [org.clojure/algo.generic "0.1.2" ]

    [org.apache.shiro/shiro-core "1.3.2"]
    [org.mozilla/rhino "1.7.7.1" ]
    [jline/jline "2.14.2" ]
    [com.sun.tools/tools "1.8.0"  ]

    [com.cemerick/pomegranate "0.3.1" :scope "provided"]
    ;;[org.projectodd.shimdandy/shimdandy-impl "1.1.0"]
    ;;[org.projectodd.shimdandy/shimdandy-api "1.2.0"]
    [codox/codox "0.10.2" :scope "provided"]

    [net.mikera/cljunit "0.6.0" :scope "provided"]
    [junit/junit "4.12" :scope "provided"]

    ;; boot/clj stuff
    [boot/base "2.6.0" ]
    [boot/core "2.6.0" ]
    [boot/pod "2.6.0" ]
    [boot/worker "2.6.0" ]
    ;; this is causing the RELEASE_6 warning
    [boot/aether "2.6.0" ]

  ]

  :source-paths #{"src/main/clojure" "src/main/java"}
  :test-runner "czlabtest.wabbit.ClojureJUnit"

   :exclude-clj #"^czlab.wabbit.demo.*"
   ;;:exclude-java ""

  :version "1.0.0"
  :debug true
  :project 'czlab/czlab-wabbit)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(require '[czlab.tpcl.boot :as b :refer [fp! se! ge artifactID]]
         '[clojure.tools.logging :as log]
         '[clojure.java.io :as io]
         '[clojure.string :as cs]
         '[czlab.xlib.antlib :as a]
         '[boot.core :as bc])

(import '[org.apache.tools.ant Project Target Task]
        '[java.io File])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(b/bootEnv!
  {:packDir
   (fn [_] (fp! (ge :homedir) ".wabbit"))

   :mdirs
   (fn [_]
     (concat [(ge :wzzDir)]
             (ge :mdirs :local)))

   :CPATH
   (fn [_]
     (concat [[:location (fp! (ge :basedir)
                              "artifacts")]]
             (ge :CPATH :local)))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- distroInit
  ""
  [root]
  (let [basedir (ge :basedir)]
    (doseq [d ["conf" "dist" "bin"
               ["etc" "ems"] "lib" "docs" ]]
      (.mkdirs (if (vector? d)
                 (apply io/file root d)
                 (io/file root d))))
    (a/runTarget*
      "pack/init"
      (a/antCopy
        {:todir (fp! root "etc")}
        [[:fileset {:dir (fp! basedir "etc")} ]])
      (a/antCopy
        {:todir (fp! root "conf")}
        [[:fileset {:dir (fp! basedir "etc/conf")} ]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packRes ""

  []

  (a/runTarget*
    "pack/res"
    (a/antCopy
      {:todir (fp! (ge :packDir) "etc/ems")
       :flatten true}
      [[:fileset {:dir (fp! (ge :srcDir) "clojure")
                  :includes "**/io.edn"}]])
    (a/antCopy
      {:todir (fp! (ge :packDir) "etc")}
      [[:fileset {:dir (fp! (ge :basedir) "etc")} ]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packBin ""

  []

  (a/runTarget*
    "pack/bin"
    (a/antCopy
      {:todir (fp! (ge :packDir) "bin")}
      [[:fileset {:dir (fp! (ge :basedir) "bin")} ]])

    (a/antChmod
      {:dir (fp! (ge :packDir) "bin")
       :perm "755"
       :includes "*"} )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- packAll*
  ""
  []
  (b/packAll distroInit)
  (packRes)
  (packBin))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  task defs below !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask qikPack

  "private"
  []

  (bc/with-pre-wrap fileset
    (packAll*)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask preJar

  "private"
  []

  (bc/with-pre-wrap fileset
    (->> (fp! (ge :czzDir)
              "czlab/wabbit/demo")
         (a/deleteDir))
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask xxx

  "for dev only"
  []

  (comp (b/initBuild)
        (b/libjars)
        (b/buildr)
        (b/pom!)
        (preJar)
        (b/jar!)
        (qikPack)
        (b/localInstall)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev

  "for dev only"
  []

  (comp (b/initBuild)
        (b/libjars)
        (b/buildr)
        (b/pom!)
        (preJar)
        (b/jar!)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask tst

  "test all"
  []

  (comp (b/testJava)
        (b/testClj)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

(deftask packDistro

  ""
  []

  (bc/with-pre-wrap fileset

    (let []
      (packAll*)
      (if (ge :wantDocs) (b/genDocs))
      (b/tarAll)
      nil)

    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask rel

  ""
  [d doco bool "Generate doc"]

  (se! :exclude-clj 0)
  (b/toggleDoco doco)
  (comp (dev)
        (b/localInstall)
        (packDistro)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask run

  "for dev only"
  []

  (a/runTasks*
    (a/antJava
      {:classname "czlab.wabbit.etc.shell"
       :fork true
       :failonerror true
       :maxmemory "2048m"}
      [[:classpath (ge :TJPATH)]
       [:argvalues ""]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF



