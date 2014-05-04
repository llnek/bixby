(defproject skaro "1.0.0-SNAPSHOT"

  :description "A sample project"
  :url "http://example.org/sample-clojure-project"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :min-lein-version "2.0.0"

  :plugins [[lein-localrepo "0.5.3"]]

  :dependencies [
    ;;[bouncycastle/bcprov-jdk15on "150"]
    ;;[bouncycastle/bcmail-jdk15on "150"]
    ;;[bouncycastle/bcpkix-jdk15on "150"]
    [org.jasypt/jasypt "1.9.2"]
    [org.mindrot/jbcrypt "0.3m"]

    [org.slf4j/slf4j-api "1.7.7"]
    [log4j/log4j "1.2.17"]

    [ch.qos.logback/logback-classic "1.1.2"]
    [ch.qos.logback/logback-core "1.1.2"]

    [net.sourceforge.jregex/jregex "1.2_01"]
    [net.sf.jopt-simple/jopt-simple "4.6"]
    [com.google.guava/guava "16.0.1"]
    [com.google.code.findbugs/jsr305 "2.0.3"]
    [joda-time/joda-time "2.3"]
    [org.zeroturnaround/zt-exec "1.6"]
    [org.zeroturnaround/zt-zip "1.7"]
    [org.apache.axis/axis "1.4"]
    [org.apache.axis/axis-jaxrpc "1.4"]

    [org.jdom/jdom2 "2.0.5"]

    [com.google.code.gson/gson "2.2.4"]

    [org.apache.commons/commons-lang3 "3.3"]
    [commons-net/commons-net "3.3"]
    [commons-io/commons-io "2.4"]

    [commons-logging/commons-logging "1.1.3"]
    ;;[commons-email/commons-email "1.3.2"]
    [commons-codec/commons-codec "1.9"]
    [commons-fileupload/commons-fileupload "1.3.1"]
    [commons-dbutils/commons-dbutils "1.5"]
    [com.sun.mail/javax.mail "1.5.1"]

    [org.apache.ivy/ivy "2.3.0"]
    [org.apache.ant/ant "1.9.3"]
    [org.apache.ant/ant-launcher "1.9.3"]
    [org.apache.ant/ant-junit4 "1.9.3"]
    [org.apache.ant/ant-junit "1.9.3"]
    [org.apache.ant/ant-apache-log4j "1.9.3" :exclusions [ log4j] ]
    [ant-contrib/ant-contrib "1.0b3" :exclusions [ ant] ]
    [org.codehaus.gant/gant_groovy2.2 "1.9.10"]

    [com.jolbox/bonecp "0.8.0.RELEASE"]

    [org.apache.httpcomponents/httpcore-nio "4.3.2"]
    [org.apache.httpcomponents/httpcore "4.3.2"]
    [org.apache.httpcomponents/httpclient "4.3.3"]
    [io.netty/netty-all "4.0.19.Final"]

    [org.eclipse.jetty/jetty-xml "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-server "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-continuation "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-servlet "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-server "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-util "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-security "9.1.3.v20140225"]
    [org.eclipse.jetty/jetty-webapp "9.1.3.v20140225"]
    [org.eclipse.jetty.websocket/websocket-api "9.1.3.v20140225"]
    [org.eclipse.jetty.websocket/websocket-common "9.1.3.v20140225"]
    [org.eclipse.jetty.websocket/websocket-servlet "9.1.3.v20140225"]
    [org.eclipse.jetty.websocket/websocket-client "9.1.3.v20140225"]
    [org.eclipse.jetty.websocket/websocket-server "9.1.3.v20140225"]

    [org.codehaus.groovy/groovy-all "2.2.2"]
    [org.scala-lang/scala-library "2.11.0"]
    [org.scala-lang/scala-compiler "2.11.0"]
    ;;[com.sun.tools/tools "1.7.0"]
    [javassist/javassist "3.12.1.GA"]

    [com.github.spullara.mustache.java/compiler "0.8.14"]
    [org.fusesource.scalate/scalate-core_2.10 "1.6.1"]
    [org.freemarker/freemarker "2.3.20"]

    [com.yahoo.platform.yui/yuicompressor "2.4.7" :exclusions [rhino] ]

    [javax/geronimo-jms_1.1_spec "1.1.1"]
    [com.h2database/h2 "1.3.175"]
    ;;[org.postgresql/postgresql "9.3-1101.jdbc41"]
    [net.sf.ehcache/ehcache "2.8.1"]

    [org.clojure/math.numeric-tower "0.0.4"]
    [org.clojure/math.combinatorics "0.0.7"]
    [org.clojure/tools.logging "0.2.6"]
    [org.clojure/tools.nrepl "0.2.3"]
    [org.clojure/data.codec "0.1.0"]
    [org.clojure/java.jdbc "0.3.3"]
    [org.clojure/java.data "0.1.1"]
    [org.clojure/java.jmx "0.2.0"]
    [org.clojure/data.json "0.2.4"]
    [org.clojure/data.xml "0.0.7"]
    [org.clojure/clojure "1.6.0"]
    [org.clojure/clojurescript "0.0-2197"]

    [org.apache.shiro/shiro-core "1.2.3"]
    [rhino/js "1.7R2"]
    [jline/jline "0.9.9"]

    [org.scalatest/scalatest_2.10 "2.1.0"]
    [net.mikera/cljunit "0.3.0"]
    [junit/junit "4.11"]

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

  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :warn-on-reflection true

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

