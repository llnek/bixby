;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/bixby "2.2.0"

  :license {:url "https://www.apache.org/licenses/LICENSE-2.0.txt"
            :name "Apache License"}

  :description "Service bus, web framework."
  :url "https://github.com/llnek/bixby"

  :dependencies [[org.apache.commons/commons-text "1.12.0"]
                 [commons-io/commons-io "2.17.0"]
                 ;;shiro needs this
                 [commons-logging/commons-logging "1.3.4"]
                 [io.czlab/twisty "2.2.0"]
                 [io.czlab/flux "2.2.0"]
                 [io.czlab/nettio "2.2.0"]
                 [io.czlab/hoard "2.2.0"]
                 [io.czlab/basal "2.2.0"]
                 [io.czlab/cljant "2.2.0"]
                 [commons-net/commons-net "3.11.1"]
                 [org.apache.shiro/shiro-core "2.0.1"]
                 [org.freemarker/freemarker "2.3.33"]]

  :XXXexclusions [org.clojure/clojure]

  :plugins [[cider/cider-nrepl "0.50.2" :exclusions [nrepl/nrepl]]
            [lein-codox "0.10.8"]
            [lein-cljsbuild "1.1.8"]]

  :profiles {:provided {:dependencies [[org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1"]
                                       [com.sun.mail/javax.mail "1.6.2"]
                                       [org.clojure/clojure "1.12.0"]]}
             :uberjar {:aot :all}}

  :aliases {"bixby" ["trampoline" "run" "-m" "czlab.bixby.cons.con4"]
            "bixby-run" ["trampoline" "run" "-m" "czlab.bixby.exec"]
            "bixby-console" ["trampoline" "run" "-m" "czlab.bixby.cons.con7"]}

  :global-vars {*warn-on-reflection* true}
  :preserve-eval-meta true
  :target-path "out/%s"
  :aot :all

  :coordinate! "czlab"
  :omit-source true

  :java-source-paths ["src/main/java" "src/test/java"]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :resource-paths ["src/main/resources"]

  ;:main czlab.bixby.cons.con7
  :main czlab.bixby.exec

  :jvm-opts ["-Dlog4j.configurationFile=file:attic/log4j2.xml" "-Dbixby.kill.port=4444"]
  :javac-options ["-source" "16"
                  "-target" "22"
                  "-Xlint:unchecked" "-Xlint:-options" "-Xlint:deprecation"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

