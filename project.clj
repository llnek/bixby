;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/blutbad "1.1.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :description "Service bus, web framework."
  :url "https://github.com/llnek/blutbad"

  :dependencies [;[org.apache.commons/commons-lang3 "3.9"]
                 [org.apache.commons/commons-text "1.8"]
                 [commons-io/commons-io "2.6"]
                 [io.aviso/pretty "0.1.37"]
                 [stencil "0.5.0"]
                 ;;shiro needs this
                 [commons-logging "1.2"]
                 [io.czlab/twisty "1.1.0"]
                 [io.czlab/flux "1.1.0"]
                 [io.czlab/nettio "1.2.0"]
                 [io.czlab/hoard "1.1.0"]
                 [io.czlab/basal "1.1.0"]
                 [commons-net/commons-net "3.6"]
                 [org.apache.shiro/shiro-core "1.4.2"]
                 ;[org.freemarker/freemarker "2.3.29"]
                 [io.czlab/antclj "1.0.4"]
                 [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1"]]

  :exclusions [org.clojure/clojure]

  :plugins [[blutbad/lein-template "1.1.0"]
            [lein-codox "0.10.7"]
            [cider/cider-nrepl "0.22.4"]]

  :profiles {:provided {:dependencies
                        [[org.clojure/clojure "1.10.1" :scope "provided"]]}
             :uberjar {:aot :all}}

  :global-vars {*warn-on-reflection* true}
  :target-path "out/%s"
  :aot :all

  :coordinate! "czlab"
  :omit-source true

  :java-source-paths ["src/main/java" "src/test/java"]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :resource-paths ["src/main/resources"]

  ;:main czlab.blutbad.cons.con7
  :main czlab.blutbad.exec

  :jvm-opts ["-Dlog4j.configurationFile=file:attic/log4j2.xml"]
  :javac-options [;"-source" "8"
                  "-Xlint:unchecked" "-Xlint:-options" "-Xlint:deprecation"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

