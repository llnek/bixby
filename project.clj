;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defproject io.czlab/wabbit "1.0.0"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :description ""
  :url "https://github.com/llnek/wabbit"

  :dependencies
  [[com.yahoo.platform.yui/yuicompressor "2.4.8"  :exclusions [rhino]]
   [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1"]
   ;;[com.github.spullara.mustache.java/compiler "0.9.4" ]
   ;;[com.google.code.findbugs/jsr305 "3.0.1"]
   [org.freemarker/freemarker "2.3.25-incubating"]
   [io.czlab/proto "1.0.0" :scope "test"]
   [io.czlab/wabbit-cons "1.0.0"]
   [io.czlab/twisty "1.0.0"]
   [io.czlab/convoy "1.0.0"]
   [io.czlab/horde "1.0.0"]
   [io.czlab/flux "1.0.0"]
   ;;[org.clojure/math.numeric-tower "0.0.4"]
   ;;[org.clojure/math.combinatorics "0.1.3"]
   ;;[org.clojure/tools.nrepl "0.2.12"]
   [org.clojure/tools.reader "0.10.0"]
   ;;[org.clojure/data.codec "0.1.0"]
   ;;[org.clojure/data.csv "0.1.3"]
   ;;[org.clojure/java.jdbc "0.6.1"]
   ;;[org.clojure/java.data "0.1.1"]
   ;;[org.clojure/java.jmx "0.3.3"]
   ;;[org.clojure/data.xml "0.0.8"]
   ;;[org.clojure/core.cache "0.6.5"]
   ;;[org.clojure/core.match "0.2.2"]
   ;;[org.clojure/core.memoize "0.5.9"]
   ;;[org.clojure/tools.analyzer.jvm "0.6.10"]
   ;;[org.clojure/tools.analyzer "0.6.9"]
   ;;[org.clojure/tools.cli "0.3.5"]
   ;;[org.clojure/data.generators "0.1.2"]
   [org.clojure/data.priority-map "0.0.7"]
   [org.clojure/core.async "0.2.395"]
   ;;[org.clojure/core.logic "0.8.11"]
   ;;[org.clojure/algo.monads "0.1.6"]
   ;;[org.clojure/algo.generic "0.1.2"]
   [org.apache.shiro/shiro-core "1.3.2"]
   [org.mozilla/rhino "1.7.7.1" ]
   [jline/jline "2.14.2" ]
   [com.sun.tools/tools "1.8.0"]]

  :plugins [[lein-codox "0.10.2"]
            [lein-czlab "1.0.0"]
            [lein-pprint "1.1.2"]]
  :hooks [leiningen.lein-czlab]

  :profiles {:provided {:dependencies
                        [[org.clojure/clojurescript "1.9.293" :scope "provided"]
                         [org.clojure/clojure "1.8.0" :scope "provided"]
                         [net.mikera/cljunit "0.6.0" :scope "test"]
                         [junit/junit "4.12" :scope "test"]
                         [codox/codox "0.10.2" :scope "provided"]]}
             :uberjar {:aot :all}}

  :global-vars {*warn-on-reflection* true}
  :target-path "out/%s"
  :aot :all

  ;;:jar-exclusions [#"(?:^|/).svn/"]
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


