(defproject skaro "1.0.0-SNAPSHOT"

  :description "A sample project"
  :url "http://example.org/sample-clojure-project"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :min-lein-version "2.0.0"
  :eval-in :leiningen

  :plugins [[lein-localrepo "0.5.3"]
            [lein-depgraph "0.1.0"]
            ]

  :dependencies [
    [bouncycastle/bcprov-jdk15on "152"]
    [bouncycastle/bcmail-jdk15on "152"]
    [bouncycastle/bcpkix-jdk15on "152"]
    [org.jasypt/jasypt "1.9.2"]
    [org.mindrot/jbcrypt "0.3m"]

    [org.slf4j/slf4j-api "1.7.7"]
    [log4j/log4j "1.2.17"]

    [ch.qos.logback/logback-classic "1.1.2"]
    [ch.qos.logback/logback-core "1.1.2"]

    [net.sourceforge.jregex/jregex "1.2_01"]
    [net.sf.jopt-simple/jopt-simple "4.6"]
    [com.google.guava/guava "18.0"]
    [com.google.code.findbugs/jsr305 "2.0.3"]
    [joda-time/joda-time "2.7"]
    [org.zeroturnaround/zt-exec "1.6"]
    [org.zeroturnaround/zt-zip "1.7"]
    [org.apache.axis/axis "1.4"]
    [org.apache.axis/axis-jaxrpc "1.4"]

    [org.jdom/jdom2 "2.0.6"]

    [com.google.code.gson/gson "2.2.4"]

    [org.apache.commons/commons-compress "1.8.1"]
    [org.apache.commons/commons-lang3 "3.3.2"]
    [commons-net/commons-net "3.3"]
    [commons-io/commons-io "2.4"]

    [commons-logging/commons-logging "1.2"]
    [org.apache.commons/commons-email "1.3.3"]
    [commons-codec/commons-codec "1.10"]
    [commons-fileupload/commons-fileupload "1.3.1"]
    [commons-dbutils/commons-dbutils "1.6"]
    [com.sun.mail/javax.mail "1.5.2"]

    [org.apache.ivy/ivy "2.4.0"]
    [org.apache.ant/ant "1.9.4"]
    [org.apache.ant/ant-launcher "1.9.4"]
    [org.apache.ant/ant-junit4 "1.9.4"]
    [org.apache.ant/ant-junit "1.9.4"]
    [org.apache.ant/ant-apache-log4j "1.9.4" :exclusions [ log4j] ]
    [ant-contrib/ant-contrib "1.0b3" :exclusions [ ant] ]
    [org.codehaus.gant/gant_groovy2.3 "1.9.11"]

    [com.jolbox/bonecp "0.8.0.RELEASE"]

    [org.apache.httpcomponents/httpcore-nio "4.4"]
    [org.apache.httpcomponents/httpcore "4.4"]
    [org.apache.httpcomponents/httpclient "4.4"]
    [io.netty/netty-all "4.0.26.Final"]

    [com.corundumstudio.socketio/netty-socketio "1.6.5"]

    [org.eclipse.jetty/jetty-xml "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-server "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-continuation "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-servlet "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-server "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-util "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-security "9.2.9.v20150224"]
    [org.eclipse.jetty/jetty-webapp "9.2.9.v20150224"]
    [org.eclipse.jetty.websocket/websocket-api "9.2.9.v20150224"]
    [org.eclipse.jetty.websocket/websocket-common "9.2.9.v20150224"]
    [org.eclipse.jetty.websocket/websocket-servlet "9.2.9.v20150224"]
    [org.eclipse.jetty.websocket/websocket-client "9.2.9.v20150224"]
    [org.eclipse.jetty.websocket/websocket-server "9.2.9.v20150224"]

    [org.codehaus.groovy/groovy-all "2.4.0"]
    ;;[org.scala-lang/scala-library "2.11.1"]
    ;;[org.scala-lang/scala-compiler "2.11.1"]
    [com.sun.tools/tools "1.8.0"]
    [javassist/javassist "3.12.1.GA"]

    [com.github.spullara.mustache.java/compiler "0.8.15"]
    ;;[org.fusesource.scalate/scalate-core_2.10 "1.6.1"]
    [org.freemarker/freemarker "2.3.22"]

    [com.yahoo.platform.yui/yuicompressor "2.4.8" :exclusions [rhino] ]
    [org.apache.geronimo.specs/geronimo-jms_1.1_spec "1.1.1"]
    [com.h2database/h2 "1.3.176"]
    [org.postgresql/postgresql "9.4-1201-jdbc41"]
    [net.sf.ehcache/ehcache "2.8.3"]

    [org.clojure/math.numeric-tower "0.0.4"]
    [org.clojure/math.combinatorics "0.0.7"]
    [org.clojure/tools.logging "0.3.0"]
    [org.clojure/tools.nrepl "0.2.3"]
    [org.clojure/data.codec "0.1.0"]
    [org.clojure/java.jdbc "0.3.4"]
    [org.clojure/java.data "0.1.1"]
    [org.clojure/java.jmx "0.2.0"]
    [org.clojure/data.json "0.2.6"]
    [org.clojure/data.xml "0.0.7"]
    [org.clojure/core.cache "0.6.3"]
    [org.clojure/tools.cli "0.3.1"]
    [org.clojure/data.generators "0.1.2"]
    [org.clojure/core.async "0.1.303.0-886421-alpha"]
    ;;[potemkin/potemkin "0.3.4"]
    ;;[lamina/lamina "0.5.2"]
    ;;[aleph/aleph "0.3.2"]
    [org.clojure/clojure "1.6.0"]
    [org.clojure/clojurescript "0.0-3058"]

    [org.apache.shiro/shiro-core "1.2.3"]
    [org.mozilla/rhino "1.7R5"]
    [jline/jline "2.12"]

    ;;[org.scalatest/scalatest_2.11 "2.2.0"]
    [net.mikera/cljunit "0.3.0"]
    [junit/junit "4.12"]
    [com.googlecode.jslint4java/jslint4java "2.0.5"]

    ]

  :pedantic? :abort

  :mirrors {

    "ibiblio" {
      :url "http://mirrors.ibiblio.org/pub/mirrors/maven2"
    }

    "clojars" {
      :url "http://clojars.org/repo"
    }

    "sonatype-oss-public" {
      :url "https://oss.sonatype.org/content/groups/public"
    }

    "central" {
      :url "http://central.maven.org/maven2"
    }

    "maven-1" {
      :url "http://repo1.maven.org/maven2"
    }

    "mandubian-mvn" {
      :url "http://mandubian-mvn.googlecode.com/svn/trunk/mandubian-mvn/repository"
    }

  }

  ;; Override location of the local maven repository. Relative to project root.
  ;;local-repo "/wdrive/localrepo/repos"

  :update :always
  :checksum :fail
  :offline? false

  :profiles {:dev {:resource-paths ["dummy-data"]
                   :dependencies [[clj-stacktrace "0.2.4"]]}
             :debug {:debug true
                     :injections [(prn (into {} (System/getProperties)))]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :global-vars {*warn-on-reflection* true
                *assert* false}

  :jvm-opts ["-Xmx1g"]

  :source-paths [ "src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths [ "src/test/clojure"]

  :target-path "target/%s/"
  :compile-path "%s/classy-files"
  :native-path "%s/bits-n-stuff"
  :clean-targets [:target-path :compile-path ]

  :jar-name "sample.jar"
  :uberjar-name "sample-standalone.jar"
  :omit-source true
  :jar-exclusions [#"(?:^|/).svn/"]
  :uberjar-exclusions [#"META-INF/DUMMY.SF"]

  :deploy-branches ["master"])

