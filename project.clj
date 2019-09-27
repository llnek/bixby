;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/wabbit "1.1.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :description "Service bus, web framework."
  :url "https://github.com/llnek/wabbit"

  :dependencies [[org.apache.commons/commons-lang3 "3.9"]
                 [commons-io/commons-io "2.6"]
                 [io.aviso/pretty "0.1.37"]
                 [stencil "0.5.0"]
                 ;;shiro needs this
                 [commons-logging "1.2"]
                 [io.czlab/twisty "1.1.0"]
                 [io.czlab/flux "1.1.0"]
                 [io.czlab/niou "1.1.0"]
                 [io.czlab/nettio "1.2.0"]
                 [io.czlab/hoard "1.1.0"]
                 [io.czlab/basal "1.1.0"]
                 [commons-net/commons-net "3.6"]
                 [org.apache.shiro/shiro-core "1.4.1"]
                 [org.freemarker/freemarker "2.3.29"]
                 [io.czlab/wabbit-shared "1.1.0"]
                 [io.czlab/antclj "1.0.4"]
                 [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1"]]

  :exclusions [org.clojure/clojure]

  :plugins [;[cider/cider-nrepl "0.14.0"]
            [lein-czlab "1.0.0"]
            [lein-javadoc "0.3.0"]
            [lein-codox "0.10.3"]
            [lein-cprint "1.2.0"]]

  :profiles {:provided {:dependencies
                        [[org.clojure/clojure "1.10.1" :scope "provided"]]}
             :uberjar {:aot :all}}

  :javadoc-opts {:package-names ["czlab.wabbit"]
                 :output-dir "docs"}

  :global-vars {*warn-on-reflection* true}
  :target-path "out/%s"
  :aot :all

  :coordinate! "czlab"
  :omit-source true

  :java-source-paths ["src/main/java" "src/test/java"]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :resource-paths ["src/main/resources"]

  :main czlab.wabbit.cons.con7
  ;:main czlab.wabbit.exec

  :jvm-opts ["-Dlog4j.configurationFile=file:attic/log4j2.xml"]
  :javac-options [;"-source" "8"
                  "-Xlint:unchecked" "-Xlint:-options" "-Xlint:deprecation"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

