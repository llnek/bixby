;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/wabbit "1.0.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :description "Service bus, web framework."
  :url "https://github.com/llnek/wabbit"

  :dependencies [[org.apache.commons/commons-lang3 "3.5"]
                 [commons-io/commons-io "2.5"]
                 [io.aviso/pretty "0.1.33"]
                 ;;shiro needs this
                 [commons-logging "1.2"]
                 [io.czlab/twisty "1.0.0"]
                 [io.czlab/convoy "1.0.0"]
                 [io.czlab/horde "1.0.0"]]

  :plugins [[cider/cider-nrepl "0.14.0"]
            [lein-javadoc "0.3.0"]
            [lein-codox "0.10.3"]
            [lein-cprint "1.2.0"]]

  :profiles {:provided {:dependencies
                        [[org.clojure/clojure "1.8.0" :scope "provided"]]}
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


  :jvm-opts ["-Dlog4j.configurationFile=file:attic/log4j2.xml"]
  :javac-options ["-source" "8"
                  "-Xlint:unchecked" "-Xlint:-options" "-Xlint:deprecation"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

